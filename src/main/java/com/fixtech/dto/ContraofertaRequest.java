package com.fixtech.dto;

import com.fixtech.entity.enums.AccionContraoferta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ContraofertaRequest(
        @NotNull AccionContraoferta accion,
        @DecimalMin(value = "0.01", message = "El monto propuesto debe ser mayor a cero")
        @Digits(integer = 8, fraction = 2, message = "El monto admite hasta 2 decimales")
        BigDecimal montoPropuesto,
        @Size(max = 300, message = "El mensaje no puede superar los 300 caracteres")
        String mensaje) {
}
