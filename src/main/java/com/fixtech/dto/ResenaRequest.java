package com.fixtech.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResenaRequest(
        @NotNull(message = "Debe indicar una calificación")
        @Min(value = 1, message = "La calificación debe estar entre 1 y 5")
        @Max(value = 5, message = "La calificación debe estar entre 1 y 5")
        Integer estrellas,
        @Min(value = 1, message = "La puntualidad debe estar entre 1 y 5")
        @Max(value = 5, message = "La puntualidad debe estar entre 1 y 5")
        Integer puntualidad,
        @Min(value = 1, message = "El trato debe estar entre 1 y 5")
        @Max(value = 5, message = "El trato debe estar entre 1 y 5")
        Integer trato,
        @Min(value = 1, message = "La calidad debe estar entre 1 y 5")
        @Max(value = 5, message = "La calidad debe estar entre 1 y 5")
        Integer calidad,
        @Min(value = 1, message = "El precio justo debe estar entre 1 y 5")
        @Max(value = 5, message = "El precio justo debe estar entre 1 y 5")
        Integer precioJusto,
        @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
        String comentario) {
}
