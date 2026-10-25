package com.fixtech.service;

import com.fixtech.dto.ServicioDetalleResponse;
import com.fixtech.dto.ServicioResumenResponse;
import com.fixtech.entity.AjustePrecio;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoAjuste;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.mapper.ServicioMapper;
import com.fixtech.repository.AjustePrecioRepository;
import com.fixtech.repository.AvanceServicioRepository;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.ConstanciaRepository;
import com.fixtech.repository.HistorialEstadoServicioRepository;
import com.fixtech.repository.ServicioRepository;
import com.fixtech.repository.TecnicoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicioConsultaService {

    private final ServicioRepository servicioRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final ServicioAccesoService servicioAccesoService;
    private final ServicioMapper servicioMapper;
    private final HistorialEstadoServicioRepository historialRepository;
    private final ConstanciaRepository constanciaRepository;
    private final AvanceServicioRepository avanceRepository;
    private final AjustePrecioRepository ajustePrecioRepository;

    public List<ServicioResumenResponse> listarMios(
            Long usuarioId, EstadoServicio estado) {
        Cliente cliente = clienteRepository.findByUsuarioId(usuarioId).orElse(null);
        if (cliente != null) {
            return servicioRepository.buscarPorCliente(cliente.getId()).stream()
                    .filter(servicio -> estado == null || servicio.getEstado() == estado)
                    .map(servicio -> servicioMapper.toResumen(servicio, true))
                    .toList();
        }

        Tecnico tecnico = tecnicoRepository.findByUsuarioId(usuarioId).orElse(null);
        if (tecnico != null) {
            return servicioRepository.buscarPorTecnico(tecnico.getId()).stream()
                    .filter(servicio -> estado == null || servicio.getEstado() == estado)
                    .map(servicio -> servicioMapper.toResumen(servicio, false))
                    .toList();
        }

        throw new ForbiddenException(
                "El usuario no tiene perfil de cliente ni de técnico");
    }

    public ServicioDetalleResponse obtenerDetalle(Long usuarioId, Long servicioId) {
        Servicio servicio = servicioAccesoService.obtenerParaParticipante(
                usuarioId, servicioId);
        boolean esCliente = servicioAccesoService.esCliente(servicio, usuarioId);

        AjustePrecio pendiente = ajustePrecioRepository
                .findTopByServicioIdAndEstadoOrderByCreadoEnDesc(
                        servicioId, EstadoAjuste.PENDIENTE)
                .orElse(null);

        return servicioMapper.toDetalle(
                servicio,
                esCliente,
                historialRepository.findByServicioIdOrderByCreadoEnAscIdAsc(servicioId),
                constanciaRepository.buscarPorServicio(servicioId),
                avanceRepository.findByServicioIdOrderByCreadoEnAscIdAsc(servicioId),
                ajustePrecioRepository.findByServicioIdOrderByCreadoEnDesc(servicioId),
                pendiente);
    }
}
