package com.fixtech.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerificacionRecuperacionRequest(
        @NotBlank @Email @Size(max = 120) String correo,
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
        String codigo) {
}
