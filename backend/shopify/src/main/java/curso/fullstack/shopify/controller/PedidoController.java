package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.Comprobante;
import curso.fullstack.shopify.dto.EnvioPedidoRequest;
import curso.fullstack.shopify.dto.NotificacionResponse;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.VentaRequest;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.ComprobanteService;
import curso.fullstack.shopify.service.NotificacionService;
import curso.fullstack.shopify.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final ComprobanteService comprobanteService;
    private final NotificacionService notificacionService;

    public PedidoController(PedidoService pedidoService, ComprobanteService comprobanteService,
                            NotificacionService notificacionService) {
        this.pedidoService = pedidoService;
        this.comprobanteService = comprobanteService;
        this.notificacionService = notificacionService;
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

    // ---- Issue #3: seguimiento del envio. PATCH y no PUT porque cada una cambia una parte
    // ---- del pedido, no lo reemplaza. Las reglas (que este pagado, en que estado puede estar)
    // ---- viven en PedidoService.

    /** Marca el pedido como enviado, con su empresa y su numero de seguimiento. */
    @PatchMapping("/{idPedido}/envio")
    public VentaResponse enviar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                @PathVariable Long idTienda, @PathVariable Long idPedido,
                                @Valid @RequestBody EnvioPedidoRequest r) {
        return pedidoService.enviar(yo, idTienda, idPedido, r);
    }

    /** Marca como entregado un pedido que ya se envio. */
    @PatchMapping("/{idPedido}/entrega")
    public VentaResponse entregar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                  @PathVariable Long idTienda, @PathVariable Long idPedido) {
        return pedidoService.entregar(yo, idTienda, idPedido);
    }

    /** Cancela un pedido que todavia no se envio: devuelve el stock y reembolsa el pago. */
    @PatchMapping("/{idPedido}/cancelacion")
    public VentaResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                  @PathVariable Long idTienda, @PathVariable Long idPedido) {
        return pedidoService.cancelar(yo, idTienda, idPedido);
    }

    // ---- Issue #3: comprobante y correos de la venta.

    /** Descarga el comprobante de la venta en PDF. */
    @GetMapping("/{idPedido}/comprobante")
    public ResponseEntity<byte[]> comprobante(@AuthenticationPrincipal UsuarioAutenticado yo,
                                              @PathVariable Long idTienda, @PathVariable Long idPedido) {
        return comoPdf(comprobanteService.comprobanteDeLaTienda(yo, idTienda, idPedido));
    }

    /** Los correos que el sistema envio por esta venta, con su estado. */
    @GetMapping("/{idPedido}/notificaciones")
    public List<NotificacionResponse> notificaciones(@AuthenticationPrincipal UsuarioAutenticado yo,
                                                     @PathVariable Long idTienda, @PathVariable Long idPedido) {
        return notificacionService.listarDePedido(yo, idTienda, idPedido);
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

    /**
     * El PDF como descarga. attachment + filename: el navegador lo guarda con ese nombre en
     * lugar de abrirlo dentro de la pagina. Lo usa tambien ClientePedidoController.
     */
    static ResponseEntity<byte[]> comoPdf(Comprobante c) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(c.nombreArchivo()).build().toString())
                .body(c.contenido());
    }
}