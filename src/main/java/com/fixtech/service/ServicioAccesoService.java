package com.fixtech.service;

import com.fixtech.entity.Servicio;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.ServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicioAccesoService {

    private final ServicioRepository servicioRepository;

    public Servicio obtenerParaParticipante(Long usuarioId, Long servicioId) {
        Servicio servicio = servicioRepository.buscarDetallePorId(servicioId)
                .orElseThrow(() -> new NotFoundException("Servicio no encontrado"));
        validarParticipante(servicio, usuarioId);
        return servicio;
    }

    public Servicio bloquear(Long servicioId) {
        return servicioRepository.findByIdForUpdate(servicioId)
                .orElseThrow(() -> new NotFoundException("Servicio no encontrado"));
    }

    public boolean esCliente(Servicio servicio, Long usuarioId) {
        return servicio.getCliente().getUsuario().getId().equals(usuarioId);
    }

    public boolean esTecnico(Servicio servicio, Long usuarioId) {
        return servicio.getTecnico().getUsuario().getId().equals(usuarioId);
    }

    public void validarParticipante(Servicio servicio, Long usuarioId) {
        if (!esCliente(servicio, usuarioId) && !esTecnico(servicio, usuarioId)) {
            throw new ForbiddenException("No tiene acceso a este servicio");
        }
    }

    public void validarCliente(Servicio servicio, Long usuarioId) {
        if (!esCliente(servicio, usuarioId)) {
            throw new ForbiddenException(
                    "Solo el cliente del servicio puede realizar esta operación");
        }
    }

    public void validarTecnico(Servicio servicio, Long usuarioId) {
        if (!esTecnico(servicio, usuarioId)) {
            throw new ForbiddenException(
                    "Solo el técnico del servicio puede realizar esta operación");
        }
    }
}
