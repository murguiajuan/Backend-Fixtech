package com.fixtech.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record PerfilTecnicoRequest(
        @NotBlank @Size(max = 80) String nombres,
        @NotBlank @Size(max = 80) String apellidos,
        @NotBlank @Pattern(
                regexp = "\\d{8}",
                message = "El DNI debe tener 8 dígitos") String dni,
        @NotBlank @Pattern(
                regexp = "9\\d{8}",
                message = "El celular debe tener 9 dígitos y empezar con 9")
        String celular,
        @Size(max = 1000) String descripcion,
        @Min(0) @Max(60) int aniosExperiencia,
        boolean ofreceDomicilio,
        boolean ofreceTaller,
        @Size(max = 200) String direccionTaller,
        @Size(max = 200) String referenciaTaller,
        @Size(max = 120) String horarioTaller,
        @Min(value = 0, message = "La garantía no puede ser negativa")
        @Max(value = 3650, message = "La garantía no puede superar los 3650 días")
        int garantiaDiasDefecto,
        @NotEmpty Set<@NotNull @Positive Long> especialidadIds,
        @NotEmpty Set<@NotNull @Positive Long> distritoIds) {
}
