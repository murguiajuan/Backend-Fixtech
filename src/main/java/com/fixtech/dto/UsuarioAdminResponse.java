package com.fixtech.dto;

import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Rol;
import java.time.LocalDateTime;

public record UsuarioAdminResponse(
        Long id,
        String correo,
        Rol rol,
        EstadoUsuario estado,
        String motivoSuspension,
        String nombres,
        String apellidos,
        LocalDateTime creadoEn) {
}
