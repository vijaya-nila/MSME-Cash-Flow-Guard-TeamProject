package com.msme.cashflowguard.exception;

import org.springframework.http.HttpStatus;

/** Base class for errors that should become a clean JSON error response. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
