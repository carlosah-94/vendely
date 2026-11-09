package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ProductoRequest;
import curso.fullstack.shopify.dto.ProductoResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.ProductoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * CRUD de productos de una tienda, desde el panel del dueño (issue #10).
 * Va bajo /api/panel/** porque esa ruta ya exige el rol ADMIN_TIENDA (SecurityConfig).
 * El catálogo público (sin token) está en TiendaPublicaController.
 * El controller solo recibe la petición y delega: las reglas están en ProductoService.
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<ProductoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                         @PathVariable Long idTienda) {
        return productoService.listar(yo, idTienda);
    }

    @GetMapping("/{idProducto}")
    public ProductoResponse ver(@AuthenticationPrincipal UsuarioAutenticado yo,
                                @PathVariable Long idTienda, @PathVariable Long idProducto) {
        return productoService.ver(yo, idTienda, idProducto);
    }

    /** 201 Created: se creó el producto (con su variante, precio y stock). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                  @PathVariable Long idTienda,
                                  @Valid @RequestBody ProductoRequest r) {
        return productoService.crear(yo, idTienda, r);
    }

    @PutMapping("/{idProducto}")
    public ProductoResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                       @PathVariable Long idTienda, @PathVariable Long idProducto,
                                       @Valid @RequestBody ProductoRequest r) {
        return productoService.actualizar(yo, idTienda, idProducto, r);
    }

    /** 204 No Content: se borró y no hay nada que devolver. */
    @DeleteMapping("/{idProducto}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo,
                         @PathVariable Long idTienda, @PathVariable Long idProducto) {
        productoService.eliminar(yo, idTienda, idProducto);
    }
}
