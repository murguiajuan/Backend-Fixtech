package com.fixtech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensajeRequest(
        @NotBlank @Size(max = 1000, message = "El mensaje no puede superar los 1000 caracteres")
        String contenido) {
}
