package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoServicio;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambioEstadoServicioRequest(
        @NotNull EstadoServicio estado,
        @Pattern(regexp = "\\d{4}", message = "El PIN debe tener 4 dígitos")
        String pin,
        @Size(max = 300, message = "El comentario no puede superar los 300 caracteres")
        String comentario,
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo) {
}
