package com.fixtech.dto;

import com.fixtech.entity.enums.EmisorContraoferta;
import com.fixtech.entity.enums.EstadoContraoferta;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ContraofertaResponse(
        Long id,
        EmisorContraoferta emisor,
        int ronda,
        BigDecimal montoPropuesto,
        String mensaje,
        EstadoContraoferta estado,
        LocalDateTime creadoEn) {
}
