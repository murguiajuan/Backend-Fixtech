package com.fixtech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParametroActualizacionRequest(
        @NotBlank(message = "La clave del parámetro es obligatoria")
        @Size(max = 60, message = "La clave no puede superar los 60 caracteres")
        String clave,
        @NotBlank(message = "El valor del parámetro es obligatorio")
        @Size(max = 200, message = "El valor no puede superar los 200 caracteres")
        String valor) {
}
