package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoResena;
import jakarta.validation.constraints.NotNull;

public record ModeracionResenaRequest(
        @NotNull(message = "Debe indicar el estado de la reseña")
        EstadoResena estado) {
}
