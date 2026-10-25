package com.fixtech.dto;

import com.fixtech.entity.enums.Modalidad;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record OfertaRequest(
        @NotNull Modalidad modalidad,
        @NotNull
        @DecimalMin(value = "0.01", message = "El precio del servicio debe ser mayor a cero")
        @Digits(integer = 8, fraction = 2, message = "El precio admite hasta 2 decimales")
        BigDecimal precioServicio,
        @DecimalMin(value = "0.00", message = "La movilidad no puede ser negativa")
        @Digits(integer = 8, fraction = 2, message = "La movilidad admite hasta 2 decimales")
        BigDecimal costoMovilidad,
        @DecimalMin(value = "0.00", message = "El diagnóstico no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El diagnóstico admite hasta 2 decimales")
        BigDecimal costoDiagnostico,
        @NotNull @Positive(message = "El tiempo estimado debe ser mayor a cero")
        Integer tiempoEstimadoHoras,
        @Min(value = 0, message = "La garantía no puede ser negativa")
        Integer garantiaDias,
        @Size(max = 500, message = "El mensaje no puede superar los 500 caracteres")
        String mensaje) {
}
