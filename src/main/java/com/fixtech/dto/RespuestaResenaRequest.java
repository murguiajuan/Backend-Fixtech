package com.fixtech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespuestaResenaRequest(
        @NotBlank(message = "La respuesta es obligatoria")
        @Size(max = 1000, message = "La respuesta no puede superar los 1000 caracteres")
        String respuesta) {
}
