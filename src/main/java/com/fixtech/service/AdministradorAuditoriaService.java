package com.fixtech.service;

import com.fixtech.dto.AuditoriaResponse;
import com.fixtech.dto.PaginaAuditoriaResponse;
import com.fixtech.entity.Auditoria;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.AuditoriaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministradorAuditoriaService {

    private static final int MAX_TAMANO = 100;
    private static final int MAX_FILTRO = 60;

    private final AuditoriaRepository auditoriaRepository;
    private final AdministradorRepository administradorRepository;

    public PaginaAuditoriaResponse listar(
            Long adminUsuarioId,
            Long usuarioId,
            String entidad,
            String accion,
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamano) {
        validarAdministrador(adminUsuarioId);
        validarPaginacion(pagina, tamano);
        validarFechas(desde, hasta);

        String entidadFiltro = normalizar(entidad, "La entidad");
        String accionFiltro = normalizar(accion, "La acción");
        LocalDateTime inicio = desde == null ? null : desde.atStartOfDay();
        LocalDateTime fin = hasta == null ? null : hasta.plusDays(1).atStartOfDay();

        Page<Auditoria> resultado = auditoriaRepository.buscar(
                usuarioId,
                entidadFiltro,
                accionFiltro == null ? null : accionFiltro.toUpperCase(),
                inicio,
                fin,
                PageRequest.of(pagina, tamano));

        return new PaginaAuditoriaResponse(
                resultado.getContent().stream().map(this::aResponse).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    private void validarAdministrador(Long adminUsuarioId) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
    }

    private void validarPaginacion(int pagina, int tamano) {
        if (pagina < 0) {
            throw new BadRequestException("La página no puede ser negativa");
        }
        if (tamano < 1 || tamano > MAX_TAMANO) {
            throw new BadRequestException(
                    "El tamaño de página debe estar entre 1 y %d".formatted(MAX_TAMANO));
        }
    }

    private void validarFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new BadRequestException(
                    "La fecha de fin no puede ser anterior a la fecha de inicio");
        }
    }

    private String normalizar(String valor, String nombre) {
        if (!StringUtils.hasText(valor)) {
            return null;
        }
        String normalizado = valor.trim();
        if (normalizado.length() > MAX_FILTRO) {
            throw new BadRequestException(
                    "%s no puede superar los %d caracteres"
                            .formatted(nombre, MAX_FILTRO));
        }
        return normalizado;
    }

    private AuditoriaResponse aResponse(Auditoria auditoria) {
        return new AuditoriaResponse(
                auditoria.getId(),
                auditoria.getUsuarioId(),
                auditoria.getAccion(),
                auditoria.getEntidad(),
                auditoria.getEntidadId(),
                auditoria.getDetalle(),
                auditoria.getIp(),
                auditoria.getCreadoEn());
    }
}
