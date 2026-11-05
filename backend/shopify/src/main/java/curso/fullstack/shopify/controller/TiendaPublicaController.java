package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.TiendaPublicaResponse;
import curso.fullstack.shopify.service.TiendaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vitrina: datos públicos de una tienda, sin iniciar sesión.
 * GET /api/public/tiendas/{subdominio}
 */
@RestController
@RequestMapping("/api/public/tiendas")
public class TiendaPublicaController {

    private final TiendaService tiendaService;

    public TiendaPublicaController(TiendaService tiendaService) {
        this.tiendaService = tiendaService;
    }

    @GetMapping("/{subdominio}")
    public TiendaPublicaResponse ver(@PathVariable String subdominio) {
        return tiendaService.publica(subdominio);
    }
}
