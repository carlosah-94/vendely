package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ClienteResponse.DireccionResponse;
import curso.fullstack.shopify.dto.DireccionRequest;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.ClienteService;
import jakarta.validation.Valid;
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

    public ClienteDireccionController(ClienteService clienteService) {
        this.clienteService = clienteService;
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
}
