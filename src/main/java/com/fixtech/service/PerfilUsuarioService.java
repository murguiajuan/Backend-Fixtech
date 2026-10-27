package com.fixtech.service;

import com.fixtech.dto.CatalogoSimpleResponse;
import com.fixtech.dto.PerfilTecnicoResponse;
import com.fixtech.dto.PerfilUsuarioResponse;
import com.fixtech.entity.Administrador;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.Modalidad;
import com.fixtech.exception.ForbiddenException;
import com.fixtech.exception.NotFoundException;
import com.fixtech.exception.UnauthorizedException;
import com.fixtech.repository.AdministradorRepository;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.UsuarioRepository;
import com.fixtech.util.UrlArchivos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfilUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final AdministradorRepository administradorRepository;

    public PerfilUsuarioResponse obtener(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Token inválido o expirado"));
        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        return switch (usuario.getRol()) {
            case CLIENTE -> perfilCliente(usuario);
            case TECNICO -> perfilTecnico(usuario);
            case ADMIN -> perfilAdministrador(usuario);
        };
    }

    private PerfilUsuarioResponse perfilCliente(Usuario usuario) {
        Cliente cliente = clienteRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Perfil de cliente no encontrado"));
        return base(usuario, cliente.getNombres(), cliente.getApellidos(),
                cliente.getFotoUrl(), null);
    }

    private PerfilUsuarioResponse perfilTecnico(Usuario usuario) {
        Tecnico tecnico = tecnicoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Perfil de técnico no encontrado"));
        PerfilTecnicoResponse perfilTecnico = new PerfilTecnicoResponse(
                tecnico.getId(),
                tecnico.getDni(),
                tecnico.getCelular(),
                tecnico.getEstadoVerificacion(),
                tecnico.getMotivoVerificacion(),
                tecnico.isBloqueadoPorDeuda(),
                tecnico.getSaldoComisionPendiente(),
                modalidades(tecnico),
                tecnico.isOfreceDomicilio(),
                tecnico.isOfreceTaller(),
                especialidades(tecnico),
                distritos(tecnico),
                tecnico.getDescripcion(),
                Byte.toUnsignedInt(tecnico.getAniosExperiencia()),
                tecnico.getDireccionTaller(),
                tecnico.getReferenciaTaller(),
                tecnico.getHorarioTaller(),
                tecnico.getGarantiaDiasDefecto(),
                tecnico.getCalificacionPromedio(),
                tecnico.getTotalResenas(),
                tecnico.getServiciosCompletados(),
                tecnico.isDestacado());
        return base(usuario, tecnico.getNombres(), tecnico.getApellidos(),
                tecnico.getFotoUrl() == null ? null : UrlArchivos.fotoTecnico(tecnico.getId()),
                perfilTecnico);
    }

    private PerfilUsuarioResponse perfilAdministrador(Usuario usuario) {
        Administrador administrador = administradorRepository
                .findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Perfil de administrador no encontrado"));
        return base(usuario, administrador.getNombres(),
                administrador.getApellidos(), null, null);
    }

    private PerfilUsuarioResponse base(
            Usuario usuario,
            String nombres,
            String apellidos,
            String fotoUrl,
            PerfilTecnicoResponse perfilTecnico) {
        return new PerfilUsuarioResponse(
                usuario.getId(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getEstado(),
                nombres,
                apellidos,
                fotoUrl,
                perfilTecnico);
    }

    private List<Modalidad> modalidades(Tecnico tecnico) {
        List<Modalidad> modalidades = new ArrayList<>(2);
        if (tecnico.isOfreceDomicilio()) {
            modalidades.add(Modalidad.DOMICILIO);
        }
        if (tecnico.isOfreceTaller()) {
            modalidades.add(Modalidad.TALLER);
        }
        return List.copyOf(modalidades);
    }

    private List<CatalogoSimpleResponse> especialidades(Tecnico tecnico) {
        return tecnico.getEspecialidades().stream()
                .sorted(Comparator.comparing(Especialidad::getNombre))
                .map(especialidad -> new CatalogoSimpleResponse(
                        especialidad.getId(),
                        especialidad.getNombre(),
                        especialidad.isActivo()))
                .toList();
    }

    private List<CatalogoSimpleResponse> distritos(Tecnico tecnico) {
        return tecnico.getDistritos().stream()
                .sorted(Comparator.comparing(Distrito::getNombre))
                .map(distrito -> new CatalogoSimpleResponse(
                        distrito.getId(),
                        distrito.getNombre(),
                        distrito.isActivo()))
                .toList();
    }
}
