package com.fixtech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReporteResenaRequest(
        @NotBlank(message = "El motivo del reporte es obligatorio")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo) {
}
