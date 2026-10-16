package com.msme.cashflowguard.controller;

import com.msme.cashflowguard.dto.MessageResponse;
import com.msme.cashflowguard.dto.UploadResponse;
import com.msme.cashflowguard.service.StatementService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    /** Multipart field name is "file" (matches the frontend's UPLOAD_FIELD). */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestAttribute("userId") Long userId,
                                 @RequestParam("file") MultipartFile file) {
        return statementService.upload(userId, file);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@RequestAttribute("userId") Long userId, @PathVariable("id") Long id) {
        return statementService.delete(userId, id);
    }
}
