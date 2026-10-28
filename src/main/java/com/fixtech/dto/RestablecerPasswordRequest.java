package com.fixtech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerPasswordRequest(
        @NotBlank @Size(min = 20, max = 200) String ticket,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
