package com.fixtech.repository;

import com.fixtech.entity.Solicitud;
import com.fixtech.entity.enums.EstadoOferta;
import com.fixtech.entity.enums.EstadoSolicitud;
import com.fixtech.entity.enums.Modalidad;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    @Query(value = "SELECT * FROM solicitud WHERE id = :solicitudId FOR UPDATE",
            nativeQuery = true)
    Optional<Solicitud> findByIdForUpdate(@Param("solicitudId") Long solicitudId);

    @EntityGraph(attributePaths = {"imagenes", "tipoEquipo", "tipoServicio", "distrito"})
    @Query("""
            select distinct s from Solicitud s
            where s.cliente.id = :clienteId
            order by s.creadoEn desc
            """)
    List<Solicitud> findByClienteIdOrderByCreadoEnDesc(@Param("clienteId") Long clienteId);

    @EntityGraph(attributePaths = {"imagenes", "tipoEquipo", "tipoServicio", "distrito"})
    @Query("""
            select distinct s from Solicitud s
            where s.cliente.id = :clienteId
              and s.estado = :estado
            order by s.creadoEn desc
            """)
    List<Solicitud> findByClienteIdAndEstadoOrderByCreadoEnDesc(
            @Param("clienteId") Long clienteId, @Param("estado") EstadoSolicitud estado);

    @EntityGraph(attributePaths = {"imagenes", "tipoEquipo", "tipoServicio", "distrito"})
    @Query("""
            select distinct s from Solicitud s
            where s.estado in :estados
              and s.fechaVencimiento > :ahora
              and s.distrito.id in :distritoIds
              and s.modalidad in :modalidades
            order by s.creadoEn desc
            """)
    List<Solicitud> buscarDisponibles(
            @Param("estados") Collection<EstadoSolicitud> estados,
            @Param("ahora") LocalDateTime ahora,
            @Param("distritoIds") Collection<Long> distritoIds,
            @Param("modalidades") Collection<Modalidad> modalidades);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Solicitud s
            set s.estado = :estadoVencida
            where s.estado in :estadosActivos
              and s.fechaVencimiento <= :ahora
            """)
    int marcarVencidas(
            @Param("estadoVencida") EstadoSolicitud estadoVencida,
            @Param("estadosActivos") Collection<EstadoSolicitud> estadosActivos,
            @Param("ahora") LocalDateTime ahora);

    long countByEstadoIn(Collection<EstadoSolicitud> estados);

    long countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(
            LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            select count(s) from Solicitud s
            where s.estado in :estadosSolicitud
              and not exists (
                  select o.id from Oferta o
                  where o.solicitud.id = s.id
                    and o.estado in :estadosOferta
              )
            """)
    long contarSinOfertas(
            @Param("estadosSolicitud") Collection<EstadoSolicitud> estadosSolicitud,
            @Param("estadosOferta") Collection<EstadoOferta> estadosOferta);

    @Query("""
            select count(s) from Solicitud s
            where s.creadoEn >= :desde and s.creadoEn < :hasta
              and s.estado in :estadosSolicitud
              and not exists (
                  select o.id from Oferta o
                  where o.solicitud.id = s.id
                    and o.estado in :estadosOferta
              )
            """)
    long contarSinOfertasEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estadosSolicitud") Collection<EstadoSolicitud> estadosSolicitud,
            @Param("estadosOferta") Collection<EstadoOferta> estadosOferta);
}
