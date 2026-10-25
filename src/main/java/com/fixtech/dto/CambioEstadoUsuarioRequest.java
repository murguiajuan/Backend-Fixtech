package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoUsuario;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoUsuarioRequest(
        @NotNull(message = "Debe indicar el nuevo estado del usuario")
        EstadoUsuario estado,
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo) {
}
