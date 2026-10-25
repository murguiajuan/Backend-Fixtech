package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fixtech.config.ArchivoProperties;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.NotFoundException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class ArchivoServiceTest {

    @TempDir
    Path directorioTemporal;

    @Test
    void guarda_y_recupera_una_imagen_png() throws IOException {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "fotos", "foto.png", "image/png", imagenPng(20, 10));

        String ruta = servicio.guardarImagenSolicitud(5L, archivo);

        assertTrue(ruta.startsWith("solicitudes/5/"));
        assertTrue(ruta.endsWith(".png"));
        ArchivoService.RecursoImagen recurso = servicio.cargar(ruta);
        assertEquals(MediaType.IMAGE_PNG, recurso.tipoContenido());
        assertTrue(recurso.recurso().exists());
    }

    @Test
    void guarda_una_imagen_jpeg_y_la_recupera_como_jpeg() throws IOException {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "fotos", "foto.jpg", "image/jpeg", imagenJpeg(20, 10));

        String ruta = servicio.guardarImagenSolicitud(5L, archivo);

        assertTrue(ruta.endsWith(".jpg"));
        ArchivoService.RecursoImagen recurso = servicio.cargar(ruta);
        assertEquals(MediaType.IMAGE_JPEG, recurso.tipoContenido());
        assertTrue(recurso.recurso().exists());
    }

    @Test
    void guarda_una_imagen_asociada_a_un_servicio() throws IOException {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "foto", "foto.png", "image/png", imagenPng(20, 10));

        String ruta = servicio.guardarImagenServicio(9L, "avances", archivo);

        assertTrue(ruta.startsWith("servicios/9/avances/"));
        assertTrue(ruta.endsWith(".png"));
        assertTrue(Files.exists(directorioTemporal.resolve(ruta)));
    }

    @Test
    void rechaza_una_categoria_de_imagen_invalida() {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "foto", "foto.png", "image/png", imagenPng(10, 10));

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.guardarImagenServicio(9L, "../fuera", archivo));
    }

    @Test
    void rechaza_un_archivo_que_no_es_imagen() {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "fotos", "virus.txt", "text/plain", "no soy una imagen".getBytes());

        assertThrows(BadRequestException.class,
                () -> servicio.guardarImagenSolicitud(5L, archivo));
    }

    @Test
    void rechaza_un_archivo_que_supera_el_tamano_maximo() {
        ArchivoProperties propiedades = propiedades();
        propiedades.setMaxImagenBytes(10);
        ArchivoService servicio = servicio(propiedades);
        MockMultipartFile archivo = new MockMultipartFile(
                "fotos", "foto.png", "image/png", new byte[11]);

        assertThrows(BadRequestException.class,
                () -> servicio.guardarImagenSolicitud(5L, archivo));
    }

    @Test
    void reduce_la_imagen_si_supera_la_dimension_maxima() throws IOException {
        ArchivoProperties propiedades = propiedades();
        propiedades.setMaxDimension(10);
        ArchivoService servicio = servicio(propiedades);
        MockMultipartFile archivo = new MockMultipartFile(
                "fotos", "grande.png", "image/png", imagenPng(40, 20));

        String ruta = servicio.guardarImagenSolicitud(5L, archivo);

        BufferedImage imagen = ImageIO.read(directorioTemporal.resolve(ruta).toFile());
        assertEquals(10, imagen.getWidth());
        assertEquals(5, imagen.getHeight());
    }

    @Test
    void elimina_una_imagen_guardada() {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "fotos", "foto.png", "image/png", imagenPng(10, 10));
        String ruta = servicio.guardarImagenSolicitud(5L, archivo);

        servicio.eliminar(ruta);

        assertFalse(Files.exists(directorioTemporal.resolve(ruta)));
    }

    @Test
    void rechaza_la_lectura_de_una_ruta_inexistente() {
        ArchivoService servicio = servicio(propiedades());

        assertThrows(NotFoundException.class,
                () -> servicio.cargar("solicitudes/5/" + UUID.randomUUID() + ".png"));
    }

    @Test
    void guarda_un_comprobante_de_pago_en_la_carpeta_del_tecnico() throws IOException {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "comprobante", "pago.png", "image/png", imagenPng(20, 10));

        String ruta = servicio.guardarComprobantePago(7L, archivo);

        assertTrue(ruta.startsWith("pagos/7/"));
        assertTrue(ruta.endsWith(".png"));
        assertTrue(Files.exists(directorioTemporal.resolve(ruta)));
    }

    @Test
    void rechaza_un_comprobante_sin_tecnico() {
        ArchivoService servicio = servicio(propiedades());
        MockMultipartFile archivo = new MockMultipartFile(
                "comprobante", "pago.png", "image/png", imagenPng(10, 10));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.guardarComprobantePago(null, archivo));
    }

    private ArchivoService servicio(ArchivoProperties propiedades) {
        propiedades.setDirectorio(directorioTemporal.toString());
        return new ArchivoService(propiedades);
    }

    private ArchivoProperties propiedades() {
        ArchivoProperties propiedades = new ArchivoProperties();
        propiedades.setMaxImagenBytes(5L * 1024 * 1024);
        propiedades.setMaxDimension(1600);
        propiedades.setCalidadJpeg(0.8);
        return propiedades;
    }

    private byte[] imagenPng(int ancho, int alto) {
        try {
            BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
            Graphics2D grafico = imagen.createGraphics();
            grafico.setColor(Color.BLUE);
            grafico.fillRect(0, 0, ancho, alto);
            grafico.dispose();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] imagenJpeg(int ancho, int alto) {
        try {
            BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D grafico = imagen.createGraphics();
            grafico.setColor(Color.RED);
            grafico.fillRect(0, 0, ancho, alto);
            grafico.dispose();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "jpg", salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
