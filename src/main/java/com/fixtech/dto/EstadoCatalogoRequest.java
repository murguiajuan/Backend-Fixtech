package com.fixtech.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoCatalogoRequest(
        @NotNull(message = "Debe indicar si el catálogo está activo")
        Boolean activo) {
}
