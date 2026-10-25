package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoAjuste;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AjustePrecioResponse(
        Long id,
        Long servicioId,
        BigDecimal montoAnterior,
        BigDecimal montoNuevo,
        String justificacion,
        boolean tieneEvidencia,
        EstadoAjuste estado,
        LocalDateTime creadoEn,
        LocalDateTime fechaRespuesta) {
}
