package com.fixtech.repository;

import com.fixtech.entity.Administrador;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {

    Optional<Administrador> findByUsuarioId(Long usuarioId);

    @Query("select a.usuario.id from Administrador a")
    List<Long> buscarUsuarioIds();

    @Query("""
            select a.usuario.id, a.nombres, a.apellidos from Administrador a
            where a.usuario.id in :usuarioIds
            """)
    List<Object[]> buscarNombresPorUsuarioIds(
            @Param("usuarioIds") Collection<Long> usuarioIds);
}
