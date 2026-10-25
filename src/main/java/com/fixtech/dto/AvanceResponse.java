package com.fixtech.dto;

import com.fixtech.entity.enums.TipoAvance;
import java.time.LocalDateTime;

public record AvanceResponse(
        Long id,
        TipoAvance tipo,
        String descripcion,
        boolean tieneFoto,
        LocalDateTime creadoEn) {
}
