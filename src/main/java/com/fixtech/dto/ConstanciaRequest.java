package com.fixtech.dto;

import com.fixtech.entity.enums.TipoConstancia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConstanciaRequest(
        @NotNull TipoConstancia tipo,
        @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres")
        String observaciones,
        @Size(max = 300, message = "Los accesorios no pueden superar los 300 caracteres")
        String accesorios) {
}
