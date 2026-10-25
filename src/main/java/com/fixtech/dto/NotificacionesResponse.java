package com.fixtech.dto;

import java.util.List;

public record NotificacionesResponse(
        List<NotificacionResponse> notificaciones,
        long noLeidas) {
}
