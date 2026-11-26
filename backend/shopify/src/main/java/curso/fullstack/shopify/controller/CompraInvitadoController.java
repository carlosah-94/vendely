package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ClienteResponse.DireccionResponse;
import curso.fullstack.shopify.dto.ConfirmarPagoRequest;
import curso.fullstack.shopify.dto.CotizacionEnvioResponse;
import curso.fullstack.shopify.dto.CuponAplicadoResponse;
import curso.fullstack.shopify.dto.DireccionInvitadoRequest;
import curso.fullstack.shopify.dto.IniciarPagoRequest;
import curso.fullstack.shopify.dto.InvitadoCreadoResponse;
import curso.fullstack.shopify.dto.PagoIniciadoResponse;
import curso.fullstack.shopify.dto.PedidoInvitadoRequest;
import curso.fullstack.shopify.dto.ResultadoPagoResponse;
import curso.fullstack.shopify.dto.ValidarCuponRequest;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.ClaveInvitado;
import curso.fullstack.shopify.service.ClienteService;
import curso.fullstack.shopify.service.ComprobanteService;
import curso.fullstack.shopify.service.CuponService;
import curso.fullstack.shopify.service.EnvioService;
import curso.fullstack.shopify.service.PagoService;
import curso.fullstack.shopify.service.PedidoService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compra como invitado: comprar sin crear cuenta, como en Shopify.
 *
 * Quien compra sin cuenta no tiene token de sesión. En su lugar, cada dirección y cada pedido que
 * crea viene con una clave secreta (ClaveInvitado), y para verlo, cotizarlo o pagarlo debe mandarla
 * en la cabecera X-Clave-Invitado. Es el mismo principio que el enlace de seguimiento que llega
 * por correo: quien no tiene la clave recibe 404, como si el pedido no existiera.
 *
 * La tienda debe estar activa y aceptar invitados (permite_invitados; también lo exige la base).
 */
@RestController
@RequestMapping("/api/public/tiendas/{subdominio}/invitado")
public class CompraInvitadoController {

    static final String CABECERA = "X-Clave-Invitado";

    private final TiendaRepository tiendaRepository;
    private final ClaveInvitado claves;
    private final ClienteService clienteService;
    private final EnvioService envioService;
    private final CuponService cuponService;
    private final PedidoService pedidoService;
    private final PagoService pagoService;
    private final ComprobanteService comprobanteService;

    public CompraInvitadoController(TiendaRepository tiendaRepository, ClaveInvitado claves,
                                    ClienteService clienteService, EnvioService envioService,
                                    CuponService cuponService, PedidoService pedidoService,
                                    PagoService pagoService, ComprobanteService comprobanteService) {
        this.tiendaRepository = tiendaRepository;
        this.claves = claves;
        this.clienteService = clienteService;
        this.envioService = envioService;
        this.cuponService = cuponService;
        this.pedidoService = pedidoService;
        this.pagoService = pagoService;
        this.comprobanteService = comprobanteService;
    }

    /** Dirección de envío del invitado. Responde la dirección y su clave. */
    @PostMapping("/direcciones")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitadoCreadoResponse<DireccionResponse> crearDireccion(@PathVariable String subdominio,
                                                                    @Valid @RequestBody DireccionInvitadoRequest r) {
        Tienda t = tiendaQueAceptaInvitados(subdominio);
        DireccionResponse d = clienteService.crearDireccionInvitado(t, r);
        return new InvitadoCreadoResponse<>(d, claves.firmar("direccion", t.getIdTienda(), d.idDireccion()));
    }

    /** Formas de entrega y su costo para la dirección del invitado. */
    @GetMapping("/direcciones/{idDireccion}/envio")
    public CotizacionEnvioResponse cotizar(@PathVariable String subdominio, @PathVariable Long idDireccion,
                                           @RequestParam BigDecimal subtotal,
                                           @RequestHeader(value = CABECERA, required = false) String clave) {
        Tienda t = tiendaQueAceptaInvitados(subdominio);
        exigir(clave, "direccion", t, idDireccion, "La dirección no existe");
        return envioService.cotizar(t.getIdTienda(), null, idDireccion, subtotal);
    }

