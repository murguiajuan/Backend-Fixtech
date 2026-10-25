package com.fixtech.service;

import com.fixtech.dto.IngresoMensualResponse;
import com.fixtech.dto.ReporteResponse;
import com.fixtech.dto.TecnicoActividadResponse;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoServicio;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ReporteExcelService {

    public static final String TIPO_CONTENIDO =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FORMATO_MONEDA = "\"S/ \"#,##0.00";

    public byte[] generar(ReporteResponse reporte) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            hojaResumen(libro, reporte, estilos);
            hojaServicios(libro, reporte.serviciosPorEstado(), estilos);
            hojaIngresos(libro, reporte.ingresosPorMes(), estilos);
            hojaTecnicos(libro, reporte.tecnicosMasActivos(), estilos);
            hojaReclamos(libro, reporte.reclamosPorEstado(), estilos);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo generar el archivo Excel del reporte", ex);
        }
    }

    private void hojaResumen(
            XSSFWorkbook libro, ReporteResponse reporte, Estilos estilos) {
        Sheet hoja = libro.createSheet("Resumen");
        hoja.setColumnWidth(0, 34 * 256);
        hoja.setColumnWidth(1, 24 * 256);

        Row titulo = hoja.createRow(0);
        Cell celdaTitulo = titulo.createCell(0);
        celdaTitulo.setCellValue("Reporte FIXTECH");
        celdaTitulo.setCellStyle(estilos.titulo());

        filaTexto(hoja, 2, "Periodo", reporte.desde().format(FORMATO_FECHA)
                + " - " + reporte.hasta().format(FORMATO_FECHA), estilos);
        filaEntero(hoja, 3, "Solicitudes registradas", reporte.totalSolicitudes(), estilos);
        filaEntero(hoja, 4, "Solicitudes sin ofertas", reporte.solicitudesSinOfertas(), estilos);
        filaEntero(hoja, 5, "Servicios registrados", reporte.totalServicios(), estilos);
        filaMoneda(hoja, 6, "Volumen de servicios", reporte.volumenServicios(), estilos);
        filaMoneda(hoja, 7, "Ingreso FIXTECH por comisiones",
                reporte.ingresoComisiones(), estilos);
        filaMoneda(hoja, 8, "Calificación promedio",
                reporte.calificacionPromedio(), estilos);
        filaEntero(hoja, 9, "Reseñas visibles", reporte.totalResenas(), estilos);
        filaEntero(hoja, 10, "Reclamos registrados", reporte.totalReclamos(), estilos);
    }

    private void hojaServicios(
            XSSFWorkbook libro,
            Map<EstadoServicio, Long> servicios,
            Estilos estilos) {
        Sheet hoja = libro.createSheet("Servicios por estado");
        hoja.setColumnWidth(0, 26 * 256);
        hoja.setColumnWidth(1, 14 * 256);
        encabezado(hoja, "Estado", "Cantidad", estilos);
        int fila = 1;
        for (Map.Entry<EstadoServicio, Long> entrada : servicios.entrySet()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(entrada.getKey().name());
            row.createCell(1).setCellValue(entrada.getValue());
        }
    }

    private void hojaIngresos(
            XSSFWorkbook libro,
            List<IngresoMensualResponse> ingresos,
            Estilos estilos) {
        Sheet hoja = libro.createSheet("Ingresos por mes");
        hoja.setColumnWidth(0, 14 * 256);
        hoja.setColumnWidth(1, 14 * 256);
        hoja.setColumnWidth(2, 20 * 256);
        hoja.setColumnWidth(3, 22 * 256);
        encabezado(hoja, "Mes", "Servicios", "Volumen", "Ingreso FIXTECH", estilos);
        int fila = 1;
        for (IngresoMensualResponse ingreso : ingresos) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(ingreso.mes());
            row.createCell(1).setCellValue(ingreso.servicios());
            celdaMoneda(row, 2, ingreso.volumenTotal(), estilos);
            celdaMoneda(row, 3, ingreso.ingresoFixtech(), estilos);
        }
    }

    private void hojaTecnicos(
            XSSFWorkbook libro,
            List<TecnicoActividadResponse> tecnicos,
            Estilos estilos) {
        Sheet hoja = libro.createSheet("Técnicos más activos");
        hoja.setColumnWidth(0, 12 * 256);
        hoja.setColumnWidth(1, 34 * 256);
        hoja.setColumnWidth(2, 14 * 256);
        hoja.setColumnWidth(3, 16 * 256);
        encabezado(hoja, "ID", "Técnico", "Servicios", "Calificación", estilos);
        int fila = 1;
        for (TecnicoActividadResponse tecnico : tecnicos) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(tecnico.tecnicoId());
            row.createCell(1).setCellValue(tecnico.nombre());
            row.createCell(2).setCellValue(tecnico.servicios());
            row.createCell(3).setCellValue(tecnico.calificacionPromedio().doubleValue());
        }
    }

    private void hojaReclamos(
            XSSFWorkbook libro,
            Map<EstadoReclamo, Long> reclamos,
            Estilos estilos) {
        Sheet hoja = libro.createSheet("Reclamos por estado");
        hoja.setColumnWidth(0, 22 * 256);
        hoja.setColumnWidth(1, 14 * 256);
        encabezado(hoja, "Estado", "Cantidad", estilos);
        int fila = 1;
        for (Map.Entry<EstadoReclamo, Long> entrada : reclamos.entrySet()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(entrada.getKey().name());
            row.createCell(1).setCellValue(entrada.getValue());
        }
    }

    private void encabezado(Sheet hoja, String primera, String segunda, Estilos estilos) {
        Row row = hoja.createRow(0);
        row.createCell(0).setCellValue(primera);
        row.createCell(1).setCellValue(segunda);
        row.getCell(0).setCellStyle(estilos.encabezado());
        row.getCell(1).setCellStyle(estilos.encabezado());
    }

    private void encabezado(
            Sheet hoja, String primera, String segunda, String tercera, String cuarta,
            Estilos estilos) {
        Row row = hoja.createRow(0);
        String[] valores = {primera, segunda, tercera, cuarta};
        for (int i = 0; i < valores.length; i++) {
            Cell celda = row.createCell(i);
            celda.setCellValue(valores[i]);
            celda.setCellStyle(estilos.encabezado());
        }
    }

    private void filaTexto(
            Sheet hoja, int indice, String etiqueta, String valor, Estilos estilos) {
        Row row = hoja.createRow(indice);
        row.createCell(0).setCellValue(etiqueta);
        row.createCell(1).setCellValue(valor);
        row.getCell(0).setCellStyle(estilos.etiqueta());
    }

    private void filaEntero(
            Sheet hoja, int indice, String etiqueta, long valor, Estilos estilos) {
        Row row = hoja.createRow(indice);
        row.createCell(0).setCellValue(etiqueta);
        row.createCell(1).setCellValue(valor);
        row.getCell(0).setCellStyle(estilos.etiqueta());
    }

    private void filaMoneda(
            Sheet hoja, int indice, String etiqueta, BigDecimal valor, Estilos estilos) {
        Row row = hoja.createRow(indice);
        row.createCell(0).setCellValue(etiqueta);
        row.getCell(0).setCellStyle(estilos.etiqueta());
        celdaMoneda(row, 1, valor, estilos);
    }

    private void celdaMoneda(
            Row row, int indice, BigDecimal valor, Estilos estilos) {
        Cell celda = row.createCell(indice);
        celda.setCellValue(valor.doubleValue());
        celda.setCellStyle(estilos.moneda());
    }

    private static final class Estilos {

        private final CellStyle titulo;
        private final CellStyle encabezado;
        private final CellStyle etiqueta;
        private final CellStyle moneda;

        private Estilos(XSSFWorkbook libro) {
            Font fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);

            Font fuenteEncabezado = libro.createFont();
            fuenteEncabezado.setBold(true);
            fuenteEncabezado.setColor(IndexedColors.WHITE.getIndex());
            encabezado = libro.createCellStyle();
            encabezado.setFont(fuenteEncabezado);
            encabezado.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            encabezado.setBorderBottom(BorderStyle.THIN);

            Font fuenteEtiqueta = libro.createFont();
            fuenteEtiqueta.setBold(true);
            etiqueta = libro.createCellStyle();
            etiqueta.setFont(fuenteEtiqueta);

            moneda = libro.createCellStyle();
            moneda.setDataFormat(libro.createDataFormat().getFormat(FORMATO_MONEDA));
        }

        private CellStyle titulo() {
            return titulo;
        }

        private CellStyle encabezado() {
            return encabezado;
        }

        private CellStyle etiqueta() {
            return etiqueta;
        }

        private CellStyle moneda() {
            return moneda;
        }
    }
}
