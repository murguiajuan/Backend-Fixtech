package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Urgencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SolicitudDisponibleResponse(
        Long id,
        EstadoSolicitud estado,
        Urgencia urgencia,
        Modalidad modalidad,
        String tipoEquipo,
        String tipoServicio,
        Long distritoId,
        String distrito,
        String marca,
        String modelo,
        String descripcion,
        LocalDate fechaPreferida,
        String horarioPreferido,
        BigDecimal presupuestoReferencial,
        LocalDateTime fechaVencimiento,
        LocalDateTime creadoEn,
        List<ImagenSolicitudResponse> imagenes) {
}
