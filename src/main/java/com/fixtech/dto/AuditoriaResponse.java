package com.fixtech.dto;

import java.time.LocalDateTime;

public record AuditoriaResponse(
        Long id,
        Long usuarioId,
        String accion,
        String entidad,
        Long entidadId,
        String detalle,
        String ip,
        LocalDateTime creadoEn) {
}
