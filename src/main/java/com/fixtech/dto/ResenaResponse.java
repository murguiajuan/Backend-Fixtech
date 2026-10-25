package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoResena;
import java.time.LocalDateTime;

public record ResenaResponse(
        Long id,
        Long servicioId,
        Long tecnicoId,
        String tecnicoNombre,
        String clienteNombre,
        int estrellas,
        Integer puntualidad,
        Integer trato,
        Integer calidad,
        Integer precioJusto,
        String comentario,
        String respuestaTecnico,
        LocalDateTime fechaRespuesta,
        EstadoResena estado,
        String motivoReporte,
        LocalDateTime creadoEn) {
}
