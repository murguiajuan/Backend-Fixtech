package com.fixtech.entity;

import com.fixtech.entity.enums.EstadoVerificacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tecnico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tecnico {

    private static final int GARANTIA_DIAS_POR_DEFECTO = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false, length = 80)
    private String nombres;

    @Column(nullable = false, length = 80)
    private String apellidos;

    @Column(nullable = false, unique = true, length = 8)
    private String dni;

    @Column(nullable = false, length = 15)
    private String celular;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "anios_experiencia", nullable = false)
    @Builder.Default
    private byte aniosExperiencia = 0;

    @Column(name = "ofrece_domicilio", nullable = false)
    @Builder.Default
    private boolean ofreceDomicilio = false;

    @Column(name = "ofrece_taller", nullable = false)
    @Builder.Default
    private boolean ofreceTaller = false;

    @Column(name = "direccion_taller", length = 200)
    private String direccionTaller;

    @Column(name = "referencia_taller", length = 200)
    private String referenciaTaller;

    @Column(name = "horario_taller", length = 120)
    private String horarioTaller;

    @Column(name = "garantia_dias_defecto", nullable = false)
    @Builder.Default
    private int garantiaDiasDefecto = GARANTIA_DIAS_POR_DEFECTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_verificacion", nullable = false, length = 12)
    @Builder.Default
    private EstadoVerificacion estadoVerificacion = EstadoVerificacion.PENDIENTE;

    @Column(name = "motivo_verificacion", length = 300)
    private String motivoVerificacion;

    @Column(name = "verificado_por")
    private Long verificadoPorId;

    @Column(name = "fecha_verificacion")
    private LocalDateTime fechaVerificacion;

    @Column(name = "bloqueado_por_deuda", nullable = false)
    @Builder.Default
    private boolean bloqueadoPorDeuda = false;

    @Column(name = "saldo_comision_pendiente", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal saldoComisionPendiente = BigDecimal.ZERO;

    @Column(name = "calificacion_promedio", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal calificacionPromedio = BigDecimal.ZERO;

    @Column(name = "total_resenas", nullable = false)
    @Builder.Default
    private int totalResenas = 0;

    @Column(name = "servicios_completados", nullable = false)
    @Builder.Default
    private int serviciosCompletados = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean destacado = false;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tecnico_especialidad",
            joinColumns = @JoinColumn(name = "tecnico_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidad_id"))
    @Builder.Default
    private Set<Especialidad> especialidades = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tecnico_distrito",
            joinColumns = @JoinColumn(name = "tecnico_id"),
            inverseJoinColumns = @JoinColumn(name = "distrito_id"))
    @Builder.Default
    private Set<Distrito> distritos = new HashSet<>();
}
