package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoAjuste;
import jakarta.validation.constraints.NotNull;

public record RespuestaAjusteRequest(@NotNull EstadoAjuste estado) {
}
