package com.fixtech.dto;

import com.fixtech.entity.enums.TipoConstancia;
import java.time.LocalDateTime;
import java.util.List;

public record ConstanciaResponse(
        Long id,
        TipoConstancia tipo,
        String observaciones,
        String accesorios,
        boolean confirmadaPorCliente,
        LocalDateTime fechaConfirmacion,
        LocalDateTime creadoEn,
        List<Long> fotoIds) {
}
