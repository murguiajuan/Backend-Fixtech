package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoVerificacion;
import java.time.LocalDateTime;

public record TecnicoVerificacionResponse(
        Long id,
        EstadoVerificacion estadoVerificacion,
        String motivoVerificacion,
        LocalDateTime fechaVerificacion) {
}
