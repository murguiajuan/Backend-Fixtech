package com.fixtech.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AjustePrecioRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "El monto propuesto debe ser mayor a cero")
        @Digits(integer = 8, fraction = 2, message = "El monto admite hasta 2 decimales")
        BigDecimal montoNuevo,
        @NotBlank @Size(max = 500, message = "La justificación no puede superar los 500 caracteres")
        String justificacion) {
}
