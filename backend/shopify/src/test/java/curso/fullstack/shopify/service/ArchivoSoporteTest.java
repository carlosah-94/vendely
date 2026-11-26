package curso.fullstack.shopify.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import curso.fullstack.shopify.exception.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Las reglas de los adjuntos de soporte (V26): qué se acepta y que el contenido coincida con
 * la extensión. Sin Spring ni base: solo bytes.
 */
class ArchivoSoporteTest {

    private static final byte[] XLSX = {'P', 'K', 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
    private static final byte[] XLS = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

    @Test
    void aceptaElExcelElPdfLasFotosYElCsv() {
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ArchivoSoporte.validar("productos.xlsx", XLSX).tipo());
        assertEquals("application/vnd.ms-excel", ArchivoSoporte.validar("viejo.XLS", XLS).tipo());
        assertEquals("application/pdf", ArchivoSoporte.validar("catalogo.pdf", PDF).tipo());
        assertTrue(ArchivoSoporte.validar("logo.png", PNG).esImagen());
        assertEquals("text/csv", ArchivoSoporte.validar("precios.csv",
                "nombre,precio\nPolo,39.90\n".getBytes(StandardCharsets.UTF_8)).tipo());
    }

    @Test
    void unEjecutableRenombradoNoPasa() {
        assertThrows(ApiException.class, () -> ArchivoSoporte.validar("productos.xlsx", EXE));
        assertThrows(ApiException.class, () -> ArchivoSoporte.validar("fotos.zip", EXE));
        assertThrows(ApiException.class, () -> ArchivoSoporte.validar("precios.csv", EXE));
    }

    @Test
    void unaExtensionQueNoEstaEnLaListaNoPasa() {
        assertThrows(ApiException.class, () -> ArchivoSoporte.validar("programa.exe", EXE));
        assertThrows(ApiException.class, () -> ArchivoSoporte.validar("sin-extension", XLSX));
        // Una foto que dice ser PDF tampoco.
        assertThrows(ApiException.class, () -> ArchivoSoporte.validar("catalogo.pdf", PNG));
    }

    @Test
    void masDe10MbNoPasa() {
        MockMultipartFile grande = new MockMultipartFile("archivo", "enorme.pdf", "application/pdf",
                new byte[(int) ArchivoSoporte.MAXIMO_BYTES + 1]);
        ApiException e = assertThrows(ApiException.class, () -> ArchivoSoporte.validar(grande));
        assertEquals(413, e.getStatus().value());
    }

    @Test
    void elNombreSeLimpiaSinPerderLaExtension() {
        assertEquals("productos.xlsx", ArchivoSoporte.nombreLimpio("C:\\Users\\ana\\productos.xlsx"));
        assertEquals("Lista de precios (nov).csv", ArchivoSoporte.nombreLimpio("Lista de precios (nov).csv"));
        assertFalse(ArchivoSoporte.nombreLimpio("<script>.pdf").contains("<"));
        String largo = ArchivoSoporte.nombreLimpio("a".repeat(300) + ".xlsx");
        assertTrue(largo.length() <= 120 && largo.endsWith(".xlsx"));
        assertEquals("archivo", ArchivoSoporte.nombreLimpio("  "));
    }
}
