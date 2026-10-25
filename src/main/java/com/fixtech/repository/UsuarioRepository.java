package com.fixtech.repository;

import com.fixtech.entity.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    @Query(value = """
            SELECT u.* FROM usuario u
            LEFT JOIN cliente c ON c.usuario_id = u.id
            LEFT JOIN tecnico t ON t.usuario_id = u.id
            LEFT JOIN administrador a ON a.usuario_id = u.id
            WHERE (:rol = '' OR u.rol = :rol)
              AND (:estado = '' OR u.estado = :estado)
              AND (:busqueda = '' OR LOWER(u.correo) LIKE :patron
                   OR LOWER(COALESCE(c.nombres, t.nombres, a.nombres)) LIKE :patron
                   OR LOWER(COALESCE(c.apellidos, t.apellidos, a.apellidos)) LIKE :patron)
            ORDER BY u.creado_en DESC, u.id DESC
            """, nativeQuery = true)
    List<Usuario> buscarParaAdministracion(
            @Param("rol") String rol,
            @Param("estado") String estado,
            @Param("busqueda") String busqueda,
            @Param("patron") String patron);
}
