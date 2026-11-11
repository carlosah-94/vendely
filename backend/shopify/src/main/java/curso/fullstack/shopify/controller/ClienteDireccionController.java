package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ClienteResponse.DireccionResponse;
import curso.fullstack.shopify.dto.CotizacionEnvioResponse;
import curso.fullstack.shopify.dto.DireccionRequest;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.ClienteService;
import curso.fullstack.shopify.service.EnvioService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Direcciones del cliente que inició sesión en la tienda. /api/cliente/** exige el rol CLIENTE.
 * El idLogin y la tienda salen del token, nunca del cuerpo.
 */
@RestController
@RequestMapping("/api/cliente/direcciones")
public class ClienteDireccionController {

    private final ClienteService clienteService;
    private final EnvioService envioService;

    public ClienteDireccionController(ClienteService clienteService, EnvioService envioService) {
        this.clienteService = clienteService;
        this.envioService = envioService;
    }

    @GetMapping
    public List<DireccionResponse> mias(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return clienteService.misDirecciones(yo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DireccionResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                   @Valid @RequestBody DireccionRequest r) {
        return clienteService.crearMiDireccion(yo, r);
    }

    /**
     * GET /api/cliente/direcciones/{id}/envio?subtotal=120.50
     * Cuánto cuesta enviar a esa dirección (o si la tienda no envía ahí), antes de pagar.
     */
    @GetMapping("/{idDireccion}/envio")
    public CotizacionEnvioResponse envio(@AuthenticationPrincipal UsuarioAutenticado yo,
                                         @PathVariable Long idDireccion,
                                         @RequestParam(required = false) BigDecimal subtotal) {
        return envioService.cotizar(yo, idDireccion, subtotal);
    }
}
