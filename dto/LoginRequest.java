package com.msme.cashflowguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Please enter your email address.")
        @Email(message = "Enter a valid email address.")
        String email,

        @NotBlank(message = "Please enter your password.")
        String password) {
}
