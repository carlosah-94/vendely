package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.PedidoService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Pedidos del cliente que inició sesión (issue #8: "los clientes solo ven sus propios pedidos").
 *
 * Es un controller aparte del panel porque la ruta /api/cliente/** exige el rol CLIENTE
 * (SecurityConfig): un dueño no entra aquí y un cliente no entra al panel.
 * Las rutas no llevan idTienda ni idLogin: se toman del token, para que nadie pueda
 * cambiarlos en la URL y ver pedidos ajenos.
 */
@RestController
@RequestMapping("/api/cliente/pedidos")
public class ClientePedidoController {

    private final PedidoService pedidoService;

    public ClientePedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /** Mis pedidos en la tienda donde estoy registrado. */
    @GetMapping
    public List<PedidoResumenResponse> misPedidos(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return pedidoService.misPedidos(yo);
    }

    /** Detalle de uno de mis pedidos (404 si no es mío). */
    @GetMapping("/{idPedido}")
    public VentaResponse miPedido(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idPedido) {
        return pedidoService.miPedido(yo, idPedido);
    }
}
