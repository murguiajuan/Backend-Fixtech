package com.fixtech.mapper;

import com.fixtech.dto.ContraofertaResponse;
import com.fixtech.dto.OfertaResponse;
import com.fixtech.dto.ServicioResponse;
import com.fixtech.dto.TecnicoResumenResponse;
import com.fixtech.entity.Contraoferta;
import com.fixtech.entity.Oferta;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Tecnico;
import com.fixtech.util.UrlArchivos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OfertaMapper {

    public OfertaResponse toResponse(Oferta oferta) {
        List<ContraofertaResponse> contraofertas = contraofertasDe(oferta).stream()
                .sorted(Comparator.comparingInt(Contraoferta::getRonda)
                        .thenComparing(Contraoferta::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toContraoferta)
                .toList();

        Long servicioId = oferta.getServicio() == null
                ? null
                : oferta.getServicio().getId();

        return new OfertaResponse(
                oferta.getId(),
                oferta.getSolicitud().getId(),
                oferta.getEstado(),
                oferta.getModalidad(),
                escalar(oferta.getPrecioServicio()),
                escalar(oferta.getCostoMovilidad()),
                escalar(oferta.getCostoDiagnostico()),
                precioTotal(oferta),
                oferta.getTiempoEstimadoHoras(),
                oferta.getGarantiaDias(),
                oferta.getMensaje(),
                oferta.getCreadoEn(),
                oferta.getActualizadoEn(),
                toTecnico(oferta.getTecnico()),
                contraofertas,
                servicioId);
    }

    public BigDecimal precioTotal(Oferta oferta) {
        BigDecimal servicio = oferta.getPrecioServicio() == null
                ? BigDecimal.ZERO
                : oferta.getPrecioServicio();
        BigDecimal movilidad = oferta.getCostoMovilidad() == null
                ? BigDecimal.ZERO
                : oferta.getCostoMovilidad();
        return servicio.add(movilidad).setScale(2, RoundingMode.HALF_UP);
    }

    public ServicioResponse toServicioResponse(Servicio servicio) {
        return new ServicioResponse(
                servicio.getId(),
                servicio.getSolicitud().getId(),
                servicio.getOferta().getId(),
                servicio.getCliente().getId(),
                servicio.getTecnico().getId(),
                servicio.getModalidad(),
                servicio.getEstado(),
                escalar(servicio.getPrecioServicio()),
                escalar(servicio.getCostoMovilidad()),
                escalar(servicio.getCostoDiagnostico()),
                escalar(servicio.getMontoTotal()),
                escalar(servicio.getMontoFinal()),
                servicio.getGarantiaDias(),
                servicio.getCreadoEn());
    }

    private ContraofertaResponse toContraoferta(Contraoferta contraoferta) {
        return new ContraofertaResponse(
                contraoferta.getId(),
                contraoferta.getEmisor(),
                contraoferta.getRonda(),
                escalar(contraoferta.getMontoPropuesto()),
                contraoferta.getMensaje(),
                contraoferta.getEstado(),
                contraoferta.getCreadoEn());
    }

    private TecnicoResumenResponse toTecnico(Tecnico tecnico) {
        return new TecnicoResumenResponse(
                tecnico.getId(),
                tecnico.getNombres(),
                tecnico.getApellidos(),
                tecnico.getFotoUrl() == null ? null : UrlArchivos.fotoTecnico(tecnico.getId()),
                tecnico.getDescripcion(),
                Byte.toUnsignedInt(tecnico.getAniosExperiencia()),
                escalar(tecnico.getCalificacionPromedio()),
                tecnico.getTotalResenas(),
                tecnico.getServiciosCompletados(),
                tecnico.isDestacado());
    }

    private List<Contraoferta> contraofertasDe(Oferta oferta) {
        return oferta.getContraofertas() == null ? List.of() : oferta.getContraofertas();
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
