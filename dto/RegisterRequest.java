package com.msme.cashflowguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Please enter your full name.")
        @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters.")
        String name,

        @Size(max = 150, message = "Business name must be at most 150 characters.")
        String businessName,

        @NotBlank(message = "Please enter your email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 190, message = "Email is too long.")
        String email,

        @NotBlank(message = "Please enter a password.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "Use 8 to 72 characters with letters and numbers.")
        String password) {
}
