package com.fixtech.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitudRecuperacionRequest(
        @NotBlank @Email @Size(max = 120) String correo) {
}
