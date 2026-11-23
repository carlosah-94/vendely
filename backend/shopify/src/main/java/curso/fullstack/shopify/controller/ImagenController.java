package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ImagenSubidaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.ImagenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fotos de una tienda (issue #4). Está aparte de ProductoController porque las usan dos
 * pantallas: las fotos del producto y el logo en Personalizar.
 *
 * La foto se sube apenas se elige, antes de guardar el producto: así el dueño la ve en el
 * formulario y puede ordenarla o quitarla. Lo que se guarda en el producto es su enlace.
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/imagenes")
public class ImagenController {

    private final ImagenService imagenService;

    public ImagenController(ImagenService imagenService) {
        this.imagenService = imagenService;
    }

    /** Sube una foto (JPG, PNG o WEBP, hasta 2 MB) y devuelve su enlace y su public_id. */
    @PostMapping
    public ImagenSubidaResponse subir(@AuthenticationPrincipal UsuarioAutenticado yo,
                                      @PathVariable Long idTienda,
                                      @RequestPart("archivo") MultipartFile archivo) {
        return imagenService.subir(yo, idTienda, archivo);
    }

    /**
     * Borra una foto recién subida que no se llegó a guardar en ningún producto.
     * 204: salió bien y no hay nada que devolver.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@AuthenticationPrincipal UsuarioAutenticado yo,
                       @PathVariable Long idTienda,
                       @RequestParam String publicId) {
        imagenService.borrar(yo, idTienda, publicId);
    }
}
