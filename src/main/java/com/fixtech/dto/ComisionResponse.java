package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoComision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ComisionResponse(
        Long id,
        Long servicioId,
        BigDecimal montoBase,
        BigDecimal porcentaje,
        BigDecimal montoComision,
        BigDecimal montoNetoTecnico,
        EstadoComision estado,
        LocalDate fechaLimitePago,
        LocalDateTime fechaPago,
        LocalDateTime creadoEn) {
}
