package com.fixtech.dto;

import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Urgencia;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SolicitudRequest(
        @NotNull @Positive Long tipoEquipoId,
        @NotNull @Positive Long tipoServicioId,
        @Size(max = 60) String marca,
        @Size(max = 80) String modelo,
        @NotBlank @Size(max = 2000) String descripcion,
        @NotNull Urgencia urgencia,
        @NotNull Modalidad modalidad,
        @NotNull @Positive Long distritoId,
        @Size(max = 200) String direccion,
        @Size(max = 200) String referencia,
        @FutureOrPresent(message = "La fecha preferida no puede ser pasada")
        LocalDate fechaPreferida,
        @Size(max = 40) String horarioPreferido,
        @DecimalMin(value = "0.01", message = "El presupuesto debe ser mayor a cero")
        @Digits(integer = 8, fraction = 2, message = "El presupuesto admite hasta 2 decimales")
        BigDecimal presupuestoReferencial) {
}
