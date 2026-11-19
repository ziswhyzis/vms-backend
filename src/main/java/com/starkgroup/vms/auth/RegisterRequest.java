package com.starkgroup.vms.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 60, message = "Username must be 3-60 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]{3,60}$",
                message = "Username may only contain letters, numbers, dots, dashes and underscores"
        )
        String username,

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 80, message = "Full name must be 2-80 characters")
        String fullName,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password
) {
}
