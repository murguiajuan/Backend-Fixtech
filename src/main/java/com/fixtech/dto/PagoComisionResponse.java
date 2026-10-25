package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoPagoComision;
import com.fixtech.entity.enums.MetodoPago;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PagoComisionResponse(
        Long id,
        Long tecnicoId,
        String tecnicoNombre,
        String tecnicoCorreo,
        BigDecimal monto,
        MetodoPago metodo,
        String numeroOperacion,
        EstadoPagoComision estado,
        String observacion,
        LocalDateTime fechaValidacion,
        LocalDateTime creadoEn,
        boolean tieneComprobante,
        List<ComisionResponse> comisiones) {
}
