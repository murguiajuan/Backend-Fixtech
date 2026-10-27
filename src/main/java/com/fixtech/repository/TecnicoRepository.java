package com.fixtech.repository;

import com.fixtech.entity.Tecnico;
import com.fixtech.entity.enums.EstadoUsuario;
import com.fixtech.entity.enums.EstadoVerificacion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TecnicoRepository extends JpaRepository<Tecnico, Long> {

    Optional<Tecnico> findByUsuarioId(Long usuarioId);

    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(String dni, Long id);

    @Query("""
            select t.usuario.id, t.nombres, t.apellidos from Tecnico t
            where t.usuario.id in :usuarioIds
            """)
    List<Object[]> buscarNombresPorUsuarioIds(
            @Param("usuarioIds") Collection<Long> usuarioIds);

    @Query("""
            select count(t) from Tecnico t
            where t.estadoVerificacion = :verificacion
              and t.usuario.estado = :estadoUsuario
              and t.bloqueadoPorDeuda = false
            """)
    long contarActivos(
            @Param("verificacion") EstadoVerificacion verificacion,
            @Param("estadoUsuario") EstadoUsuario estadoUsuario);

    @Query(value = "SELECT * FROM tecnico WHERE id = :id FOR UPDATE",
            nativeQuery = true)
    Optional<Tecnico> findByIdForUpdate(@Param("id") Long id);

    @Query("select t.id from Tecnico t where t.bloqueadoPorDeuda = true")
    List<Long> buscarIdsBloqueadosPorDeuda();

    @EntityGraph(attributePaths = {"usuario"})
    List<Tecnico> findByBloqueadoPorDeudaTrue();

    @EntityGraph(attributePaths = {"usuario"})
    @Query("""
            select t from Tecnico t
            where (:busqueda = ''
                   or lower(t.nombres) like :patron
                   or lower(t.apellidos) like :patron
                   or lower(t.dni) like :patron
                   or lower(t.usuario.correo) like :patron)
            order by t.creadoEn desc, t.id desc
            """)
    List<Tecnico> buscarParaAdministracion(
            @Param("busqueda") String busqueda,
            @Param("patron") String patron);

    @EntityGraph(attributePaths = {"usuario"})
    @Query("""
            select t from Tecnico t
            where t.estadoVerificacion = :estado
              and (:busqueda = ''
                   or lower(t.nombres) like :patron
                   or lower(t.apellidos) like :patron
                   or lower(t.dni) like :patron
                   or lower(t.usuario.correo) like :patron)
            order by t.creadoEn desc, t.id desc
            """)
    List<Tecnico> buscarParaAdministracionPorEstado(
            @Param("estado") EstadoVerificacion estado,
            @Param("busqueda") String busqueda,
            @Param("patron") String patron);
}
