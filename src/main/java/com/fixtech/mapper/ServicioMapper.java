package com.fixtech.mapper;

import com.fixtech.dto.AjustePrecioResponse;
import com.fixtech.dto.AvanceResponse;
import com.fixtech.dto.ConstanciaResponse;
import com.fixtech.dto.ContactoClienteResponse;
import com.fixtech.dto.ContactoTecnicoResponse;
import com.fixtech.dto.HistorialEstadoResponse;
import com.fixtech.dto.MensajeResponse;
import com.fixtech.dto.ServicioDetalleResponse;
import com.fixtech.dto.ServicioResumenResponse;
import com.fixtech.dto.ServicioResponse;
import com.fixtech.dto.SolicitudServicioResponse;
import com.fixtech.entity.AjustePrecio;
import com.fixtech.entity.AvanceServicio;
import com.fixtech.entity.Constancia;
import com.fixtech.entity.ConstanciaFoto;
import com.fixtech.entity.HistorialEstadoServicio;
import com.fixtech.entity.Mensaje;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.Modalidad;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ServicioMapper {

    public ServicioResponse toResponse(Servicio servicio) {
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

    public ServicioResumenResponse toResumen(Servicio servicio, boolean esCliente) {
        return new ServicioResumenResponse(
                servicio.getId(),
                servicio.getSolicitud().getId(),
                servicio.getModalidad(),
                servicio.getEstado(),
                escalar(servicio.getMontoTotal()),
                escalar(servicio.getMontoFinal()),
                esCliente ? servicio.getTecnico().getId() : servicio.getCliente().getId(),
                esCliente
                        ? nombre(
                                servicio.getTecnico().getNombres(),
                                servicio.getTecnico().getApellidos())
                        : nombre(
                                servicio.getCliente().getNombres(),
                                servicio.getCliente().getApellidos()),
                servicio.getSolicitud().getTipoEquipo() == null
                        ? null
                        : servicio.getSolicitud().getTipoEquipo().getNombre(),
                servicio.getSolicitud().getTipoServicio() == null
                        ? null
                        : servicio.getSolicitud().getTipoServicio().getNombre(),
                servicio.getFechaProgramada(),
                servicio.getFechaFinalizacion(),
                servicio.getCreadoEn());
    }

    public ServicioDetalleResponse toDetalle(
            Servicio servicio,
            boolean esCliente,
            List<HistorialEstadoServicio> historial,
            List<Constancia> constancias,
            List<AvanceServicio> avances,
            List<AjustePrecio> ajustes,
            AjustePrecio pendiente) {
        boolean domicilio = servicio.getModalidad() == Modalidad.DOMICILIO;
        boolean taller = servicio.getModalidad() == Modalidad.TALLER;

        return new ServicioDetalleResponse(
                servicio.getId(),
                servicio.getSolicitud().getId(),
                servicio.getOferta().getId(),
                servicio.getModalidad(),
                servicio.getEstado(),
                escalar(servicio.getPrecioServicio()),
                escalar(servicio.getCostoMovilidad()),
                escalar(servicio.getCostoDiagnostico()),
                escalar(servicio.getMontoTotal()),
                escalar(servicio.getMontoFinal()),
                servicio.getMetodoPago(),
                esCliente && domicilio ? servicio.getPinInicio() : null,
                servicio.isPinValidado(),
                servicio.getGarantiaDias(),
                servicio.getFechaProgramada(),
                servicio.getFechaInicio(),
                servicio.getFechaFinalizacion(),
                servicio.getFechaLimiteConformidad(),
                servicio.getFechaConfirmacion(),
                servicio.isConfirmadoAutomaticamente(),
                servicio.getCanceladoPor(),
                servicio.getMotivoCancelacion(),
                servicio.getCreadoEn(),
                servicio.getActualizadoEn(),
                new ContactoClienteResponse(
                        servicio.getCliente().getId(),
                        servicio.getCliente().getNombres(),
                        servicio.getCliente().getApellidos(),
                        servicio.getCliente().getCelular(),
                        servicio.getCliente().getUsuario().getCorreo()),
                new ContactoTecnicoResponse(
                        servicio.getTecnico().getId(),
                        servicio.getTecnico().getNombres(),
                        servicio.getTecnico().getApellidos(),
                        servicio.getTecnico().getCelular(),
                        servicio.getTecnico().getUsuario().getCorreo(),
                        servicio.getTecnico().getFotoUrl(),
                        escalar(servicio.getTecnico().getCalificacionPromedio()),
                        servicio.getTecnico().getTotalResenas(),
                        taller ? servicio.getTecnico().getDireccionTaller() : null,
                        taller ? servicio.getTecnico().getReferenciaTaller() : null,
                        taller ? servicio.getTecnico().getHorarioTaller() : null),
                new SolicitudServicioResponse(
                        servicio.getSolicitud().getId(),
                        servicio.getSolicitud().getDescripcion(),
                        servicio.getSolicitud().getTipoEquipo() == null
                                ? null
                                : servicio.getSolicitud().getTipoEquipo().getNombre(),
                        servicio.getSolicitud().getTipoServicio() == null
                                ? null
                                : servicio.getSolicitud().getTipoServicio().getNombre(),
                        servicio.getSolicitud().getMarca(),
                        servicio.getSolicitud().getModelo(),
                        servicio.getSolicitud().getUrgencia(),
                        servicio.getSolicitud().getDistrito() == null
                                ? null
                                : servicio.getSolicitud().getDistrito().getNombre(),
                        domicilio ? servicio.getSolicitud().getDireccion() : null,
                        domicilio ? servicio.getSolicitud().getReferencia() : null,
                        servicio.getSolicitud().getFechaPreferida(),
                        servicio.getSolicitud().getHorarioPreferido()),
                historial.stream()
                        .map(registro -> toHistorial(registro, servicio))
                        .toList(),
                constancias.stream().map(this::toConstancia).toList(),
                avances.stream().map(this::toAvance).toList(),
                ajustes.stream().map(this::toAjuste).toList(),
                pendiente == null ? null : toAjuste(pendiente));
    }

    public ConstanciaResponse toConstancia(Constancia constancia) {
        return new ConstanciaResponse(
                constancia.getId(),
                constancia.getTipo(),
                constancia.getObservaciones(),
                constancia.getAccesorios(),
                constancia.isConfirmadaPorCliente(),
                constancia.getFechaConfirmacion(),
                constancia.getCreadoEn(),
                constancia.getFotos() == null
                        ? List.of()
                        : constancia.getFotos().stream()
                                .map(ConstanciaFoto::getId)
                                .toList());
    }

    public AvanceResponse toAvance(AvanceServicio avance) {
        return new AvanceResponse(
                avance.getId(),
                avance.getTipo(),
                avance.getDescripcion(),
                avance.getRutaFoto() != null,
                avance.getCreadoEn());
    }

    public AjustePrecioResponse toAjuste(AjustePrecio ajuste) {
        return new AjustePrecioResponse(
                ajuste.getId(),
                ajuste.getServicio().getId(),
                escalar(ajuste.getMontoAnterior()),
                escalar(ajuste.getMontoNuevo()),
                ajuste.getJustificacion(),
                ajuste.getRutaEvidencia() != null,
                ajuste.getEstado(),
                ajuste.getCreadoEn(),
                ajuste.getFechaRespuesta());
    }

    public MensajeResponse toMensaje(Mensaje mensaje, Long usuarioId) {
        boolean mio = mensaje.getEmisor().getId().equals(usuarioId);
        return new MensajeResponse(
                mensaje.getId(),
                mensaje.getEmisor().getId(),
                nombreEmisor(mensaje),
                mensaje.getContenido(),
                mensaje.isLeido(),
                mio,
                mensaje.getCreadoEn());
    }

    private HistorialEstadoResponse toHistorial(
            HistorialEstadoServicio registro, Servicio servicio) {
        Long cambiadoPorId = registro.getCambiadoPor() == null
                ? null
                : registro.getCambiadoPor().getId();
        String nombre = null;
        if (cambiadoPorId != null) {
            if (cambiadoPorId.equals(servicio.getCliente().getUsuario().getId())) {
                nombre = nombre(
                        servicio.getCliente().getNombres(),
                        servicio.getCliente().getApellidos());
            } else if (cambiadoPorId.equals(servicio.getTecnico().getUsuario().getId())) {
                nombre = nombre(
                        servicio.getTecnico().getNombres(),
                        servicio.getTecnico().getApellidos());
            } else {
                nombre = "Administrador";
            }
        }
        return new HistorialEstadoResponse(
                registro.getId(),
                registro.getEstadoAnterior() == null
                        ? null
                        : registro.getEstadoAnterior().name(),
                registro.getEstadoNuevo().name(),
                cambiadoPorId,
                nombre,
                registro.getComentario(),
                registro.getCreadoEn());
    }

    private String nombreEmisor(Mensaje mensaje) {
        if (mensaje.getServicio().getCliente().getUsuario().getId()
                .equals(mensaje.getEmisor().getId())) {
            return nombre(
                    mensaje.getServicio().getCliente().getNombres(),
                    mensaje.getServicio().getCliente().getApellidos());
        }
        return nombre(
                mensaje.getServicio().getTecnico().getNombres(),
                mensaje.getServicio().getTecnico().getApellidos());
    }

    private String nombre(String nombres, String apellidos) {
        String nombre = nombres == null ? "" : nombres;
        String apellido = apellidos == null ? "" : apellidos;
        return (nombre + " " + apellido).trim();
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
