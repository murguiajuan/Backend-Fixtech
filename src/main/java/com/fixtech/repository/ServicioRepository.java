package com.fixtech.repository;

import com.fixtech.entity.Servicio;
import com.fixtech.entity.enums.EstadoServicio;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    Optional<Servicio> findByOfertaId(Long ofertaId);

    Optional<Servicio> findBySolicitudId(Long solicitudId);

    boolean existsByOfertaId(Long ofertaId);

    @EntityGraph(attributePaths = {
        "solicitud", "oferta", "cliente", "cliente.usuario", "tecnico", "tecnico.usuario"
    })
    @Query("""
            select s from Servicio s
            where s.cliente.id = :clienteId
            order by s.creadoEn desc, s.id desc
            """)
    List<Servicio> buscarPorCliente(@Param("clienteId") Long clienteId);

    @EntityGraph(attributePaths = {
        "solicitud", "oferta", "cliente", "cliente.usuario", "tecnico", "tecnico.usuario"
    })
    @Query("""
            select s from Servicio s
            where s.tecnico.id = :tecnicoId
            order by s.creadoEn desc, s.id desc
            """)
    List<Servicio> buscarPorTecnico(@Param("tecnicoId") Long tecnicoId);

    @EntityGraph(attributePaths = {
        "solicitud", "solicitud.distrito", "solicitud.tipoEquipo", "solicitud.tipoServicio",
        "oferta", "cliente", "cliente.usuario", "tecnico", "tecnico.usuario"
    })
    @Query("select s from Servicio s where s.id = :servicioId")
    Optional<Servicio> buscarDetallePorId(@Param("servicioId") Long servicioId);

    @Query(value = "SELECT * FROM servicio WHERE id = :servicioId FOR UPDATE",
            nativeQuery = true)
    Optional<Servicio> findByIdForUpdate(@Param("servicioId") Long servicioId);

    @Query("""
            select s.id from Servicio s
            where s.estado = :estado
              and s.fechaLimiteConformidad is not null
              and s.fechaLimiteConformidad <= :ahora
            order by s.fechaLimiteConformidad asc
            """)
    List<Long> buscarIdsParaConfirmacionAutomatica(
            @Param("estado") EstadoServicio estado,
            @Param("ahora") LocalDateTime ahora);

    @Query("select s.estado, count(s) from Servicio s group by s.estado")
    List<Object[]> contarPorEstado();

    @Query("""
            select s.estado, count(s) from Servicio s
            where s.creadoEn >= :desde and s.creadoEn < :hasta
            group by s.estado
            """)
    List<Object[]> contarPorEstadoEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    long countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(
            LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            select t.id, t.nombres, t.apellidos, t.calificacionPromedio, count(s)
            from Servicio s join s.tecnico t
            where s.creadoEn >= :desde and s.creadoEn < :hasta
              and s.estado <> :estadoExcluido
            group by t.id, t.nombres, t.apellidos, t.calificacionPromedio
            order by count(s) desc, t.id asc
            """)
    List<Object[]> tecnicosMasActivos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estadoExcluido") EstadoServicio estadoExcluido);
}
