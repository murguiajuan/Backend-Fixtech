package com.fixtech.service;

import com.fixtech.entity.Auditoria;
import com.fixtech.repository.AuditoriaRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final int MAX_ACCION = 60;
    private static final int MAX_ENTIDAD = 60;
    private static final int MAX_DETALLE = 1000;
    private static final int MAX_IP = 45;

    private final AuditoriaRepository auditoriaRepository;

    @Transactional
    public void registrar(
            Long usuarioId, String accion, String entidad, Long entidadId, String detalle) {
        auditoriaRepository.save(Auditoria.builder()
                .usuarioId(usuarioId)
                .accion(recortar(accion, MAX_ACCION))
                .entidad(recortar(entidad, MAX_ENTIDAD))
                .entidadId(entidadId)
                .detalle(recortar(detalle, MAX_DETALLE))
                .ip(recortar(obtenerIp(), MAX_IP))
                .build());
    }

    private String obtenerIp() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        HttpServletRequest request = atributos.getRequest();
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.length() <= maximo
                ? normalizado
                : normalizado.substring(0, maximo);
    }
}
