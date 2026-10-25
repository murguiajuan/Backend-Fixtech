package com.fixtech.dto;

import java.time.LocalDateTime;

public record HistorialEstadoResponse(
        Long id,
        String estadoAnterior,
        String estadoNuevo,
        Long cambiadoPorId,
        String cambiadoPorNombre,
        String comentario,
        LocalDateTime creadoEn) {
}
