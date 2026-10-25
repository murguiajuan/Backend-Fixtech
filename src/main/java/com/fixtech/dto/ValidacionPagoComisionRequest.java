package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoPagoComision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ValidacionPagoComisionRequest(
        @NotNull(message = "Debe indicar el estado de la validación")
        EstadoPagoComision estado,
        @Size(max = 300, message = "La observación no puede superar los 300 caracteres")
        String observacion) {
}
