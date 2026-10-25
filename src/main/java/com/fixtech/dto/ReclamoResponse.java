package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoReclamo;
import java.time.LocalDateTime;
import java.util.List;

public record ReclamoResponse(
        Long id,
        Long servicioId,
        Long clienteId,
        String clienteNombre,
        String descripcion,
        EstadoReclamo estado,
        String respuestaTecnico,
        String resolucion,
        Long resueltoPorId,
        LocalDateTime creadoEn,
        LocalDateTime fechaResolucion,
        List<Long> fotoIds) {
}
