package com.fixtech.service;

import com.fixtech.config.ArchivoProperties;
import com.fixtech.exception.BadRequestException;
import com.fixtech.exception.NotFoundException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class ArchivoService {

    private static final byte[] CABECERA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] CABECERA_PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final ArchivoProperties properties;
    private final Path directorioBase;

    public ArchivoService(ArchivoProperties properties) {
        this.properties = properties;
        this.directorioBase = Paths.get(properties.getDirectorio()).toAbsolutePath().normalize();
    }

    public String guardarImagenSolicitud(Long solicitudId, MultipartFile archivo) {
        return guardarImagen(Path.of("solicitudes", solicitudId.toString()), archivo);
    }

    public String guardarFotoPerfilTecnico(Long tecnicoId, MultipartFile archivo) {
        if (tecnicoId == null) {
            throw new IllegalArgumentException("El técnico es obligatorio");
        }
        return guardarImagen(Path.of("tecnicos", tecnicoId.toString(), "perfil"), archivo);
    }

    public String guardarImagenServicio(
            Long servicioId, String categoria, MultipartFile archivo) {
        if (categoria == null || !categoria.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("La categoría de la imagen no es válida");
        }
        return guardarImagen(
                Path.of("servicios", servicioId.toString(), categoria), archivo);
    }

    public String guardarComprobantePago(Long tecnicoId, MultipartFile archivo) {
        if (tecnicoId == null) {
            throw new IllegalArgumentException("El técnico es obligatorio");
        }
        return guardarImagen(Path.of("pagos", tecnicoId.toString()), archivo);
    }

    public String guardarImagenReclamo(Long reclamoId, MultipartFile archivo) {
        if (reclamoId == null) {
            throw new IllegalArgumentException("El reclamo es obligatorio");
        }
        return guardarImagen(Path.of("reclamos", reclamoId.toString()), archivo);
    }

    private String guardarImagen(Path rutaRelativa, MultipartFile archivo) {
        validarArchivo(archivo);
        TipoImagen tipo = detectarTipo(archivo);
        BufferedImage original = leerImagen(archivo);
        BufferedImage procesada = ajustarImagen(original, tipo);

        Path directorio = directorioBase.resolve(rutaRelativa).normalize();
        if (!directorio.startsWith(directorioBase)) {
            throw new IllegalArgumentException("La ruta de la imagen no es válida");
        }
        try {
            Files.createDirectories(directorio);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el directorio de imágenes", ex);
        }

        Path destino = directorio.resolve(UUID.randomUUID() + tipo.extension());
        try {
            if (tipo == TipoImagen.JPEG) {
                escribirJpeg(procesada, destino);
            } else {
                if (!ImageIO.write(procesada, "png", destino.toFile())) {
                    throw new IOException("No hay un escritor PNG disponible");
                }
            }
            if (Files.size(destino) > properties.getMaxImagenBytes()) {
                Files.deleteIfExists(destino);
                throw new BadRequestException("La imagen comprimida excede el tamaño permitido");
            }
        } catch (IOException ex) {
            eliminarArchivo(destino);
            throw new IllegalStateException("No se pudo guardar la imagen", ex);
        }

        return directorioBase.relativize(destino).toString().replace('\\', '/');
    }

    public RecursoImagen cargar(String rutaArchivo) {
        Path archivo = directorioBase.resolve(rutaArchivo).normalize();
        if (!archivo.startsWith(directorioBase) || !Files.isRegularFile(archivo)) {
            throw new NotFoundException("Imagen no encontrada");
        }
        MediaType tipo = rutaArchivo.toLowerCase(Locale.ROOT).endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;
        return new RecursoImagen(new FileSystemResource(archivo), tipo);
    }

    public void eliminar(String rutaArchivo) {
        if (rutaArchivo == null || rutaArchivo.isBlank()) {
            return;
        }
        Path archivo = directorioBase.resolve(rutaArchivo).normalize();
        if (!archivo.startsWith(directorioBase)) {
            return;
        }
        eliminarArchivo(archivo);
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("Cada foto debe ser un archivo no vacío");
        }
        if (archivo.getSize() > properties.getMaxImagenBytes()) {
            long maximoMb = Math.max(1, properties.getMaxImagenBytes() / (1024 * 1024));
            throw new BadRequestException(
                    "Cada foto debe pesar como máximo %d MB".formatted(maximoMb));
        }
    }

    private TipoImagen detectarTipo(MultipartFile archivo) {
        byte[] cabecera;
        try (InputStream entrada = archivo.getInputStream()) {
            cabecera = entrada.readNBytes(8);
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo leer la imagen");
        }

        if (coincide(cabecera, CABECERA_JPEG)) {
            return TipoImagen.JPEG;
        }
        if (coincide(cabecera, CABECERA_PNG)) {
            return TipoImagen.PNG;
        }
        throw new BadRequestException("Solo se permiten fotos JPG o PNG");
    }

    private BufferedImage leerImagen(MultipartFile archivo) {
        try (InputStream entrada = archivo.getInputStream()) {
            BufferedImage imagen = ImageIO.read(entrada);
            if (imagen == null) {
                throw new BadRequestException("El archivo no es una imagen válida");
            }
            return imagen;
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo procesar la imagen");
        }
    }

    private BufferedImage ajustarImagen(BufferedImage original, TipoImagen tipo) {
        int ancho = original.getWidth();
        int alto = original.getHeight();
        double factor = Math.min(
                1.0, (double) properties.getMaxDimension() / Math.max(ancho, alto));
        int nuevoAncho = Math.max(1, (int) Math.round(ancho * factor));
        int nuevoAlto = Math.max(1, (int) Math.round(alto * factor));

        if (nuevoAncho == ancho && nuevoAlto == alto) {
            return tipo == TipoImagen.JPEG ? convertirARgb(original) : original;
        }

        int tipoImagen = tipo == TipoImagen.JPEG
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage salida = new BufferedImage(nuevoAncho, nuevoAlto, tipoImagen);
        Graphics2D grafico = salida.createGraphics();
        try {
            if (tipo == TipoImagen.JPEG) {
                grafico.setColor(Color.WHITE);
                grafico.fillRect(0, 0, nuevoAncho, nuevoAlto);
            }
            grafico.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            grafico.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            grafico.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
        } finally {
            grafico.dispose();
        }
        return salida;
    }

    private BufferedImage convertirARgb(BufferedImage original) {
        BufferedImage salida = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = salida.createGraphics();
        try {
            grafico.setColor(Color.WHITE);
            grafico.fillRect(0, 0, original.getWidth(), original.getHeight());
            grafico.drawImage(original, 0, 0, null);
        } finally {
            grafico.dispose();
        }
        return salida;
    }

    private void escribirJpeg(BufferedImage imagen, Path destino) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        if (escritor == null) {
            throw new IllegalStateException("No hay un escritor JPEG disponible");
        }
        try (ImageOutputStream salida = ImageIO.createImageOutputStream(destino.toFile())) {
            if (salida == null) {
                throw new IOException("No se pudo abrir el archivo de destino");
            }
            escritor.setOutput(salida);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            if (parametros.canWriteCompressed()) {
                parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parametros.setCompressionQuality((float) properties.getCalidadJpeg());
            }
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
    }

    private void eliminarArchivo(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ex) {
            log.warn("No se pudo eliminar el archivo {}", archivo.getFileName(), ex);
        }
    }

    private boolean coincide(byte[] datos, byte[] cabecera) {
        if (datos.length < cabecera.length) {
            return false;
        }
        for (int i = 0; i < cabecera.length; i++) {
            if (datos[i] != cabecera[i]) {
                return false;
            }
        }
        return true;
    }

    public record RecursoImagen(Resource recurso, MediaType tipoContenido) {
    }

    private enum TipoImagen {
        JPEG(".jpg"),
        PNG(".png");

        private final String extension;

        TipoImagen(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }
}
