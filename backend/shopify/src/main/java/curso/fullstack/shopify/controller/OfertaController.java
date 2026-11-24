package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.CambioEstadoRequest;
import curso.fullstack.shopify.dto.OfertaRequest;
import curso.fullstack.shopify.dto.OfertaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.OfertaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Ofertas automáticas (panel → Descuentos). Requiere el permiso de productos.
 * GET    /api/panel/tiendas/{idTienda}/ofertas
 * POST   /api/panel/tiendas/{idTienda}/ofertas
 * PUT    /api/panel/tiendas/{idTienda}/ofertas/{idOferta}
 * PATCH  /api/panel/tiendas/{idTienda}/ofertas/{idOferta}/estado   { "activa": false } = pausar
 * DELETE /api/panel/tiendas/{idTienda}/ofertas/{idOferta}          (solo si nunca se usó)
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/ofertas")
public class OfertaController {

    private final OfertaService ofertaService;

    public OfertaController(OfertaService ofertaService) {
        this.ofertaService = ofertaService;
    }

    @GetMapping
    public List<OfertaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return ofertaService.listar(yo, idTienda);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfertaResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                @Valid @RequestBody OfertaRequest r) {
        return ofertaService.crear(yo, idTienda, r);
    }

    @PutMapping("/{idOferta}")
    public OfertaResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                     @PathVariable Long idOferta, @Valid @RequestBody OfertaRequest r) {
        return ofertaService.actualizar(yo, idTienda, idOferta, r);
    }

    @PatchMapping("/{idOferta}/estado")
    public OfertaResponse cambiarEstado(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                        @PathVariable Long idOferta, @Valid @RequestBody CambioEstadoRequest r) {
        return ofertaService.cambiarEstado(yo, idTienda, idOferta, r.activa());
    }

    @DeleteMapping("/{idOferta}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                         @PathVariable Long idOferta) {
        ofertaService.eliminar(yo, idTienda, idOferta);
    }
}
