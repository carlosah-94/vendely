package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.ImagenSubidaResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Sube y borra fotos en Cloudinary (API REST, sin SDK, igual que los clientes de las pasarelas).
 *
 * Por qué un servicio externo y no una carpeta del servidor: en Render el disco es temporal, se
 * borra en cada despliegue y cada vez que el contenedor se duerme. Una foto guardada ahí
 * desaparece sola a los días. Cloudinary además entrega las imágenes por su propia red.
 *
 * Sin CLOUDINARY_URL el cliente queda apagado: subir responde 503 y pegar un enlace sigue
 * funcionando, así que el proyecto corre completo sin crear ninguna cuenta.
 */
@Slf4j
@Component
public class CloudinaryClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    /** cloudinary://<api_key>:<api_secret>@<cloud_name> */
    private static final Pattern URL_CLOUDINARY = Pattern.compile("^cloudinary://([^:]+):([^@]+)@(.+)$");

    /**
     * De una URL de entrega al public_id: lo que va después de /upload/, sin la versión (v123…)
     * ni la extensión. https://res.cloudinary.com/demo/image/upload/v17/vendely/t1/abc.jpg
     * → vendely/t1/abc
     */
    private static final Pattern URL_ENTREGA = Pattern.compile(
            "^https://res\\.cloudinary\\.com/[^/]+/image/upload/(?:v\\d+/)?(.+?)\\.[a-zA-Z0-9]+$");

    private final RestClient http;
    private final String apiKey;
    private final String apiSecret;
    private final boolean configurado;

    public CloudinaryClient(@Value("${app.cloudinary.url:}") String cloudinaryUrl) {
        Matcher m = URL_CLOUDINARY.matcher(cloudinaryUrl == null ? "" : cloudinaryUrl.trim());
        boolean valido = m.matches();
        this.apiKey = valido ? m.group(1) : "";
        this.apiSecret = valido ? m.group(2) : "";
        this.configurado = valido;
        this.http = RestClient.builder()
                .baseUrl("https://api.cloudinary.com/v1_1/" + (valido ? m.group(3) : "sin-configurar"))
                .build();
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank() && !valido) {
            log.warn("CLOUDINARY_URL no tiene el formato cloudinary://clave:secreto@nube: las fotos no se subirán");
        }
    }

    /** false si falta CLOUDINARY_URL: subir una foto responde 503 y solo se pueden pegar enlaces. */
    public boolean estaConfigurado() {
        return configurado;
    }

    /**
     * Sube la foto y devuelve su enlace y su public_id.
     * La carpeta agrupa por tienda (vendely/t12), para que en el panel de Cloudinary se vea
     * de quién es cada imagen y se pueda limpiar una tienda entera si hiciera falta.
     */
    public ImagenSubidaResponse subir(byte[] contenido, String nombreArchivo, String carpeta) {
        Map<String, String> firmados = new TreeMap<>();     // ordenado: la firma lo exige
        firmados.put("folder", carpeta);
        firmados.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("file", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        });
        firmados.forEach(cuerpo::add);
        cuerpo.add("api_key", apiKey);
        cuerpo.add("signature", firmar(firmados));

        Map<String, Object> r = http.post().uri("/image/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(cuerpo)
                .retrieve()
                .body(JSON);

        String url = r == null ? null : texto(r.get("secure_url"));
        String publicId = r == null ? null : texto(r.get("public_id"));
        return (url == null || publicId == null) ? null : new ImagenSubidaResponse(url, publicId);
    }

    /**
     * Sube un archivo de la conversación con soporte (V26). Las fotos van como imagen; el resto
     * (Excel, PDF, ZIP...) como "raw", que Cloudinary guarda tal cual y entrega para descargar.
     * use_filename: el enlace conserva el nombre y la extensión ("productos_x8k2.xlsx"), así
     * el archivo baja con un nombre que se entiende.
     */
    public ImagenSubidaResponse subirArchivo(byte[] contenido, String nombreArchivo, String carpeta, boolean esImagen) {
        Map<String, String> firmados = new TreeMap<>();     // ordenado: la firma lo exige
        firmados.put("folder", carpeta);
        firmados.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        firmados.put("unique_filename", "true");
        firmados.put("use_filename", "true");

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("file", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        });
        firmados.forEach(cuerpo::add);
        cuerpo.add("api_key", apiKey);
        cuerpo.add("signature", firmar(firmados));

        Map<String, Object> r = http.post().uri(esImagen ? "/image/upload" : "/raw/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(cuerpo)
                .retrieve()
                .body(JSON);

        String url = r == null ? null : texto(r.get("secure_url"));
        String publicId = r == null ? null : texto(r.get("public_id"));
        return (url == null || publicId == null) ? null : new ImagenSubidaResponse(url, publicId);
    }

    /**
     * Pide que se borre una foto. Devuelve true si Cloudinary confirma que ya no está.
     * Nunca lanza: borrar la foto es limpieza, y que falle no puede impedir guardar el producto.
     */
    public boolean borrar(String publicId) {
        return borrar(publicId, true);
    }

    /** Igual que borrar(publicId), para una foto (esImagen) o un archivo "raw" (Excel, PDF...). */
    public boolean borrar(String publicId, boolean esImagen) {
        if (!configurado || publicId == null || publicId.isBlank()) {
            return false;
        }
        Map<String, String> firmados = new TreeMap<>();
        firmados.put("public_id", publicId);
        firmados.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        firmados.forEach(cuerpo::add);
        cuerpo.add("api_key", apiKey);
        cuerpo.add("signature", firmar(firmados));
        try {
            Map<String, Object> r = http.post().uri(esImagen ? "/image/destroy" : "/raw/destroy")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(cuerpo)
                    .retrieve()
                    .body(JSON);
            String resultado = r == null ? null : texto(r.get("result"));
            if (!"ok".equals(resultado)) {
                log.warn("Cloudinary no borró la foto {}: {}", publicId, resultado);
            }
            return "ok".equals(resultado);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar la foto {} en Cloudinary: {}", publicId, e.getMessage());
            return false;
        }
    }

    /**
     * El public_id de una URL nuestra, o null si el enlace no es de Cloudinary (una foto
     * pegada de otra página): esas no son nuestras y no se intentan borrar.
     */
    public static String publicIdDe(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = URL_ENTREGA.matcher(url.trim());
        return m.matches() ? m.group(1) : null;
    }

    /**
     * La firma que exige Cloudinary: los parámetros ordenados por nombre como clave=valor
     * unidos por &, con el api_secret pegado al final, todo pasado por SHA-1.
     * Así Cloudinary sabe que la subida viene de nosotros sin que el secreto viaje nunca.
     */
    private String firmar(Map<String, String> parametros) {
        StringBuilder texto = new StringBuilder();
        for (Map.Entry<String, String> e : new LinkedHashMap<>(parametros).entrySet()) {
            if (!texto.isEmpty()) {
                texto.append('&');
            }
            texto.append(e.getKey()).append('=').append(e.getValue());
        }
        texto.append(apiSecret);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(sha1.digest(texto.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 siempre existe en Java", e);
        }
    }

    private static String texto(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString();
    }
}