    /** Vista previa de un código de descuento para el invitado. */
    @PostMapping("/cupones/validar")
    public CuponAplicadoResponse validarCupon(@PathVariable String subdominio, @Valid @RequestBody ValidarCuponRequest r) {
        Tienda t = tiendaQueAceptaInvitados(subdominio);
        return cuponService.validarInvitado(t.getIdTienda(), r.codigo(), r.subtotal());
    }

    /** Crea el pedido del invitado. Responde el pedido y su clave (la del enlace del correo). */
    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitadoCreadoResponse<VentaResponse> crearPedido(@PathVariable String subdominio,
                                                             @Valid @RequestBody PedidoInvitadoRequest r) {
        Tienda t = tiendaQueAceptaInvitados(subdominio);
        exigir(r.claveDireccion(), "direccion", t, r.idDireccionEnvio(), "La dirección no existe");
        VentaResponse v = pedidoService.registrarComoInvitado(t, r);
        return new InvitadoCreadoResponse<>(v, claves.firmar("pedido", t.getIdTienda(), v.idPedido()));
    }

    /** Ver el pedido (desde el enlace del correo o al volver de la pasarela). */
    @GetMapping("/pedidos/{idPedido}")
    public VentaResponse verPedido(@PathVariable String subdominio, @PathVariable Long idPedido,
                                   @RequestHeader(value = CABECERA, required = false) String clave) {
        Tienda t = tienda(subdominio);
        exigir(clave, "pedido", t, idPedido, "El pedido no existe");
        return pedidoService.pedidoDeInvitado(t.getIdTienda(), idPedido);
    }

    @GetMapping("/pedidos/{idPedido}/comprobante")
    public ResponseEntity<byte[]> comprobante(@PathVariable String subdominio, @PathVariable Long idPedido,
                                              @RequestHeader(value = CABECERA, required = false) String clave) {
        Tienda t = tienda(subdominio);
        exigir(clave, "pedido", t, idPedido, "El pedido no existe");
        return PedidoController.comoPdf(comprobanteService.comprobanteDeInvitado(t.getIdTienda(), idPedido));
    }

    @PostMapping("/pedidos/{idPedido}/pago")
    public PagoIniciadoResponse pagar(@PathVariable String subdominio, @PathVariable Long idPedido,
                                      @RequestHeader(value = CABECERA, required = false) String clave,
                                      @RequestBody(required = false) IniciarPagoRequest r) {
        Tienda t = tienda(subdominio);
        exigir(clave, "pedido", t, idPedido, "El pedido no existe");
        return pagoService.iniciarInvitado(t.getIdTienda(), idPedido, r == null ? null : r.metodo());
    }

    @PostMapping("/pedidos/{idPedido}/pago/confirmar")
    public ResultadoPagoResponse confirmarPago(@PathVariable String subdominio, @PathVariable Long idPedido,
                                               @RequestHeader(value = CABECERA, required = false) String clave,
                                               @Valid @RequestBody ConfirmarPagoRequest r) {
        Tienda t = tienda(subdominio);
        exigir(clave, "pedido", t, idPedido, "El pedido no existe");
        return pagoService.confirmarInvitado(t.getIdTienda(), idPedido, r.paymentId());
    }

    // ------------------------------------------------------------------ apoyo

    /** La tienda activa (una suspendida responde 404, como en el catálogo). */
    private Tienda tienda(String subdominio) {
        return tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
    }

    private Tienda tiendaQueAceptaInvitados(String subdominio) {
        Tienda t = tienda(subdominio);
        if (!Boolean.TRUE.equals(t.getPermiteInvitados())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Esta tienda pide iniciar sesión para comprar");
        }
        return t;
    }

    /** Sin la clave correcta: 404 (no se revela si el pedido o la dirección existen). */
    private void exigir(String clave, String tipo, Tienda t, Long id, String mensaje) {
        if (id == null || !claves.valida(clave, tipo, t.getIdTienda(), id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, mensaje);
        }
    }
}
