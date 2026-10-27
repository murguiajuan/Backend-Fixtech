package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Rol;

public record PerfilUsuarioResponse(
        Long id,
        String correo,
        Rol rol,
        EstadoUsuario estado,
        String nombres,
        String apellidos,
        String fotoUrl,
        PerfilTecnicoResponse tecnico) {
}
