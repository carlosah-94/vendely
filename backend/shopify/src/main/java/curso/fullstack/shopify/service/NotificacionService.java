package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.dto.Comprobante;
import curso.fullstack.shopify.dto.NotificacionResponse;
import curso.fullstack.shopify.entity.DetallePedido;
import curso.fullstack.shopify.entity.Inventario;
import curso.fullstack.shopify.entity.Notificacion;
import curso.fullstack.shopify.entity.Pedido;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.entity.TipoEntrega;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.DetallePedidoRepository;
import curso.fullstack.shopify.repository.InventarioRepository;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.NotificacionRepository;
import curso.fullstack.shopify.repository.PedidoRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.repository.VarianteRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

/**
 * Los correos que manda Vendely y su registro (issue #3).
 *
 * Tres decisiones que explican cómo está escrito:
 *
 *  1. **En segundo plano** (@Async). Enviar un correo tarda cientos de milisegundos y depende de
 *     un servicio ajeno. El cliente que acaba de pagar no tiene que esperar eso, y menos que su
 *     compra falle porque el correo no salió.
 *  2. **Se registra siempre**, en la tabla notificaciones: enviado, simulado o fallido con su
 *     motivo. Un correo que no llegó y no deja rastro es un problema que nadie puede atender.
 *  3. **Modo simulado**: sin clave de correo (BREVO_API_KEY o RESEND_API_KEY) no se envía nada, se registra como simulado. Así el
 *     proyecto corre completo en cualquier máquina del equipo sin tener que crear una cuenta.
 */
