package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ProductoPublicoResponse;
import curso.fullstack.shopify.dto.TiendaPublicaResponse;
import curso.fullstack.shopify.service.ProductoService;
import curso.fullstack.shopify.service.TiendaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vitrina: datos públicos de una tienda, sin iniciar sesión.
 * GET /api/public/tiendas/{subdominio}
 * GET /api/public/tiendas/{subdominio}/productos   (catálogo, issue #10)
 *
 * El catálogo va aquí (y no en ProductoController) porque comparte la ruta pública
 * /api/public/**, que SecurityConfig deja abierta para GET: un visitante lo ve sin token.
 */
@RestController
@RequestMapping("/api/public/tiendas")
public class TiendaPublicaController {

    private final TiendaService tiendaService;
    private final ProductoService productoService;

    public TiendaPublicaController(TiendaService tiendaService, ProductoService productoService) {
        this.tiendaService = tiendaService;
        this.productoService = productoService;
    }

    @GetMapping("/{subdominio}")
    public TiendaPublicaResponse ver(@PathVariable String subdominio) {
        return tiendaService.publica(subdominio);
    }

    /** Catálogo público: productos activos de una tienda activa, con precio y disponibilidad. */
    @GetMapping("/{subdominio}/productos")
    public List<ProductoPublicoResponse> catalogo(@PathVariable String subdominio) {
        return productoService.catalogo(subdominio);
    }
}
