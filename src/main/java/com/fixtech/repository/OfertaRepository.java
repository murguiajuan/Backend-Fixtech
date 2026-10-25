package com.fixtech.repository;

import com.fixtech.entity.Oferta;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoSolicitud;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    Optional<Oferta> findBySolicitudIdAndTecnicoId(Long solicitudId, Long tecnicoId);

    @Query("""
            select o.solicitud.id from Oferta o
            where o.id = :ofertaId
            """)
    Optional<Long> findSolicitudIdPorOfertaId(@Param("ofertaId") Long ofertaId);

    @EntityGraph(attributePaths = {"tecnico", "contraofertas", "servicio"})
    @Query("""
            select distinct o from Oferta o
            where o.solicitud.id = :solicitudId
            order by o.creadoEn desc, o.id desc
            """)
    List<Oferta> buscarPorSolicitud(@Param("solicitudId") Long solicitudId);

    @EntityGraph(attributePaths = {"solicitud", "tecnico", "contraofertas", "servicio"})
    @Query("""
            select distinct o from Oferta o
            where o.id = :ofertaId
            """)
    Optional<Oferta> buscarDetallePorId(@Param("ofertaId") Long ofertaId);

    @EntityGraph(attributePaths = {"solicitud", "tecnico", "contraofertas", "servicio"})
    @Query("""
            select distinct o from Oferta o
            where o.tecnico.id = :tecnicoId
            order by o.creadoEn desc, o.id desc
            """)
    List<Oferta> buscarPorTecnico(@Param("tecnicoId") Long tecnicoId);

    @Query(value = "SELECT * FROM oferta WHERE id = :ofertaId FOR UPDATE",
            nativeQuery = true)
    Optional<Oferta> findByIdForUpdate(@Param("ofertaId") Long ofertaId);

    @EntityGraph(attributePaths = {"tecnico", "tecnico.usuario"})
    @Query("""
            select o from Oferta o
            where o.solicitud.id = :solicitudId
              and o.id <> :ofertaId
              and o.estado in :estados
            """)
    List<Oferta> buscarOtrasActivas(
            @Param("solicitudId") Long solicitudId,
            @Param("ofertaId") Long ofertaId,
            @Param("estados") Collection<EstadoOferta> estados);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Oferta o
            set o.estado = :nuevoEstado
            where o.solicitud.id = :solicitudId
              and o.id <> :ofertaId
              and o.estado in :estados
            """)
    int rechazarDemas(
            @Param("solicitudId") Long solicitudId,
            @Param("ofertaId") Long ofertaId,
            @Param("estados") Collection<EstadoOferta> estados,
            @Param("nuevoEstado") EstadoOferta nuevoEstado);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Oferta o
            set o.estado = :estadoExpirada
            where o.estado in :estadosActivos
              and o.solicitud.estado in :estadosSolicitud
            """)
    int marcarExpiradasDeSolicitudes(
            @Param("estadoExpirada") EstadoOferta estadoExpirada,
            @Param("estadosActivos") Collection<EstadoOferta> estadosActivos,
            @Param("estadosSolicitud") Collection<EstadoSolicitud> estadosSolicitud);

    long countBySolicitudIdAndEstadoIn(Long solicitudId, Collection<EstadoOferta> estados);
}
