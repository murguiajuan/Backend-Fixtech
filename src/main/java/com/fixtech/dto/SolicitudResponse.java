package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Urgencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SolicitudResponse(
        Long id,
        EstadoSolicitud estado,
        Urgencia urgencia,
        Modalidad modalidad,
        Long tipoEquipoId,
        String tipoEquipo,
        Long tipoServicioId,
        String tipoServicio,
        Long distritoId,
        String distrito,
        String marca,
        String modelo,
        String descripcion,
        String direccion,
        String referencia,
        LocalDate fechaPreferida,
        String horarioPreferido,
        BigDecimal presupuestoReferencial,
        LocalDateTime fechaVencimiento,
        String motivoCancelacion,
        LocalDateTime creadoEn,
        List<ImagenSolicitudResponse> imagenes) {
}
