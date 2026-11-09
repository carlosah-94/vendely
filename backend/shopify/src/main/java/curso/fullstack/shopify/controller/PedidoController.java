package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.VentaRequest;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Ventas de una tienda, desde el panel del dueño. /api/panel/** exige
 * ADMIN_TIENDA (SecurityConfig).
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@AuthenticationPrincipal UsuarioAutenticado yo,
            @PathVariable Long idTienda,
            @Valid @RequestBody VentaRequest r) {
        return pedidoService.registrar(yo, idTienda, r);
    }
}