package com.fixtech.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void traduce_las_etiquetas_tecnicas_a_nombres_de_dominio() {
        OpenAPI openApi = new OpenAPI();
        Operation operacion = new Operation()
                .tags(List.of("auth-controller", "catalogo-publico-controller"));
        openApi.path("/prueba", new PathItem().get(operacion));

        OpenApiCustomizer customizer = config.fixtechEtiquetas();
        customizer.customise(openApi);

        assertEquals(List.of("Autenticación", "Catálogos públicos"), operacion.getTags());
        assertEquals(
                List.of("Autenticación", "Catálogos públicos"),
                openApi.getTags().stream().map(Tag::getName).toList());
    }

    @Test
    void conserva_etiquetas_desconocidas_y_ordena_la_lista_superior() {
        OpenAPI openApi = new OpenAPI();
        Operation operacion = new Operation().tags(List.of("zeta", "auth-controller"));
        openApi.path("/prueba", new PathItem().get(operacion));

        config.fixtechEtiquetas().customise(openApi);

        assertEquals(List.of("zeta", "Autenticación"), operacion.getTags());
        assertEquals(
                List.of("Autenticación", "zeta"),
                openApi.getTags().stream().map(Tag::getName).toList());
    }

    @Test
    void tolera_un_documento_sin_rutas() {
        OpenAPI openApi = new OpenAPI();

        config.fixtechEtiquetas().customise(openApi);

        assertTrue(openApi.getTags() == null || openApi.getTags().isEmpty());
    }
}
