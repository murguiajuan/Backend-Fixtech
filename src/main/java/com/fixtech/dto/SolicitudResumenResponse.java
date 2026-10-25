package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Urgencia;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SolicitudResumenResponse(
        Long id,
        EstadoSolicitud estado,
        Urgencia urgencia,
        Modalidad modalidad,
        String tipoEquipo,
        String tipoServicio,
        String distrito,
        String marca,
        String modelo,
        String descripcion,
        BigDecimal presupuestoReferencial,
        LocalDateTime fechaVencimiento,
        LocalDateTime creadoEn,
        String imagenPrincipalUrl) {
}
