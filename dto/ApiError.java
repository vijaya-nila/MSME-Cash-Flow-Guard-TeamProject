package com.msme.cashflowguard.dto;

/** Standard error body. The frontend reads the "message" field. */
public record ApiError(String timestamp, int status, String error, String message) {
}
