package com.fixtech.dto;

import java.util.List;

public record PaginaAuditoriaResponse(
        List<AuditoriaResponse> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {
}
