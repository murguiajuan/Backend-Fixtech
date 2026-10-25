package com.fixtech.repository;

import com.fixtech.entity.Reclamo;
import com.fixtech.entity.enums.EstadoReclamo;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

    boolean existsByServicioIdAndEstadoIn(
            Long servicioId, Collection<EstadoReclamo> estados);

    @EntityGraph(attributePaths = {
        "fotos", "cliente", "servicio", "servicio.cliente", "servicio.cliente.usuario",
        "servicio.tecnico", "servicio.tecnico.usuario"
    })
    @Query("""
            select distinct r from Reclamo r
            where r.servicio.id = :servicioId
            order by r.creadoEn desc, r.id desc
            """)
    List<Reclamo> buscarPorServicio(@Param("servicioId") Long servicioId);

    @EntityGraph(attributePaths = {
        "fotos", "cliente", "servicio", "servicio.cliente", "servicio.cliente.usuario",
        "servicio.tecnico", "servicio.tecnico.usuario"
    })
    @Query("select distinct r from Reclamo r where r.id = :reclamoId")
    Optional<Reclamo> buscarDetallePorId(@Param("reclamoId") Long reclamoId);

    @Query(value = "SELECT * FROM reclamo WHERE id = :reclamoId FOR UPDATE",
            nativeQuery = true)
    Optional<Reclamo> findByIdForUpdate(@Param("reclamoId") Long reclamoId);

    @EntityGraph(attributePaths = {
        "fotos", "cliente", "servicio", "servicio.cliente", "servicio.cliente.usuario",
        "servicio.tecnico", "servicio.tecnico.usuario"
    })
    @Query("""
            select distinct r from Reclamo r
            order by r.creadoEn desc, r.id desc
            """)
    List<Reclamo> listarTodos();

    @EntityGraph(attributePaths = {
        "fotos", "cliente", "servicio", "servicio.cliente", "servicio.cliente.usuario",
        "servicio.tecnico", "servicio.tecnico.usuario"
    })
    @Query("""
            select distinct r from Reclamo r
            where r.estado = :estado
            order by r.creadoEn asc, r.id asc
            """)
    List<Reclamo> listarPorEstado(@Param("estado") EstadoReclamo estado);

    long countByEstado(EstadoReclamo estado);

    long countByEstadoIn(Collection<EstadoReclamo> estados);

    long countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(
            LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            select r.estado, count(r) from Reclamo r
            where r.creadoEn >= :desde and r.creadoEn < :hasta
            group by r.estado
            """)
    List<Object[]> contarPorEstadoEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
