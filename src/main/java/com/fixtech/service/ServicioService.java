package com.fixtech.service;

import com.fixtech.entity.Oferta;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.exception.BusinessException;
import com.fixtech.repository.ServicioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final ServicioEventoService servicioEventoService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public Servicio crearDesdeOferta(Oferta oferta, Solicitud solicitud) {
        if (servicioRepository.existsByOfertaId(oferta.getId())) {
            throw new BusinessException("La oferta ya tiene un servicio asociado");
        }

        BigDecimal movilidad = oferta.getCostoMovilidad() == null
                ? BigDecimal.ZERO
                : oferta.getCostoMovilidad();
        BigDecimal diagnostico = oferta.getCostoDiagnostico() == null
                ? BigDecimal.ZERO
                : oferta.getCostoDiagnostico();
        BigDecimal montoTotal = oferta.getPrecioServicio()
                .add(movilidad)
                .setScale(2, RoundingMode.HALF_UP);

        Servicio servicio = Servicio.builder()
                .solicitud(solicitud)
                .oferta(oferta)
                .cliente(solicitud.getCliente())
                .tecnico(oferta.getTecnico())
                .modalidad(oferta.getModalidad())
                .estado(EstadoServicio.PROGRAMADO)
                .precioServicio(oferta.getPrecioServicio().setScale(2, RoundingMode.HALF_UP))
                .costoMovilidad(movilidad.setScale(2, RoundingMode.HALF_UP))
                .costoDiagnostico(diagnostico.setScale(2, RoundingMode.HALF_UP))
                .montoTotal(montoTotal)
                .garantiaDias(oferta.getGarantiaDias())
                .pinInicio(oferta.getModalidad() == Modalidad.DOMICILIO
                        ? generarPin()
                        : null)
                .fechaProgramada(solicitud.getFechaPreferida() == null
                        ? null
                        : solicitud.getFechaPreferida().atStartOfDay())
                .build();

        Servicio guardado = servicioRepository.save(servicio);
        oferta.setServicio(guardado);
        servicioEventoService.registrarCambio(
                guardado,
                null,
                EstadoServicio.PROGRAMADO,
                null,
                "Servicio creado al aceptar la oferta");
        return guardado;
    }

    private String generarPin() {
        return "%04d".formatted(secureRandom.nextInt(10_000));
    }
}
