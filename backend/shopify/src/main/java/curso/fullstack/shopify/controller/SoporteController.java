package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.MensajeSoporteRequest;
import curso.fullstack.shopify.dto.MensajeSoporteResponse;
import curso.fullstack.shopify.dto.SoporteRequest;
import curso.fullstack.shopify.dto.SoporteResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.SoporteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acceso de soporte de Vendely a la tienda (solo el dueño) y la conversación con soporte.
 *
 * GET    /api/panel/tiendas/{id}/soporte            ¿está activo? y lo último que hizo soporte
 * POST   /api/panel/tiendas/{id}/soporte            dar permiso por { "dias": 1..7, "mensaje": "qué necesitas" (opcional) }
 * DELETE /api/panel/tiendas/{id}/soporte            quitar el permiso ya
 * GET    /api/panel/tiendas/{id}/soporte/mensajes   la conversación (el dueño, o soporte con acceso)
 * POST   /api/panel/tiendas/{id}/soporte/mensajes   escribir { "mensaje": "..." } (los dos, con el acceso activo)
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/soporte")
public class SoporteController {

    private final SoporteService soporteService;

    public SoporteController(SoporteService soporteService) {
        this.soporteService = soporteService;
    }

    @GetMapping
    public SoporteResponse ver(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return soporteService.ver(yo, idTienda);
    }

    @PostMapping
    public SoporteResponse permitir(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                    @Valid @RequestBody SoporteRequest request) {
        return soporteService.permitir(yo, idTienda, request.dias(), request.mensaje());
    }

    @GetMapping("/mensajes")
    public List<MensajeSoporteResponse> mensajes(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                 @PathVariable Long idTienda) {
        return soporteService.mensajes(yo, idTienda);
    }

    @PostMapping("/mensajes")
    public List<MensajeSoporteResponse> escribir(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                 @PathVariable Long idTienda,
                                                 @Valid @RequestBody MensajeSoporteRequest request) {
        return soporteService.escribir(yo, idTienda, request.mensaje());
    }

    @DeleteMapping
    public SoporteResponse quitar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return soporteService.quitar(yo, idTienda);
    }
}
