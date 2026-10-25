package com.fixtech.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    private static final Map<String, String> ETIQUETAS = new LinkedHashMap<>();

    static {
        ETIQUETAS.put("auth-controller", "Autenticación");
        ETIQUETAS.put("catalogo-publico-controller", "Catálogos públicos");
        ETIQUETAS.put("solicitud-controller", "Solicitudes");
        ETIQUETAS.put("oferta-controller", "Ofertas y negociación");
        ETIQUETAS.put("servicio-controller", "Servicios");
        ETIQUETAS.put("ajuste-precio-controller", "Ajustes de precio");
        ETIQUETAS.put("comision-controller", "Comisiones");
        ETIQUETAS.put("resena-controller", "Reseñas");
        ETIQUETAS.put("reclamo-controller", "Reclamos");
        ETIQUETAS.put("notificacion-controller", "Notificaciones");
        ETIQUETAS.put("admin-auditoria-controller", "Administración · Auditoría");
        ETIQUETAS.put("admin-catalogo-controller", "Administración · Catálogos");
        ETIQUETAS.put("admin-comision-controller", "Administración · Comisiones");
        ETIQUETAS.put("admin-dashboard-controller", "Administración · Panel");
        ETIQUETAS.put("admin-parametro-controller", "Administración · Parámetros");
        ETIQUETAS.put("admin-reclamo-controller", "Administración · Reclamos");
        ETIQUETAS.put("admin-resena-controller", "Administración · Reseñas");
        ETIQUETAS.put("admin-tecnico-controller", "Administración · Técnicos");
        ETIQUETAS.put("admin-usuario-controller", "Administración · Usuarios");
    }

    @Bean
    public OpenAPI fixtechOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FIXTECH API")
                        .version("1.0.0")
                        .description("""
                                API REST de la plataforma FIXTECH para la gestión de servicios \
                                de soporte y mantenimiento tecnológico en Piura.

                                Autenticación: enviar el token JWT en el encabezado \
                                `Authorization: Bearer <token>`.

                                Las rutas de administración requieren el rol ADMIN. Las \
                                operaciones de clientes y técnicos también validan la \
                                propiedad del recurso.
                                """)
                        .contact(new Contact()
                                .name("Equipo FIXTECH")
                                .email("soporte@fixtech.pe"))
                        .license(new License()
                                .name("Uso académico")
                                .url("https://fixtech.pe")))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                                .name(ESQUEMA_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token de acceso emitido por /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }

    /**
     * Sustituye las etiquetas generadas a partir de los nombres de controlador por
     * nombres de dominio en español, sin modificar los controladores.
     */
    @Bean
    public OpenApiCustomizer fixtechEtiquetas() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            List<String> etiquetasFinales = new ArrayList<>();
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operacion -> {
                if (operacion.getTags() == null) {
                    return;
                }
                List<String> traducidas = operacion.getTags().stream()
                        .map(etiqueta -> ETIQUETAS.getOrDefault(etiqueta, etiqueta))
                        .distinct()
                        .toList();
                operacion.setTags(new ArrayList<>(traducidas));
                traducidas.stream()
                        .filter(etiqueta -> !etiquetasFinales.contains(etiqueta))
                        .forEach(etiquetasFinales::add);
            }));
            openApi.setTags(etiquetasFinales.stream()
                    .sorted()
                    .map(nombre -> new Tag().name(nombre))
                    .toList());
        };
    }
}
