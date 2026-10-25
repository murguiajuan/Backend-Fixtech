package com.fixtech.mapper;

import com.fixtech.dto.ReclamoResponse;
import com.fixtech.entity.Reclamo;
import com.fixtech.entity.ReclamoFoto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReclamoMapper {

    public ReclamoResponse toResponse(Reclamo reclamo) {
        return new ReclamoResponse(
                reclamo.getId(),
                reclamo.getServicio().getId(),
                reclamo.getCliente().getId(),
                nombre(reclamo),
                reclamo.getDescripcion(),
                reclamo.getEstado(),
                reclamo.getRespuestaTecnico(),
                reclamo.getResolucion(),
                reclamo.getResueltoPorId(),
                reclamo.getCreadoEn(),
                reclamo.getFechaResolucion(),
                reclamo.getFotos() == null
                        ? List.of()
                        : reclamo.getFotos().stream()
                                .map(ReclamoFoto::getId)
                                .toList());
    }

    private String nombre(Reclamo reclamo) {
        String nombres = reclamo.getCliente().getNombres() == null
                ? ""
                : reclamo.getCliente().getNombres();
        String apellidos = reclamo.getCliente().getApellidos() == null
                ? ""
                : reclamo.getCliente().getApellidos();
        return (nombres + " " + apellidos).trim();
    }
}
