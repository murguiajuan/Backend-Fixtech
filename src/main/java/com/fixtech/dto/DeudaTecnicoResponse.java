package com.fixtech.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeudaTecnicoResponse(
        Long tecnicoId,
        String tecnicoNombre,
        String correo,
        BigDecimal saldoPendiente,
        BigDecimal limiteDeuda,
        boolean bloqueadoPorDeuda,
        int comisionesPendientes,
        int comisionesVencidas,
        LocalDate proximoVencimiento) {
}
