package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.VentaRequest;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
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

    // ---- Issue #8: consultar y eliminar. El controller solo recibe la petición y delega:
    // ---- las reglas (dueño de la tienda, pago aprobado, stock) viven en PedidoService.

    /** Lista de ventas de la tienda (RF3.2). */
    @GetMapping
    public List<PedidoResumenResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                              @PathVariable Long idTienda) {
        return pedidoService.listar(yo, idTienda);
    }

    /** Detalle de una venta con sus productos (RF3.2). */
    @GetMapping("/{idPedido}")
    public VentaResponse detalle(@AuthenticationPrincipal UsuarioAutenticado yo,
                                 @PathVariable Long idTienda, @PathVariable Long idPedido) {
        return pedidoService.detalle(yo, idTienda, idPedido);
    }

    /**
     * Elimina una venta y devuelve su stock (RF3.4).
     * 204 No Content: el borrado salió bien y no hay nada que devolver en el cuerpo.
     */
    @DeleteMapping("/{idPedido}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo,
                         @PathVariable Long idTienda, @PathVariable Long idPedido) {
        pedidoService.eliminar(yo, idTienda, idPedido);
    }
}