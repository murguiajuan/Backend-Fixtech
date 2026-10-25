package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoVerificacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerificacionTecnicoRequest(
        @NotNull EstadoVerificacion estado,
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo) {
}
