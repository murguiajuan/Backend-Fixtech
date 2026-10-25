package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoReclamo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoReclamoRequest(
        @NotNull(message = "Debe indicar el nuevo estado")
        EstadoReclamo estado,
        @Size(max = 1000, message = "La resolución no puede superar los 1000 caracteres")
        String resolucion) {
}
