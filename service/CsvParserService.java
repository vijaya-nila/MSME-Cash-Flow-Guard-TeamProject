package com.msme.cashflowguard.service;

import com.msme.cashflowguard.exception.BadRequestException;
import com.msme.cashflowguard.model.ParsedCsv;
import com.msme.cashflowguard.model.RawRow;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stage 3: turns an uploaded file into raw text rows using Apache Commons CSV.
 * It only checks "is this a readable CSV?". What the values mean is checked in
 * {@link ValidationService}.
 */
@Service
public class CsvParserService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_ROWS = 50_000;

    public ParsedCsv parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("This file is empty. Please choose a statement that contains data.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("Only .csv files are accepted. Please export your statement as CSV.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("The file is larger than 10 MB. Please upload a smaller statement.");
        }
        try (InputStream in = file.getInputStream()) {
            return parse(in, name);
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read.");
        }
    }

    /** Parses CSV text. The first non-empty line must be the header. */
    public ParsedCsv parse(InputStream in, String filename) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();
        try (CSVParser parser = format.parse(reader)) {
            Iterator<CSVRecord> it = parser.iterator();
            if (!it.hasNext()) {
                throw new BadRequestException("This file is empty. Please choose a statement that contains data.");
            }
            CSVRecord headerRecord = it.next();
            List<String> headers = new ArrayList<>();
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < headerRecord.size(); i++) {
                String h = headerRecord.get(i);
                if (i == 0 && h != null && h.startsWith("\uFEFF")) {
                    h = h.substring(1);          // Excel adds a hidden byte-order mark
                }
                h = h == null ? "" : h.trim().toLowerCase(Locale.ROOT);
                headers.add(h);
                columnIndex.putIfAbsent(h, i);
            }

            List<RawRow> rows = new ArrayList<>();
            int rowNumber = 0;
            while (it.hasNext()) {
                CSVRecord record = it.next();
                List<String> values = new ArrayList<>(record.size());
                boolean allBlank = true;
                for (int i = 0; i < record.size(); i++) {
                    String v = record.get(i);
                    values.add(v == null ? "" : v);
                    if (v != null && !v.isBlank()) {
                        allBlank = false;
                    }
                }
                if (allBlank) {
                    continue;
                }
                rowNumber++;
                if (rowNumber > MAX_ROWS) {
                    throw new BadRequestException("This statement has more than " + MAX_ROWS
                            + " rows. Please upload a shorter period.");
                }
                rows.add(new RawRow(rowNumber, values));
            }
            return new ParsedCsv(filename, headers, columnIndex, rows);
        } catch (IllegalStateException | IllegalArgumentException | java.io.UncheckedIOException e) {
            throw new BadRequestException("The file could not be read as a valid CSV. Please re-export it from your bank.");
        }
    }
}
