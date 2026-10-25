package com.fixtech.dto;

import com.fixtech.entity.enums.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FinalizarServicioRequest(
        @NotNull
        @DecimalMin(value = "0.00", message = "El monto final no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El monto admite hasta 2 decimales")
        BigDecimal montoFinal,
        @NotNull MetodoPago metodoPago) {
}
