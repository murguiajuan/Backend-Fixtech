package com.fixtech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccesoRecuperacionRequest(
        @NotBlank @Size(min = 20, max = 200) String ticket) {
}
