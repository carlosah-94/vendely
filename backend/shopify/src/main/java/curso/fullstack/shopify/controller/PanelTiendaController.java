package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.TiendaActualizarRequest;
import curso.fullstack.shopify.dto.TiendaCrearRequest;
import curso.fullstack.shopify.dto.TiendaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.TiendaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiendas del dueño (rol ADMIN_TIENDA, regla de SecurityConfig para /api/panel/**).
 *
 * POST /api/panel/tiendas        crear una tienda
 * GET  /api/panel/tiendas        mis tiendas
 * GET  /api/panel/tiendas/{id}   ver una de mis tiendas
 * PUT  /api/panel/tiendas/{id}   editar una de mis tiendas
 */
@RestController
@RequestMapping("/api/panel/tiendas")
public class PanelTiendaController {

    private final TiendaService tiendaService;

    public PanelTiendaController(TiendaService tiendaService) {
        this.tiendaService = tiendaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TiendaResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                @Valid @RequestBody TiendaCrearRequest request) {
        return tiendaService.crear(yo, request);
    }

    @GetMapping
    public List<TiendaResponse> misTiendas(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return tiendaService.misTiendas(yo);
    }

    @GetMapping("/{idTienda}")
    public TiendaResponse miTienda(@AuthenticationPrincipal UsuarioAutenticado yo,
                                   @PathVariable Long idTienda) {
        return tiendaService.miTienda(yo, idTienda);
    }

    @PutMapping("/{idTienda}")
    public TiendaResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                     @PathVariable Long idTienda,
                                     @Valid @RequestBody TiendaActualizarRequest request) {
        return tiendaService.actualizar(yo, idTienda, request);
    }
}
