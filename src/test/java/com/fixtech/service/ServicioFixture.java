package com.fixtech.service;

import com.fixtech.entity.Cliente;
import com.fixtech.entity.Comision;
import com.fixtech.entity.Oferta;
import com.fixtech.entity.Servicio;
import com.fixtech.entity.Solicitud;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoComision;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoServicio;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.entity.enums.Rol;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

final class ServicioFixture {

    private ServicioFixture() {
    }

    static Usuario usuario(Long id, Rol rol) {
        return Usuario.builder()
                .id(id)
                .correo("usuario%d@fixtech.pe".formatted(id))
                .rol(rol)
                .estado(EstadoUsuario.ACTIVO)
                .build();
    }

    static Cliente cliente(Long id, Long usuarioId) {
        return Cliente.builder()
                .id(id)
                .usuario(usuario(usuarioId, Rol.CLIENTE))
                .nombres("Cliente")
                .apellidos("Prueba")
                .celular("999999999")
                .build();
    }

    static Tecnico tecnico(Long id, Long usuarioId) {
        return Tecnico.builder()
                .id(id)
                .usuario(usuario(usuarioId, Rol.TECNICO))
                .nombres("Tecnico")
                .apellidos("Prueba")
                .dni("12345678")
                .celular("988888888")
                .estadoVerificacion(EstadoVerificacion.APROBADO)
                .calificacionPromedio(new BigDecimal("4.50"))
                .totalResenas(3)
                .build();
    }

    static Solicitud solicitud(Long id, Cliente cliente) {
        return Solicitud.builder()
                .id(id)
                .cliente(cliente)
                .descripcion("Equipo con falla")
                .modalidad(Modalidad.DOMICILIO)
                .estado(EstadoSolicitud.ACEPTADA)
                .fechaVencimiento(LocalDateTime.now().plusDays(1))
                .fechaPreferida(LocalDate.now().plusDays(1))
                .horarioPreferido("Tarde")
                .direccion("Av. Siempre Viva 123")
                .referencia("Frente al parque")
                .build();
    }

    static Oferta oferta(Long id, Solicitud solicitud, Tecnico tecnico) {
        return Oferta.builder()
                .id(id)
                .solicitud(solicitud)
                .tecnico(tecnico)
                .modalidad(solicitud.getModalidad())
                .precioServicio(new BigDecimal("100.00"))
                .costoMovilidad(new BigDecimal("10.00"))
                .costoDiagnostico(new BigDecimal("5.00"))
                .tiempoEstimadoHoras(2)
                .garantiaDias(7)
                .estado(EstadoOferta.ACEPTADA)
                .build();
    }

    static Servicio servicio(Long id, EstadoServicio estado, Modalidad modalidad) {
        Cliente cliente = cliente(1L, 10L);
        Tecnico tecnico = tecnico(2L, 20L);
        Solicitud solicitud = solicitud(50L, cliente);
        solicitud.setModalidad(modalidad);
        Oferta oferta = oferta(100L, solicitud, tecnico);
        oferta.setModalidad(modalidad);

        Servicio servicio = Servicio.builder()
                .id(id)
                .solicitud(solicitud)
                .oferta(oferta)
                .cliente(cliente)
                .tecnico(tecnico)
                .modalidad(modalidad)
                .estado(estado)
                .precioServicio(new BigDecimal("100.00"))
                .costoMovilidad(new BigDecimal("10.00"))
                .costoDiagnostico(new BigDecimal("5.00"))
                .montoTotal(new BigDecimal("110.00"))
                .garantiaDias(7)
                .pinInicio(modalidad == Modalidad.DOMICILIO ? "1234" : null)
                .fechaProgramada(LocalDateTime.now().plusDays(1))
                .creadoEn(LocalDateTime.now())
                .actualizadoEn(LocalDateTime.now())
                .build();
        oferta.setServicio(servicio);
        return servicio;
    }

    static Comision comision(
            Long id, Servicio servicio, String montoComision, LocalDate fechaLimite) {
        BigDecimal comision = new BigDecimal(montoComision);
        return Comision.builder()
                .id(id)
                .servicio(servicio)
                .tecnico(servicio.getTecnico())
                .montoBase(new BigDecimal("110.00"))
                .porcentaje(new BigDecimal("0.0600"))
                .montoComision(comision)
                .montoNetoTecnico(new BigDecimal("110.00").subtract(comision))
                .estado(EstadoComision.PENDIENTE)
                .fechaLimitePago(fechaLimite)
                .build();
    }
}
