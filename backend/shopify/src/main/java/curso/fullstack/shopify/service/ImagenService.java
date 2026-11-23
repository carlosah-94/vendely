package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.ImagenSubidaResponse;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subir y borrar fotos de una tienda (issue #4).
 *
 * Esta clase es la que dice que NO: el tipo de archivo, el tamaño y de quién es la tienda.
 * CloudinaryClient solo habla con el servicio; aquí están las reglas.
 */
@Slf4j
@Service
public class ImagenService {

    /** Formatos que acepta el navegador en todas partes y que Cloudinary sirve bien. */
    private static final List<String> TIPOS = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAXIMO_BYTES = 2L * 1024 * 1024;   // 2 MB

    private final VerificadorTienda verificadorTienda;
    private final CloudinaryClient cloudinary;

    public ImagenService(VerificadorTienda verificadorTienda, CloudinaryClient cloudinary) {
        this.verificadorTienda = verificadorTienda;
        this.cloudinary = cloudinary;
    }

    /**
     * Sube una foto de la tienda y devuelve su enlace. Las fotos van a una carpeta por tienda
     * (vendely/t12): en el panel de Cloudinary se ve de quién es cada una.
     */
    public ImagenSubidaResponse subir(UsuarioAutenticado yo, Long idTienda, MultipartFile archivo) {
        verificadorTienda.delDueno(yo, idTienda);
        exigirConfigurado();
        byte[] contenido = validar(archivo);

        ImagenSubidaResponse subida;
        try {
            subida = cloudinary.subir(contenido, nombreLimpio(archivo), "vendely/t" + idTienda);
        } catch (RestClientException e) {
            log.error("Cloudinary falló al subir una foto de la tienda {}: {}", idTienda, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo subir la foto. Inténtalo de nuevo");
        }
        if (subida == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo subir la foto. Inténtalo de nuevo");
        }
        log.info("Foto subida: tienda={}, public_id={}", idTienda, subida.publicId());
        return subida;
    }

    /**
     * Borra una foto que se subió pero no se llegó a guardar (el dueño la quitó del formulario
     * o cerró sin guardar). Las fotos que sí quedaron guardadas las borra ProductoService
     * cuando se quitan del producto.
     *
     * El public_id tiene que empezar por la carpeta de ESTA tienda: si no, un dueño podría
     * borrar las fotos de otra tienda mandando su identificador.
     */
    public void borrar(UsuarioAutenticado yo, Long idTienda, String publicId) {
        verificadorTienda.delDueno(yo, idTienda);
        exigirConfigurado();
        if (publicId == null || !publicId.startsWith("vendely/t" + idTienda + "/")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Esa foto no es de esta tienda");
        }
        cloudinary.borrar(publicId);
    }

    // ------------------------------------------------------------------ las reglas

    private void exigirConfigurado() {
        if (!cloudinary.estaConfigurado()) {
            // Igual que el pago sin credenciales: la función no está disponible, pero el resto
            // del panel funciona y se pueden seguir pegando enlaces de fotos.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Subir fotos no está disponible por ahora. Puedes pegar el enlace de una imagen");
        }
    }

    /** Comprueba el archivo y devuelve su contenido, para no leerlo dos veces. */
    private byte[] validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No llegó ninguna foto");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "La foto no puede pesar más de 2 MB");
        }
        byte[] contenido = leer(archivo);
        // El tipo lo declara el navegador, así que no basta: se comprueba también el contenido.
        if (!TIPOS.contains(archivo.getContentType()) || !esImagen(contenido)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La foto tiene que ser JPG, PNG o WEBP");
        }
        return contenido;
    }

    /**
     * Mira los primeros bytes del archivo: JPG, PNG y WEBP empiezan cada uno con una marca
     * conocida. Así, un .exe renombrado a .jpg no pasa aunque el navegador diga image/jpeg.
     */
    private static boolean esImagen(byte[] b) {
        if (b.length < 12) {
            return false;
        }
        boolean jpg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpg || png || webp;
    }

    private static byte[] leer(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer la foto");
        }
    }

    /** El nombre original solo se usa para la extensión; Cloudinary genera el suyo. */
    private static String nombreLimpio(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || nombre.isBlank()) {
            return "foto";
        }
        String limpio = nombre.replaceAll("[^A-Za-z0-9._-]", "_");
        return limpio.length() > 80 ? limpio.substring(limpio.length() - 80) : limpio;
    }
}
