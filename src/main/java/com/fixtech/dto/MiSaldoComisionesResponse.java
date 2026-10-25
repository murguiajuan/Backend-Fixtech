package com.fixtech.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MiSaldoComisionesResponse(
        BigDecimal saldoPendiente,
        BigDecimal totalPagado,
        BigDecimal limiteDeuda,
        boolean bloqueadoPorDeuda,
        int comisionesVencidas,
        LocalDate proximoVencimiento,
        List<ComisionResponse> comisionesPendientes,
        List<PagoComisionResponse> pagos) {
}
