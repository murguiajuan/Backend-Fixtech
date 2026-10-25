package com.fixtech.repository;

import com.fixtech.entity.Cliente;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByUsuarioId(Long usuarioId);

    @Query("""
            select c.usuario.id, c.nombres, c.apellidos from Cliente c
            where c.usuario.id in :usuarioIds
            """)
    List<Object[]> buscarNombresPorUsuarioIds(
            @Param("usuarioIds") Collection<Long> usuarioIds);
}
