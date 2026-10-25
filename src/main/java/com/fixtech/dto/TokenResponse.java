package com.fixtech.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiraEnSegundos) {

    private static final String TIPO_BEARER = "Bearer";

    public static TokenResponse bearer(
            String accessToken, String refreshToken, long expiraEnSegundos) {
        return new TokenResponse(accessToken, refreshToken, TIPO_BEARER, expiraEnSegundos);
    }
}
