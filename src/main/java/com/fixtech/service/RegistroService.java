package com.fixtech.service;

import com.fixtech.dto.RegistroClienteRequest;
import com.fixtech.dto.RegistroTecnicoRequest;
import com.fixtech.dto.UsuarioRegistradoResponse;
import com.fixtech.entity.Cliente;
import com.fixtech.entity.Distrito;
import com.fixtech.entity.Especialidad;
import com.fixtech.entity.Tecnico;
import com.fixtech.entity.Usuario;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import com.fixtech.entity.enums.Rol;
import com.fixtech.exception.BusinessException;
import com.fixtech.repository.ClienteRepository;
import com.fixtech.repository.DistritoRepository;
import com.fixtech.repository.EspecialidadRepository;
import com.fixtech.repository.TecnicoRepository;
import com.fixtech.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RegistroService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TecnicoRepository tecnicoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final DistritoRepository distritoRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioRegistradoResponse registrarCliente(RegistroClienteRequest request) {
        String correo = normalizarCorreo(request.correo());
        validarCorreoDisponible(correo);

        Usuario usuario = usuarioRepository.save(crearUsuario(correo, request.password(), Rol.CLIENTE));
        Cliente cliente = Cliente.builder()
                .usuario(usuario)
                .nombres(request.nombres().trim())
                .apellidos(request.apellidos().trim())
                .dni(textoONulo(request.dni()))
                .celular(request.celular())
                .build();
        clienteRepository.save(cliente);

        return new UsuarioRegistradoResponse(usuario.getId(), usuario.getCorreo(), usuario.getRol());
    }

    @Transactional
    public UsuarioRegistradoResponse registrarTecnico(RegistroTecnicoRequest request) {
        validarModalidades(request);

        String correo = normalizarCorreo(request.correo());
        validarCorreoDisponible(correo);
        validarDniDisponible(request.dni());

        Set<Especialidad> especialidades = obtenerEspecialidades(request.especialidadIds());
        Set<Distrito> distritos = obtenerDistritos(request.distritoIds());

        Usuario usuario = usuarioRepository.save(crearUsuario(correo, request.password(), Rol.TECNICO));
        Tecnico tecnico = Tecnico.builder()
                .usuario(usuario)
                .nombres(request.nombres().trim())
                .apellidos(request.apellidos().trim())
                .dni(request.dni())
                .celular(request.celular())
                .descripcion(textoONulo(request.descripcion()))
                .aniosExperiencia((byte) request.aniosExperiencia())
                .ofreceDomicilio(request.ofreceDomicilio())
                .ofreceTaller(request.ofreceTaller())
                .direccionTaller(textoONulo(request.direccionTaller()))
                .referenciaTaller(textoONulo(request.referenciaTaller()))
                .horarioTaller(textoONulo(request.horarioTaller()))
                .estadoVerificacion(EstadoVerificacion.PENDIENTE)
                .especialidades(especialidades)
                .distritos(distritos)
                .build();
        tecnicoRepository.save(tecnico);

        return new UsuarioRegistradoResponse(usuario.getId(), usuario.getCorreo(), usuario.getRol());
    }

    private Usuario crearUsuario(String correo, String password, Rol rol) {
        return Usuario.builder()
                .correo(correo)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .estado(EstadoUsuario.ACTIVO)
                .aceptoTerminos(true)
                .fechaAceptoTerminos(LocalDateTime.now())
                .build();
    }

    private void validarModalidades(RegistroTecnicoRequest request) {
        if (!request.ofreceDomicilio() && !request.ofreceTaller()) {
            throw new BusinessException("El técnico debe ofrecer al menos una modalidad");
        }
        if (request.ofreceTaller() && !StringUtils.hasText(request.direccionTaller())) {
            throw new BusinessException(
                    "La dirección del taller es obligatoria cuando se ofrece atención en taller");
        }
    }

    private void validarCorreoDisponible(String correo) {
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new BusinessException("El correo ya está registrado");
        }
    }

    private void validarDniDisponible(String dni) {
        if (tecnicoRepository.existsByDni(dni)) {
            throw new BusinessException("El DNI ya está registrado");
        }
    }

    private Set<Especialidad> obtenerEspecialidades(Set<Long> ids) {
        Set<Especialidad> encontradas = new HashSet<>(especialidadRepository.findAllById(ids));
        if (encontradas.size() != ids.size()) {
            throw new BusinessException("Una o más especialidades no existen");
        }
        return encontradas;
    }

    private Set<Distrito> obtenerDistritos(Set<Long> ids) {
        Set<Distrito> encontrados = new HashSet<>(distritoRepository.findAllById(ids));
        if (encontrados.size() != ids.size()) {
            throw new BusinessException("Uno o más distritos no existen");
        }
        return encontrados;
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private String textoONulo(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