@Slf4j
@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final TiendaRepository tiendaRepository;
    private final LoginRepository loginRepository;
    private final VarianteRepository varianteRepository;
    private final InventarioRepository inventarioRepository;
    private final VerificadorTienda verificadorTienda;
    private final ComprobanteService comprobanteService;
    private final CorreoClient correos;

    public NotificacionService(NotificacionRepository notificacionRepository, PedidoRepository pedidoRepository,
                               DetallePedidoRepository detallePedidoRepository, TiendaRepository tiendaRepository,
                               LoginRepository loginRepository, VarianteRepository varianteRepository,
                               InventarioRepository inventarioRepository, VerificadorTienda verificadorTienda,
                               ComprobanteService comprobanteService, CorreoClient correos) {
        this.notificacionRepository = notificacionRepository;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.tiendaRepository = tiendaRepository;
        this.loginRepository = loginRepository;
        this.varianteRepository = varianteRepository;
        this.inventarioRepository = inventarioRepository;
        this.verificadorTienda = verificadorTienda;
        this.comprobanteService = comprobanteService;
        this.correos = correos;
    }

    // ------------------------------------------------------------------ lo que ve el dueño

    /** Los correos de un pedido, para el detalle de la venta en el panel. */
    @Transactional(readOnly = true)
    public List<NotificacionResponse> listarDePedido(UsuarioAutenticado yo, Long idTienda, Long idPedido) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        // El pedido tiene que ser de esta tienda: si no, 404 (no se revela que existe).
        pedidoRepository.findByIdPedidoAndIdTienda(idPedido, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe"));
        return notificacionRepository.findByIdPedidoOrderByFechaCreacionDesc(idPedido).stream()
                .map(NotificacionResponse::desde)
                .toList();
    }

    // ------------------------------------------------------------------ los avisos

    /**
     * Pago aprobado: confirmación al cliente (con el comprobante adjunto) y aviso al dueño.
     * Lo dispara PagoAprobadoEvent, que se publica tanto cuando el cliente vuelve de la pasarela
     * como cuando llega el aviso del webhook (issue #8).
     */
    @Async
    public void avisarPagoAprobado(Long idTienda, Long idPedido) {
        Pedido p = pedidoRepository.findById(idPedido).orElse(null);
        Tienda t = tiendaRepository.findById(idTienda).orElse(null);
        if (p == null || t == null) {
            return;
        }

        if (!yaSeRegistro(idPedido, Notificacion.PAGO_APROBADO)) {
            String html = envoltura(t, "¡Gracias por tu compra!", """
                    <p>Tu pago del pedido <strong>#%d</strong> se confirmó. Ya lo estamos preparando.</p>
                    %s
                    <p>Adjuntamos tu comprobante de compra en PDF.</p>
                    """.formatted(p.getNumeroPedido(), resumenHtml(p)));
            Comprobante comprobante = comprobanteDe(idPedido);
            enviar(idTienda, idPedido, Notificacion.PAGO_APROBADO, p.getCorreoContacto(),
                    "Tu compra en " + t.getNombre() + " está confirmada · Pedido #" + p.getNumeroPedido(),
                    html, comprobante);
        }

        if (t.getCorreoContacto() != null && !yaSeRegistro(idPedido, Notificacion.NUEVO_PEDIDO)) {
            String html = envoltura(t, "Tienes una venta nueva", """
                    <p>El pedido <strong>#%d</strong> de %s ya está pagado.</p>
                    %s
                    <p>Entra a tu panel para marcarlo como enviado cuando lo despaches.</p>
                    """.formatted(p.getNumeroPedido(), escapar(nombreCliente(p)), resumenHtml(p)));
            enviar(idTienda, idPedido, Notificacion.NUEVO_PEDIDO, t.getCorreoContacto(),
                    "Nuevo pedido #" + p.getNumeroPedido() + " en " + t.getNombre(), html, null);
        }
    }

    /** Pedido enviado: al cliente, con la empresa y el número con el que puede rastrearlo. */
    @Async
    public void avisarPedidoEnviado(Long idTienda, Long idPedido) {
        Pedido p = pedidoRepository.findById(idPedido).orElse(null);
        Tienda t = tiendaRepository.findById(idTienda).orElse(null);
        if (p == null || t == null || yaSeRegistro(idPedido, Notificacion.PEDIDO_ENVIADO)) {
            return;
        }
        TipoEntrega tipo = TipoEntrega.de(p.getTipoEntrega());
        if (tipo == TipoEntrega.RECOJO) {
            // Recojo en tienda (V18): no "va en camino", ya está listo en el local.
            String html = envoltura(t, "Tu pedido está listo para recoger", """
                    <p>El pedido <strong>#%d</strong> ya está listo. Puedes pasar a recogerlo:</p>
                    <table style="border-collapse:collapse;margin:12px 0">
                      <tr><td style="padding:4px 12px 4px 0;color:#6b7280">Dirección</td><td><strong>%s</strong></td></tr>
                      <tr><td style="padding:4px 12px 4px 0;color:#6b7280">Horario</td><td><strong>%s</strong></td></tr>
                    </table>
                    <p>Menciona tu número de pedido al llegar.</p>
                    """.formatted(p.getNumeroPedido(), escapar(t.getRecojoDireccion()),
                    escapar(t.getRecojoHorario() == null ? "Consulta con la tienda" : t.getRecojoHorario())));
            enviar(idTienda, idPedido, Notificacion.PEDIDO_ENVIADO, p.getCorreoContacto(),
                    "Tu pedido #" + p.getNumeroPedido() + " está listo para recoger", html, null);
            return;
        }
        if (tipo == TipoEntrega.DELIVERY) {
            String quien = p.getEmpresaEnvio() == null ? ""
                    : "<p>Lo lleva: <strong>" + escapar(p.getEmpresaEnvio()) + "</strong></p>";
            String html = envoltura(t, "Tu pedido va en camino", """
                    <p>El pedido <strong>#%d</strong> salió hacia tu dirección con el delivery de %s.</p>
                    %s
                    <p>Ten tu celular a la mano por si te llaman al llegar.</p>
                    """.formatted(p.getNumeroPedido(), escapar(t.getNombre()), quien));
            enviar(idTienda, idPedido, Notificacion.PEDIDO_ENVIADO, p.getCorreoContacto(),
                    "Tu pedido #" + p.getNumeroPedido() + " va en camino", html, null);
            return;
        }
        String html = envoltura(t, "Tu pedido va en camino", """
                <p>El pedido <strong>#%d</strong> salió hacia tu dirección.</p>
                <table style="border-collapse:collapse;margin:12px 0">
                  <tr><td style="padding:4px 12px 4px 0;color:#6b7280">Empresa de envío</td><td><strong>%s</strong></td></tr>
                  <tr><td style="padding:4px 12px 4px 0;color:#6b7280">Número de seguimiento</td><td><strong>%s</strong></td></tr>
                </table>
                <p>Con ese número puedes seguir tu paquete en la página de la empresa de envío.</p>
                """.formatted(p.getNumeroPedido(), escapar(p.getEmpresaEnvio()), escapar(p.getNumeroSeguimiento())));
        enviar(idTienda, idPedido, Notificacion.PEDIDO_ENVIADO, p.getCorreoContacto(),
                "Tu pedido #" + p.getNumeroPedido() + " va en camino", html, null);
    }

    /**
     * Stock bajo: al dueño, cuando a una variante le queda menos que su stock mínimo.
     * Se avisa al cruzar el límite, con el stock que quedó después de la venta.
     */
    @Async
    public void avisarStockBajo(Long idTienda, Long idVariante, Integer quedan) {
        Inventario inv = inventarioRepository.findById(idVariante).orElse(null);
        Tienda t = tiendaRepository.findById(idTienda).orElse(null);
        if (inv == null || t == null || t.getCorreoContacto() == null) {
            return;
        }
        int minimo = inv.getStockMinimo() == null ? 0 : inv.getStockMinimo();
        int restante = quedan == null ? 0 : quedan;
        if (minimo <= 0 || restante > minimo) {
            return;   // todavía no es poco, o la variante no tiene mínimo configurado
        }
        String producto = varianteRepository.buscarInfo(idVariante)
                .map(i -> i.getNombreProducto() + " (" + i.getNombreVariante() + ")")
                .orElse("un producto");
        String html = envoltura(t, "Queda poco stock", """
                <p>De <strong>%s</strong> %s.</p>
                <p>Su mínimo configurado es %d. Entra a Inventario para reponerlo.</p>
                """.formatted(escapar(producto),
                        restante == 0 ? "ya no queda nada" : "quedan solo " + restante + " unidad(es)", minimo));
        enviar(idTienda, null, Notificacion.STOCK_BAJO, t.getCorreoContacto(),
                "Queda poco stock en " + t.getNombre(), html, null);
    }

    // ------------------------------------------------------------------ enviar y registrar

    /**
     * El único camino de salida: intenta enviar y guarda el resultado en notificaciones.
     *
     * Nunca lanza una excepción hacia arriba. Corre en segundo plano, después de que la venta
     * ya se guardó: si el proveedor de correo falla, lo que corresponde es registrarlo, no
     * tumbar nada (el pedido ya está pagado y el dinero ya se cobró).
     */
    private void enviar(Long idTienda, Long idPedido, short tipo, String destinatario,
                        String asunto, String html, Comprobante adjunto) {
        Notificacion n = new Notificacion();
        n.setIdTienda(idTienda);
        n.setIdPedido(idPedido);
        n.setDestinatario(recortar(destinatario, 150));
        n.setTipo(tipo);
        n.setAsunto(recortar(asunto, 200));

        if (!correos.estaConfigurado()) {
            n.setEstado(Notificacion.SIMULADO);
            n.setFechaEnvio(OffsetDateTime.now());
            guardar(n);
            log.info("Correo simulado (falta BREVO_API_KEY o RESEND_API_KEY): tipo={}, para={}", tipo, enmascarar(destinatario));
            return;
        }
        try {
            String id = correos.enviar(destinatario, asunto, html,
                    adjunto == null ? null : adjunto.nombreArchivo(),
                    adjunto == null ? null : adjunto.contenido());
            n.setEstado(Notificacion.ENVIADO);
            n.setFechaEnvio(OffsetDateTime.now());
            guardar(n);
            log.info("Correo enviado con {}: tipo={}, para={}, id={}", correos.servicio(), tipo, enmascarar(destinatario), id);
        } catch (RestClientException e) {
            n.setEstado(Notificacion.FALLIDO);
            n.setFechaEnvio(null);                       // lo exige ck_notificaciones_envio
            n.setError(recortar(e.getMessage(), 1000));
            guardar(n);
            log.error("No se pudo enviar el correo: tipo={}, para={}, motivo={}",
                    tipo, enmascarar(destinatario), e.getMessage());
        }
    }

    /**
     * Guarda el registro. El save del repositorio ya abre su propia transacción, que es lo que
     * se quiere aquí: este método corre en un hilo aparte, sin la transacción de la venta.
     * Si ni el registro se puede guardar, queda al menos en el log.
     */
    private void guardar(Notificacion n) {
        try {
            notificacionRepository.save(n);
        } catch (RuntimeException e) {
            log.error("No se pudo registrar la notificación (tipo={}): {}", n.getTipo(), e.getMessage());
        }
    }

    /**
     * Si ya se registró un correo de ese tipo para ese pedido (enviado o simulado). Es la
     * protección contra el aviso repetido: la pasarela reintenta sus avisos, y el cliente
     * puede volver a la pantalla del resultado cuando quiera.
     */
    private boolean yaSeRegistro(Long idPedido, short tipo) {
        return idPedido != null
                && notificacionRepository.existsByIdPedidoAndTipoAndEstadoNot(idPedido, tipo, Notificacion.FALLIDO);
    }

    /** El comprobante para adjuntar. Si falla, el correo sale igual, sin adjunto. */
    private Comprobante comprobanteDe(Long idPedido) {
        try {
            return comprobanteService.generar(idPedido);
        } catch (RuntimeException e) {
            log.warn("No se pudo generar el comprobante del pedido {} para el correo: {}", idPedido, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ el HTML

    /**
     * El marco de todos los correos: la cabecera con el nombre y el color de la tienda.
     * Los estilos van en línea porque los clientes de correo (Gmail, Outlook) ignoran
     * las hojas de estilo y muchas etiquetas de una página normal.
     */
    private String envoltura(Tienda t, String titulo, String contenido) {
        String color = colorValido(t.getColorPrincipal());
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:0 auto;color:#1f2937">
                  <div style="background:%s;color:#ffffff;padding:20px 24px">
                    <h1 style="margin:0;font-size:20px">%s</h1>
                  </div>
                  <div style="padding:24px;border:1px solid #e5e7eb;border-top:none;font-size:14px;line-height:1.5">
                    <h2 style="margin:0 0 12px;font-size:17px;color:%s">%s</h2>
                    %s
                  </div>
                  <p style="font-size:11px;color:#6b7280;text-align:center;margin-top:12px">
                    Enviado por Vendely · tienda %s
                  </p>
                </div>
                """.formatted(color, escapar(t.getNombre()), color, escapar(titulo), contenido,
                        escapar(t.getSubdominio()));
    }

    /** Los productos del pedido y sus montos, para el cuerpo del correo. */
    private String resumenHtml(Pedido p) {
        StringBuilder filas = new StringBuilder();
        for (DetallePedido d : detallePedidoRepository.findByIdPedido(p.getIdPedido())) {
            filas.append("""
                    <tr>
                      <td style="padding:6px 0;border-bottom:1px solid #f3f4f6">%s <span style="color:#6b7280">%s</span></td>
                      <td style="padding:6px 0;border-bottom:1px solid #f3f4f6;text-align:right">x%d</td>
                    </tr>
                    """.formatted(escapar(d.getNombreProducto()), escapar(d.getNombreVariante()), d.getCantidad()));
        }
        return """
                <table style="width:100%%;border-collapse:collapse;font-size:14px;margin:12px 0">
                  %s
                  <tr><td style="padding:6px 0;color:#6b7280">Productos</td>
                      <td style="padding:6px 0;text-align:right">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280">Envío</td>
                      <td style="padding:6px 0;text-align:right">%s</td></tr>
                  <tr><td style="padding:8px 0;font-weight:bold;border-top:1px solid #d1d5db">Total</td>
                      <td style="padding:8px 0;text-align:right;font-weight:bold;border-top:1px solid #d1d5db">%s</td></tr>
                </table>
                """.formatted(filas, dinero(p.getMoneda(), p.getSubtotal()),
                        dinero(p.getMoneda(), p.getCostoEnvio()), dinero(p.getMoneda(), p.getTotal()));
    }

    // ------------------------------------------------------------------ apoyo

    private String nombreCliente(Pedido p) {
        if (p.getIdLogin() == null) {
            return "un invitado";
        }
        return loginRepository.findById(p.getIdLogin())
                .map(l -> (l.getNombres() + " " + l.getPrimerApellido()).trim())
                .orElse("un cliente");
    }

    /**
     * Escapa el texto que va dentro del HTML. Los nombres de los productos y de las personas
     * los escribe el usuario: sin esto, un nombre con &lt; o &amp; rompería el correo.
     */
    private static String escapar(String texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(texto);
    }

    /** Solo se acepta un color #rrggbb; cualquier otra cosa se cambia por el gris de Vendely. */
    private static String colorValido(String color) {
        return color != null && color.matches("#[0-9A-Fa-f]{6}") ? color : "#374151";
    }

    private static String dinero(String moneda, BigDecimal monto) {
        BigDecimal m = monto == null ? BigDecimal.ZERO : monto;
        return (moneda == null ? "" : moneda.trim() + " ") + m.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** En el log no se escribe el correo completo de nadie: ana@gmail.com -> a***@gmail.com */
    private static String enmascarar(String correo) {
        if (correo == null || !correo.contains("@")) {
            return "(sin correo)";
        }
        int arroba = correo.indexOf('@');
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() > max ? texto.substring(0, max) : texto;
    }
}
