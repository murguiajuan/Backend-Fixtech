package com.fixtech.util;

public final class UrlArchivos {

    private static final String PREFIJO_API = "/api";

    private UrlArchivos() {
    }

    public static String fotoTecnico(Long tecnicoId) {
        return tecnicoId == null
                ? null
                : "%s/tecnicos/%d/foto".formatted(PREFIJO_API, tecnicoId);
    }
}
