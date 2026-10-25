package com.fixtech.service;

import com.fixtech.dto.CambioEstadoUsuarioRequest;
import com.fixtech.dto.UsuarioAdminResponse;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Rol;
import com.fixtech.entity.enums.TipoNotificacion;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.BusinessException;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.UsuarioRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministradorUsuarioService {

    private static final int MAX_MOTIVO = 300;
    private static final int MAX_BUSQUEDA = 120;

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final AdministradorRepository administradorRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    public List<UsuarioAdminResponse> listar(
            Rol rol, EstadoUsuario estado, String busqueda) {
        String filtro = normalizar(busqueda);
        if (filtro != null && filtro.length() > MAX_BUSQUEDA) {
            throw new BadRequestException(
                    "La búsqueda no puede superar los %d caracteres"
                            .formatted(MAX_BUSQUEDA));
        }
        String patron = filtro == null
                ? ""
                : "%" + filtro.toLowerCase(Locale.ROOT) + "%";
        List<Usuario> usuarios = usuarioRepository.buscarParaAdministracion(
                rol == null ? "" : rol.name(),
                estado == null ? "" : estado.name(),
                filtro == null ? "" : filtro,
                patron);
        Map<Long, String[]> nombres = cargarNombres(usuarios);
        return usuarios.stream()
                .map(usuario -> aResponse(usuario, nombres.get(usuario.getId())))
                .toList();
    }

    @Transactional
    public UsuarioAdminResponse cambiarEstado(
            Long adminUsuarioId,
            Long usuarioId,
            CambioEstadoUsuarioRequest request) {
        administradorRepository.findByUsuarioId(adminUsuarioId)
                .orElseThrow(() -> new ForbiddenException(
                        "El usuario no tiene perfil de administrador"));
        if (request == null || request.estado() == null) {
            throw new BadRequestException("Debe indicar el nuevo estado del usuario");
        }
        if (request.estado() != EstadoUsuario.ACTIVO
                && request.estado() != EstadoUsuario.SUSPENDIDO) {
            throw new BadRequestException(
                    "Solo se permite suspender o reactivar una cuenta");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (usuario.getId().equals(adminUsuarioId)) {
            throw new ForbiddenException(
                    "No puede cambiar el estado de su propia cuenta");
        }
        if (usuario.getRol() == Rol.ADMIN) {
            throw new ForbiddenException(
                    "No se puede cambiar el estado de una cuenta administradora");
        }
        if (usuario.getEstado() == request.estado()) {
            throw new BusinessException(
                    "El usuario ya se encuentra en estado "
                            + request.estado());
        }

        String motivo = normalizar(request.motivo());
        if (request.estado() == EstadoUsuario.SUSPENDIDO) {
            if (motivo == null) {
                throw new BadRequestException(
                        "El motivo es obligatorio para suspender la cuenta");
            }
            if (motivo.length() > MAX_MOTIVO) {
                throw new BadRequestException(
                        "El motivo no puede superar los %d caracteres"
                                .formatted(MAX_MOTIVO));
            }
            usuario.setMotivoSuspension(motivo);
            usuario.setEstado(EstadoUsuario.SUSPENDIDO);
        } else {
            usuario.setMotivoSuspension(null);
            usuario.setEstado(EstadoUsuario.ACTIVO);
        }
        usuarioRepository.save(usuario);

        boolean suspendido = usuario.getEstado() == EstadoUsuario.SUSPENDIDO;
        notificacionService.crear(
                usuario.getId(),
                suspendido
                        ? TipoNotificacion.USUARIO_SUSPENDIDO
                        : TipoNotificacion.USUARIO_REACTIVADO,
                suspendido ? "Cuenta suspendida" : "Cuenta reactivada",
                suspendido
                        ? "Su cuenta fue suspendida. Motivo: " + motivo
                        : "Su cuenta fue reactivada. Ya puede iniciar sesión nuevamente.",
                "USUARIO",
                usuario.getId());
        auditoriaService.registrar(
                adminUsuarioId,
                suspendido ? "SUSPENDER_USUARIO" : "REACTIVAR_USUARIO",
                "usuario",
                usuario.getId(),
                suspendido
                        ? "Estado anterior ACTIVO, nuevo SUSPENDIDO. Motivo: " + motivo
                        : "Estado anterior SUSPENDIDO, nuevo ACTIVO");
        return aResponse(usuario, cargarNombres(List.of(usuario)).get(usuario.getId()));
    }

    private Map<Long, String[]> cargarNombres(List<Usuario> usuarios) {
        Map<Long, String[]> nombres = new HashMap<>();
        if (usuarios.isEmpty()) {
            return nombres;
        }
        List<Long> ids = usuarios.stream().map(Usuario::getId).toList();
        agregarNombres(nombres, clienteRepository.buscarNombresPorUsuarioIds(ids));
        agregarNombres(nombres, tecnicoRepository.buscarNombresPorUsuarioIds(ids));
        agregarNombres(nombres, administradorRepository.buscarNombresPorUsuarioIds(ids));
        return nombres;
    }

    private void agregarNombres(
            Map<Long, String[]> nombres, List<Object[]> filas) {
        for (Object[] fila : filas) {
            nombres.put(
                    ((Number) fila[0]).longValue(),
                    new String[]{
                        (String) fila[1],
                        (String) fila[2]
                    });
        }
    }

    private UsuarioAdminResponse aResponse(Usuario usuario, String[] nombres) {
        return new UsuarioAdminResponse(
                usuario.getId(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getEstado(),
                usuario.getMotivoSuspension(),
                nombres == null ? null : nombres[0],
                nombres == null ? null : nombres[1],
                usuario.getCreadoEn());
    }

    private String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
