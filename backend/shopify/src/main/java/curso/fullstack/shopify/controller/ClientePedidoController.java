package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ConfirmarPagoRequest;
import curso.fullstack.shopify.dto.PagoIniciadoResponse;
import curso.fullstack.shopify.dto.PedidoClienteRequest;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.ResultadoPagoResponse;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.PagoService;
import curso.fullstack.shopify.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Pedidos del cliente que inició sesión (issue #8: "los clientes solo ven sus propios pedidos"),
 * su creación desde la tienda y su pago.
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
    private final PagoService pagoService;

    public ClientePedidoController(PedidoService pedidoService, PagoService pagoService) {
        this.pedidoService = pedidoService;
        this.pagoService = pagoService;
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

    /** Crear un pedido desde la tienda (descuenta el stock; 409 si no alcanza). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                               @Valid @RequestBody PedidoClienteRequest r) {
        return pedidoService.registrarDesdeTienda(yo, r);
    }

    /** Inicia el pago con Mercado Pago: responde la URL a donde enviar al cliente. */
    @PostMapping("/{idPedido}/pago")
    public PagoIniciadoResponse pagar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idPedido) {
        return pagoService.iniciar(yo, idPedido);
    }

    /** Confirma el pago consultándolo en Mercado Pago (nunca se confía en lo que diga el navegador). */
    @PostMapping("/{idPedido}/pago/confirmar")
    public ResultadoPagoResponse confirmarPago(@AuthenticationPrincipal UsuarioAutenticado yo,
                                               @PathVariable Long idPedido,
                                               @Valid @RequestBody ConfirmarPagoRequest r) {
        return pagoService.confirmar(yo, idPedido, r.paymentId());
    }
}
