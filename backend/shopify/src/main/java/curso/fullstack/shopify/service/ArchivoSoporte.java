package curso.fullstack.shopify.service;

import curso.fullstack.shopify.exception.ApiException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * Las reglas de los archivos que se adjuntan en la conversación con soporte (V26): qué tipos,
 * cuánto pesan y que el contenido sea de verdad lo que dice la extensión.
 *
 * Igual que con las fotos (ImagenService), la extensión y el tipo los decide quien sube el
 * archivo, así que no bastan: se miran también los primeros bytes. Un .exe renombrado a
 * .xlsx no pasa.
 */
public final class ArchivoSoporte {

    public static final long MAXIMO_BYTES = 10L * 1024 * 1024;   // 10 MB
    static final String TIPOS_TEXTO = "Excel, CSV, PDF, Word, TXT, fotos (JPG, PNG, WEBP) o ZIP";

    /** Qué contenido se espera para cada extensión. */
    private enum Marca { JPG, PNG, WEBP, PDF, ZIP_OFFICE, OLE_OFFICE, TEXTO }

    private static final Map<String, Marca> EXTENSIONES = Map.ofEntries(
            Map.entry("jpg", Marca.JPG), Map.entry("jpeg", Marca.JPG),
            Map.entry("png", Marca.PNG), Map.entry("webp", Marca.WEBP),
            Map.entry("pdf", Marca.PDF),
            Map.entry("xlsx", Marca.ZIP_OFFICE), Map.entry("docx", Marca.ZIP_OFFICE), Map.entry("zip", Marca.ZIP_OFFICE),
            Map.entry("xls", Marca.OLE_OFFICE), Map.entry("doc", Marca.OLE_OFFICE),
            Map.entry("csv", Marca.TEXTO), Map.entry("txt", Marca.TEXTO));

    /** El tipo que se guarda: el que corresponde a la extensión, no el que mandó el navegador. */
    private static final Map<String, String> TIPOS = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("zip", "application/zip"),
            Map.entry("xls", "application/vnd.ms-excel"), Map.entry("doc", "application/msword"),
            Map.entry("csv", "text/csv"), Map.entry("txt", "text/plain"));

    /** Un archivo que pasó las reglas, listo para subir. */
    public record Valido(byte[] contenido, String nombre, String tipo) {
        public boolean esImagen() {
            return tipo.startsWith("image/");
        }
    }

    private ArchivoSoporte() {
    }

    /** Comprueba el archivo y lo devuelve leído; si no cumple, ApiException con un mensaje claro. */
    public static Valido validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No llegó ningún archivo");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo no puede pesar más de 10 MB");
        }
        String nombre = nombreLimpio(archivo.getOriginalFilename());
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");
        }
        return validar(nombre, contenido);
    }

    /** La parte que no depende de Spring (la usan las pruebas). */
    static Valido validar(String nombre, byte[] contenido) {
        String extension = extension(nombre);
        Marca marca = EXTENSIONES.get(extension);
        if (marca == null || !coincide(marca, contenido)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Puedes adjuntar " + TIPOS_TEXTO);
        }
        return new Valido(contenido, nombre, TIPOS.get(extension));
    }

    private static boolean coincide(Marca marca, byte[] b) {
        return switch (marca) {
            case JPG -> b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case PNG -> empieza(b, 0x89, 'P', 'N', 'G');
            case WEBP -> b.length >= 12 && empieza(b, 'R', 'I', 'F', 'F')
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case PDF -> empieza(b, '%', 'P', 'D', 'F');
            case ZIP_OFFICE -> empieza(b, 'P', 'K', 0x03, 0x04);
            case OLE_OFFICE -> empieza(b, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case TEXTO -> esTexto(b);
        };
    }

    private static boolean empieza(byte[] b, int... marca) {
        if (b.length < marca.length) {
            return false;
        }
        for (int i = 0; i < marca.length; i++) {
            if ((b[i] & 0xFF) != marca[i]) {
                return false;
            }
        }
        return true;
    }

    /** Un CSV o TXT no tiene marca: basta con que no haya bytes nulos (los binarios casi siempre los tienen). */
    private static boolean esTexto(byte[] b) {
        int hasta = Math.min(b.length, 8192);
        for (int i = 0; i < hasta; i++) {
            if (b[i] == 0) {
                return false;
            }
        }
        return b.length > 0;
    }

    private static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * El nombre para mostrar en el chat: sin rutas ni caracteres raros, y como mucho 120
     * caracteres (conservando la extensión).
     */
    static String nombreLimpio(String original) {
        if (original == null || original.isBlank()) {
            return "archivo";
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String limpio = base.replaceAll("[^\\p{L}\\p{N} ._()-]", "_").strip();
        if (limpio.isEmpty()) {
            return "archivo";
        }
        if (limpio.length() > 120) {
            String ext = extension(limpio);
            limpio = limpio.substring(0, 119 - ext.length()) + "." + ext;
        }
        return limpio;
    }
}
