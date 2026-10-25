package com.fixtech.mapper;

import com.fixtech.dto.ComisionResponse;
import com.fixtech.dto.PagoComisionResponse;
import com.fixtech.entity.Comision;
import com.fixtech.entity.PagoComision;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class ComisionMapper {

    public ComisionResponse toResponse(Comision comision) {
        return new ComisionResponse(
                comision.getId(),
                comision.getServicio().getId(),
                comision.getMontoBase(),
                comision.getPorcentaje(),
                comision.getMontoComision(),
                comision.getMontoNetoTecnico(),
                comision.getEstado(),
                comision.getFechaLimitePago(),
                comision.getFechaPago(),
                comision.getCreadoEn());
    }

    public PagoComisionResponse toResponse(PagoComision pago) {
        String nombre = (pago.getTecnico().getNombres() + " "
                + pago.getTecnico().getApellidos()).trim();
        return new PagoComisionResponse(
                pago.getId(),
                pago.getTecnico().getId(),
                nombre,
                pago.getTecnico().getUsuario().getCorreo(),
                pago.getMonto(),
                pago.getMetodo(),
                pago.getNumeroOperacion(),
                pago.getEstado(),
                pago.getObservacion(),
                pago.getFechaValidacion(),
                pago.getCreadoEn(),
                pago.getRutaComprobante() != null && !pago.getRutaComprobante().isBlank(),
                pago.getDetalles().stream()
                        .map(detalle -> toResponse(detalle.getComision()))
                        .sorted(Comparator.comparing(ComisionResponse::id))
                        .toList());
    }
}
