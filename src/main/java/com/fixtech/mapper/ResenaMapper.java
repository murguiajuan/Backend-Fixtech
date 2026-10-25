package com.fixtech.mapper;

import com.fixtech.dto.ResenaResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Resena;
import com.fixtech.entity.Tecnico;
import org.springframework.stereotype.Component;

@Component
public class ResenaMapper {

    public ResenaResponse toResponse(Resena resena) {
        return new ResenaResponse(
                resena.getId(),
                resena.getServicio().getId(),
                resena.getTecnico().getId(),
                nombreTecnico(resena.getTecnico()),
                nombrePublico(resena.getCliente()),
                resena.getEstrellas(),
                resena.getPuntualidad(),
                resena.getTrato(),
                resena.getCalidad(),
                resena.getPrecioJusto(),
                resena.getComentario(),
                resena.getRespuestaTecnico(),
                resena.getFechaRespuesta(),
                resena.getEstado(),
                resena.getMotivoReporte(),
                resena.getCreadoEn());
    }

    private String nombreTecnico(Tecnico tecnico) {
        return nombre(tecnico.getNombres(), tecnico.getApellidos());
    }

    private String nombrePublico(Cliente cliente) {
        String nombres = cliente.getNombres() == null ? "" : cliente.getNombres().trim();
        String apellidos = cliente.getApellidos() == null ? "" : cliente.getApellidos().trim();
        if (apellidos.isEmpty()) {
            return nombres.isEmpty() ? "Cliente" : nombres;
        }
        return (nombres + " " + apellidos.charAt(0) + ".").trim();
    }

    private String nombre(String nombres, String apellidos) {
        String nombre = nombres == null ? "" : nombres;
        String apellido = apellidos == null ? "" : apellidos;
        return (nombre + " " + apellido).trim();
    }
}
