package com.fixtech.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DistritoRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar los 60 caracteres")
        String nombre,
        @Size(max = 60, message = "La provincia no puede superar los 60 caracteres")
        String provincia,
        @NotNull(message = "El tope de movilidad es obligatorio")
        @DecimalMin(value = "0.00", message = "El tope de movilidad no puede ser negativo")
        @Digits(integer = 6, fraction = 2,
                message = "El tope de movilidad debe tener como máximo 6 enteros y 2 decimales")
        BigDecimal topeMovilidad) {
}
