package com.fixtech.dto;

public record RecuperacionVerificadaResponse(
        String ticket,
        long expiraEnSegundos) {
}
