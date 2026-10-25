package com.fixtech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fixtech.dto.IngresoMensualResponse;
import com.fixtech.dto.ReporteResponse;
import com.fixtech.dto.TecnicoActividadResponse;
import com.fixtech.entity.enums.EstadoReclamo;
import com.fixtech.entity.enums.EstadoServicio;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReporteExcelServiceTest {

    private final ReporteExcelService reporteExcelService = new ReporteExcelService();

    @Test
    void genera_un_libro_excel_con_las_hojas_del_reporte() throws Exception {
        ReporteResponse reporte = new ReporteResponse(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 25),
                15,
                2,
                9,
                Map.of(EstadoServicio.CONFIRMADO, 4L),
                new BigDecimal("500.00"),
                new BigDecimal("30.00"),
                List.of(new IngresoMensualResponse(
                        "2026-10", 4, new BigDecimal("200.00"), new BigDecimal("12.00"))),
                List.of(new TecnicoActividadResponse(
                        2L, "Ana Uno", 9L, new BigDecimal("4.50"))),
                new BigDecimal("4.25"),
                7,
                3,
                Map.of(EstadoReclamo.ABIERTO, 2L));

        byte[] archivo = reporteExcelService.generar(reporte);

        assertTrue(archivo.length > 0);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            assertEquals(5, libro.getNumberOfSheets());
            assertEquals("Resumen", libro.getSheetName(0));
            assertEquals("Servicios por estado", libro.getSheetName(1));
            assertEquals("Ingresos por mes", libro.getSheetName(2));
            assertEquals("Técnicos más activos", libro.getSheetName(3));
            assertEquals("Reclamos por estado", libro.getSheetName(4));

            var resumen = libro.getSheetAt(0);
            assertEquals("Reporte FIXTECH",
                    resumen.getRow(0).getCell(0).getStringCellValue());
            assertEquals("01/10/2026 - 25/10/2026",
                    resumen.getRow(2).getCell(1).getStringCellValue());
            assertEquals(15, (long) resumen.getRow(3).getCell(1).getNumericCellValue());
            assertEquals(500.0, resumen.getRow(6).getCell(1).getNumericCellValue());

            var ingresos = libro.getSheetAt(2);
            assertEquals("2026-10", ingresos.getRow(1).getCell(0).getStringCellValue());
            assertEquals(12.0, ingresos.getRow(1).getCell(3).getNumericCellValue());
        }
    }

    @Test
    void genera_el_libro_aunque_el_periodo_no_tenga_datos() throws Exception {
        ReporteResponse reporte = new ReporteResponse(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
                0,
                0,
                0,
                Map.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                0,
                0,
                Map.of());

        byte[] archivo = reporteExcelService.generar(reporte);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            assertEquals(5, libro.getNumberOfSheets());
            assertEquals(0, libro.getSheetAt(1).getLastRowNum());
            assertEquals(0.0, libro.getSheetAt(0).getRow(6).getCell(1)
                    .getNumericCellValue());
        }
    }
}
