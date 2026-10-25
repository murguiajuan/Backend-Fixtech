package com.fixtech.dto;

import com.fixtech.entity.enums.TipoNotificacion;
import java.time.LocalDateTime;

public record NotificacionResponse(
        Long id,
        TipoNotificacion tipo,
        String titulo,
        String mensaje,
        String referenciaTipo,
        Long referenciaId,
        boolean leida,
        LocalDateTime creadoEn) {
}
