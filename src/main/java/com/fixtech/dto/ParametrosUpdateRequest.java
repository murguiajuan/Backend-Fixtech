package com.fixtech.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ParametrosUpdateRequest(
        @NotEmpty(message = "Debe indicar al menos un parámetro")
        @Size(max = 13, message = "No se pueden actualizar más de 13 parámetros a la vez")
        List<@Valid ParametroActualizacionRequest> parametros) {
}
