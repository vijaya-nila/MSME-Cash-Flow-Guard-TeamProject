package com.msme.cashflowguard.exception;

import org.springframework.http.HttpStatus;

public class CsvValidationException extends ApiException {

    public CsvValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
