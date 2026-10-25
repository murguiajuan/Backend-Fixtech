package com.fixtech.dto;

import com.fixtech.entity.enums.TipoAvance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvanceRequest(
        @NotNull TipoAvance tipo,
        @NotBlank @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
        String descripcion) {
}
