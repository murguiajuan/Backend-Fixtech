package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TecnicoAdminResponse(
        Long id,
        Long usuarioId,
        String correo,
        String nombres,
        String apellidos,
        String dni,
        String celular,
        String fotoUrl,
        EstadoVerificacion estadoVerificacion,
        String motivoVerificacion,
        boolean bloqueadoPorDeuda,
        BigDecimal saldoComisionPendiente,
        List<Modalidad> modalidades,
        BigDecimal calificacionPromedio,
        int totalResenas,
        int serviciosCompletados,
        boolean destacado,
        LocalDateTime fechaVerificacion,
        LocalDateTime creadoEn) {
}
