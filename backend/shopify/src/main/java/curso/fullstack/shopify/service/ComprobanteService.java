package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.PermisoTienda;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import curso.fullstack.shopify.dto.Comprobante;
import curso.fullstack.shopify.entity.DetallePedido;
import curso.fullstack.shopify.entity.Direccion;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.Pago;
import curso.fullstack.shopify.entity.Pedido;
import curso.fullstack.shopify.entity.Precio;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.DetallePedidoRepository;
import curso.fullstack.shopify.repository.DireccionRepository;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.PagoRepository;
import curso.fullstack.shopify.repository.PedidoRepository;
import curso.fullstack.shopify.repository.PrecioRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprobante de compra en PDF (issue #3).
 *
 * No es un documento tributario: es el respaldo interno de la compra, para que el cliente y el
 * dueño tengan por escrito qué se compró y por cuánto. Lleva esa advertencia impresa, porque
 * emitir una boleta o factura de verdad exige estar autorizado ante la SUNAT.
 *
 * Los precios se leen por id_precio (el que SE COBRÓ), no el precio actual del producto: un
 * comprobante que cambia cuando el dueño sube un precio no serviría de respaldo.
 */
@Slf4j
@Service
public class ComprobanteService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String AVISO = "Comprobante interno, no válido como comprobante SUNAT.";

    private final VerificadorTienda verificadorTienda;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final PrecioRepository precioRepository;
    private final TiendaRepository tiendaRepository;
    private final LoginRepository loginRepository;
    private final DireccionRepository direccionRepository;
    private final PagoRepository pagoRepository;

    public ComprobanteService(VerificadorTienda verificadorTienda, PedidoRepository pedidoRepository,
                              DetallePedidoRepository detallePedidoRepository, PrecioRepository precioRepository,
                              TiendaRepository tiendaRepository, LoginRepository loginRepository,
                              DireccionRepository direccionRepository, PagoRepository pagoRepository) {
        this.verificadorTienda = verificadorTienda;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.precioRepository = precioRepository;
        this.tiendaRepository = tiendaRepository;
        this.loginRepository = loginRepository;
        this.direccionRepository = direccionRepository;
        this.pagoRepository = pagoRepository;
    }

    // ------------------------------------------------------------------ las dos puertas de entrada

    /**
     * El comprobante de uno de MIS pedidos. La tienda y el cliente salen del token, así que un
     * cliente no puede descargar el comprobante de otro cambiando el número de la URL: 404.
     */
    @Transactional(readOnly = true)
    public Comprobante miComprobante(UsuarioAutenticado yo, Long idPedido) {
        Pedido p = pedidoRepository.findByIdPedidoAndIdTienda(idPedido, yo.idTienda())
                .filter(x -> yo.idLogin().equals(x.getIdLogin()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe"));
        return generar(p);
    }

    /** El comprobante de una venta, desde el panel: la tienda tiene que ser del dueño. */
    @Transactional(readOnly = true)
    public Comprobante comprobanteDeLaTienda(UsuarioAutenticado yo, Long idTienda, Long idPedido) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        Pedido p = pedidoRepository.findByIdPedidoAndIdTienda(idPedido, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe"));
        return generar(p);
    }

    /** Para el correo de confirmación: ahí el pedido ya se verificó al aprobarse el pago. */
    @Transactional(readOnly = true)
    public Comprobante generar(Long idPedido) {
        return generar(pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe")));
    }

    // ------------------------------------------------------------------ el PDF

    private Comprobante generar(Pedido p) {
        Tienda tienda = tiendaRepository.findById(p.getIdTienda())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        Color color = colorDe(tienda.getColorPrincipal());

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 42, 48);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();

            // 1. Encabezado: la tienda, con su color
            doc.add(titulo(tienda.getNombre(), color));
            doc.add(menudo(correoYSitio(tienda)));
            doc.add(espacio());

            // 2. El pedido
            doc.add(subtitulo("Comprobante de compra", color));
            doc.add(datos("Pedido", "#" + p.getNumeroPedido()));
            doc.add(datos("Fecha", fecha(p.getFechaCreacion())));
            doc.add(datos("Cliente", nombreCliente(p)));
            doc.add(datos("Correo", p.getCorreoContacto()));
            doc.add(datos("Envío a", direccionDe(p)));
            doc.add(datos("Método de pago", metodoDePago(p.getIdPedido())));
            doc.add(espacio());

            // 3. Los productos, con el precio que se cobró
            doc.add(tablaDeProductos(p, color));
            doc.add(espacio());

            // 4. Los totales. Los precios ya incluyen el impuesto (ck_pedidos_total), así que el
            //    valor sin impuesto se obtiene restándolo, no sumándolo.
            doc.add(tablaDeTotales(p));
            doc.add(espacio());

            // 5. La advertencia: esto no reemplaza a una boleta ni a una factura
            doc.add(menudo(AVISO));
            doc.close();
        } catch (DocumentException e) {
            log.error("No se pudo generar el comprobante del pedido {}: {}", p.getIdPedido(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el comprobante");
        }
        return new Comprobante("comprobante-" + p.getNumeroPedido() + ".pdf", salida.toByteArray());
    }

    private PdfPTable tablaDeProductos(Pedido p, Color color) {
        List<DetallePedido> detalles = detallePedidoRepository.findByIdPedido(p.getIdPedido());
        Map<Long, Precio> precios = precioRepository
                .findAllById(detalles.stream().map(DetallePedido::getIdPrecio).toList()).stream()
                .collect(Collectors.toMap(Precio::getIdPrecio, Function.identity()));

        PdfPTable t = new PdfPTable(new float[] {44, 16, 12, 14, 14});
        t.setWidthPercentage(100);
        for (String cabecera : List.of("Producto", "Variante", "Cant.", "Precio", "Importe")) {
            PdfPCell c = new PdfPCell(new Phrase(cabecera, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            c.setBackgroundColor(color);
            c.setPadding(5);
            c.setBorderWidth(0);
            t.addCell(c);
        }
        for (DetallePedido d : detalles) {
            Precio precio = precios.get(d.getIdPrecio());
            BigDecimal monto = precio == null ? BigDecimal.ZERO : precio.getMonto();
            celda(t, d.getNombreProducto(), Element.ALIGN_LEFT);
            celda(t, d.getNombreVariante(), Element.ALIGN_LEFT);
            celda(t, String.valueOf(d.getCantidad()), Element.ALIGN_CENTER);
            celda(t, dinero(p.getMoneda(), monto), Element.ALIGN_RIGHT);
            celda(t, dinero(p.getMoneda(), monto.multiply(BigDecimal.valueOf(d.getCantidad()))), Element.ALIGN_RIGHT);
        }
        return t;
    }

    private PdfPTable tablaDeTotales(Pedido p) {
        BigDecimal impuesto = p.getMontoImpuestos() == null ? BigDecimal.ZERO : p.getMontoImpuestos();
        BigDecimal sinImpuesto = p.getTotal().subtract(impuesto).setScale(2, RoundingMode.HALF_UP);
        String nombreImpuesto = p.getIdImpuesto() == null
                ? "Impuesto (exportación: no aplica)"
                : pedidoRepository.nombreDelImpuesto(p.getIdImpuesto());

        PdfPTable t = new PdfPTable(new float[] {70, 30});
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        fila(t, "Productos", dinero(p.getMoneda(), p.getSubtotal()), false);
        fila(t, "Envío", dinero(p.getMoneda(), p.getCostoEnvio()), false);
        fila(t, "Valor sin " + nombreImpuesto, dinero(p.getMoneda(), sinImpuesto), false);
        fila(t, nombreImpuesto + " incluido", dinero(p.getMoneda(), impuesto), false);
        fila(t, "Total", dinero(p.getMoneda(), p.getTotal()), true);
        return t;
    }

    // ------------------------------------------------------------------ apoyo

    /** "PEN 110.00": la moneda del pedido delante, sin símbolos que dependan del idioma. */
    private static String dinero(String moneda, BigDecimal monto) {
        BigDecimal m = monto == null ? BigDecimal.ZERO : monto;
        return (moneda == null ? "" : moneda.trim() + " ") + m.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nombreCliente(Pedido p) {
        if (p.getIdLogin() == null) {
            return "Invitado";
        }
        return loginRepository.findById(p.getIdLogin())
                .map(l -> (l.getNombres() + " " + l.getPrimerApellido()).trim())
                .orElse("Cliente");
    }

    private String direccionDe(Pedido p) {
        Direccion d = direccionRepository.findById(p.getIdDireccionEnvio()).orElse(null);
        if (d == null) {
            return "-";
        }
        String texto = d.getDestinatario() + " · " + d.getDireccion();
        return d.getReferencia() == null || d.getReferencia().isBlank() ? texto : texto + " (" + d.getReferencia() + ")";
    }

    /** Con qué se pagó. Un pedido sin pago aprobado todavía no tiene método. */
    private String metodoDePago(Long idPedido) {
        return pagoRepository.findFirstByIdPedidoAndEstadoOrderByIdPagoDesc(idPedido, Pago.APROBADO)
                .map(pago -> switch (pago.getProveedor()) {
                    case Pago.PAYPAL -> "PayPal";
                    case Pago.MERCADO_PAGO -> "Mercado Pago";
                    default -> "Otro";
                })
                .orElse("Pendiente de pago");
    }

    private static String correoYSitio(Tienda t) {
        String correo = t.getCorreoContacto() == null ? "" : t.getCorreoContacto();
        return correo.isBlank() ? t.getSubdominio() : correo + " · " + t.getSubdominio();
    }

    private static String fecha(OffsetDateTime f) {
        return f == null ? "-" : f.atZoneSameInstant(ZoneId.of("America/Lima")).format(FECHA);
    }

    /** El color de la tienda (#7C3AED) para el PDF; si no es un color válido, gris oscuro. */
    private static Color colorDe(String hex) {
        try {
            return Color.decode(hex == null || hex.isBlank() ? "#374151" : hex.trim());
        } catch (NumberFormatException e) {
            return Color.decode("#374151");
        }
    }

    private static Paragraph titulo(String texto, Color color) {
        return new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, color));
    }

    private static Paragraph subtitulo(String texto, Color color) {
        Paragraph p = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, color));
        p.setSpacingAfter(6);
        return p;
    }

    private static Paragraph menudo(String texto) {
        return new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY));
    }

    private static Paragraph datos(String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.add(new Phrase(etiqueta + ": ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        p.add(new Phrase(valor == null ? "-" : valor, FontFactory.getFont(FontFactory.HELVETICA, 10)));
        return p;
    }

    private static Paragraph espacio() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(4);
        return p;
    }

    private static void celda(PdfPTable t, String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "-" : texto, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c.setPadding(5);
        c.setHorizontalAlignment(alineacion);
        c.setBorderWidth(0);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(c);
    }

    private static void fila(PdfPTable t, String etiqueta, String valor, boolean negrita) {
        Font f = FontFactory.getFont(negrita ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 10);
        PdfPCell izq = new PdfPCell(new Phrase(etiqueta, f));
        PdfPCell der = new PdfPCell(new Phrase(valor, f));
        for (PdfPCell c : List.of(izq, der)) {
            c.setPadding(4);
            c.setBorderWidth(0);
            if (negrita) {
                c.setBorderWidthTop(0.5f);
                c.setBorderColor(Color.GRAY);
            }
        }
        der.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(izq);
        t.addCell(der);
    }
}
