package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServicioResumenResponse(
        Long id,
        Long solicitudId,
        Modalidad modalidad,
        EstadoServicio estado,
        BigDecimal montoTotal,
        BigDecimal montoFinal,
        Long contraparteId,
        String contraparteNombre,
        String tipoEquipo,
        String tipoServicio,
        LocalDateTime fechaProgramada,
        LocalDateTime fechaFinalizacion,
        LocalDateTime creadoEn) {
}
