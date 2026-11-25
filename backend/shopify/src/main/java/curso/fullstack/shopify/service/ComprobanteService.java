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
import curso.fullstack.shopify.repository.PropietarioRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Comprobante en PDF (issue #3, V25).
 *
 * En una tienda de Perú es la representación impresa de la BOLETA o FACTURA electrónica, con el
 * formato de la SUNAT: recuadro con el RUC del vendedor, el tipo de comprobante y su número
 * (B001-00000001, F001-00000001), datos del cliente (DNI o RUC y razón social), operación
 * gravada, IGV, total y el importe en letras. El número lo asigna la base al aprobarse el pago;
 * antes, el PDF dice "pendiente de pago". Lleva la nota de que es de demostración: emitirlo de
 * verdad exige enviarlo a la SUNAT por un OSE autorizado.
 *
 * Pedidos de otros países o anteriores a V25: el comprobante interno de siempre.
 *
 * Los precios se leen por id_precio (el que SE COBRÓ), no el precio actual del producto: un
 * comprobante que cambia cuando el dueño sube un precio no serviría de respaldo.
 */
@Slf4j
@Service
public class ComprobanteService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String AVISO = "Comprobante interno, no válido como comprobante SUNAT.";
    private static final String AVISO_SUNAT = "Documento de demostración: no fue enviado a la SUNAT ni a un OSE "
            + "(operador de servicios electrónicos). Tiene el formato y la numeración de un comprobante electrónico.";

    private final VerificadorTienda verificadorTienda;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final PrecioRepository precioRepository;
    private final TiendaRepository tiendaRepository;
    private final LoginRepository loginRepository;
    private final DireccionRepository direccionRepository;
    private final PagoRepository pagoRepository;
    private final PropietarioRepository propietarioRepository;
    private final JdbcTemplate jdbc;

    public ComprobanteService(VerificadorTienda verificadorTienda, PedidoRepository pedidoRepository,
                              DetallePedidoRepository detallePedidoRepository, PrecioRepository precioRepository,
                              TiendaRepository tiendaRepository, LoginRepository loginRepository,
                              DireccionRepository direccionRepository, PagoRepository pagoRepository,
                              PropietarioRepository propietarioRepository, JdbcTemplate jdbc) {
        this.propietarioRepository = propietarioRepository;
        this.jdbc = jdbc;
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
            boolean sunat = p.getTipoComprobante() != null;

            if (sunat) {
                // 1. Encabezado SUNAT: el vendedor a la izquierda; RUC, tipo y número en el recuadro.
                doc.add(encabezadoSunat(p, tienda, color));
                doc.add(espacio());
                doc.add(datos("Fecha de emisión", fecha(p.getComprobanteEmitidoEl() != null
                        ? p.getComprobanteEmitidoEl() : p.getFechaCreacion())));
                doc.add(datos("Pedido", "#" + p.getNumeroPedido()));
                if ("F".equals(p.getTipoComprobante().trim())) {
                    doc.add(datos("Razón social", p.getClienteRazonSocial()));
                    doc.add(datos("RUC", p.getClienteDocNumero()));
                } else {
                    doc.add(datos("Cliente", nombreCliente(p)));
                    if (p.getClienteDocNumero() != null) doc.add(datos(p.getClienteDocTipo(), p.getClienteDocNumero()));
                }
                doc.add(datos("Correo", p.getCorreoContacto()));
                doc.add(datos("Entrega", direccionDe(p)));
                doc.add(datos("Método de pago", metodoDePago(p.getIdPedido())));
                doc.add(datos("Moneda", "PEN".equals(trim(p.getMoneda())) ? "Soles (PEN)" : p.getMoneda()));
                doc.add(espacio());
            } else {
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
            }

            // 3. Los productos, con el precio que se cobró
            doc.add(tablaDeProductos(p, color));
            doc.add(espacio());

            // 4. Los totales. Los precios ya incluyen el impuesto (ck_pedidos_total), así que el
            //    valor sin impuesto se obtiene restándolo, no sumándolo.
            doc.add(sunat ? tablaDeTotalesSunat(p) : tablaDeTotales(p));
            doc.add(espacio());

            if (sunat) {
                // 5. El importe en letras y la nota (representación impresa, de demostración)
                doc.add(datos("SON", MontoEnLetras.de(p.getTotal(), trim(p.getMoneda()))));
                doc.add(espacio());
                String tipo = "F".equals(p.getTipoComprobante().trim()) ? "de la factura electrónica" : "de la boleta de venta electrónica";
                doc.add(menudo(p.numeroComprobante() == null
                        ? "Pedido pendiente de pago: el número " + tipo + " se asigna al confirmarse el pago."
                        : "Representación impresa " + tipo + " " + p.numeroComprobante() + "."));
                doc.add(menudo(AVISO_SUNAT));
            } else {
                // 5. La advertencia: esto no reemplaza a una boleta ni a una factura
                doc.add(menudo(AVISO));
            }
            doc.close();
        } catch (DocumentException e) {
            log.error("No se pudo generar el comprobante del pedido {}: {}", p.getIdPedido(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el comprobante");
        }
        String nombre = p.numeroComprobante() != null ? p.numeroComprobante() : "comprobante-" + p.getNumeroPedido();
        return new Comprobante(nombre + ".pdf", salida.toByteArray());
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
            // Precio cobrado por unidad: el de lista menos lo que rebajó la oferta automática.
            BigDecimal descuento = d.getDescuentoUnitario() == null ? BigDecimal.ZERO : d.getDescuentoUnitario();
            BigDecimal monto = precio == null ? BigDecimal.ZERO : precio.getMonto().subtract(descuento);
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
        if (p.getDescuentoCupon() != null && p.getDescuentoCupon().signum() > 0) {
            fila(t, "Código de descuento", "- " + dinero(p.getMoneda(), p.getDescuentoCupon()), false);
        }
        fila(t, "Envío", dinero(p.getMoneda(), p.getCostoEnvio()), false);
        fila(t, "Valor sin " + nombreImpuesto, dinero(p.getMoneda(), sinImpuesto), false);
        fila(t, nombreImpuesto + " incluido", dinero(p.getMoneda(), impuesto), false);
        fila(t, "Total", dinero(p.getMoneda(), p.getTotal()), true);
        return t;
    }

    // ------------------------------------------------------------------ formato SUNAT

    /** Vendedor a la izquierda; a la derecha el recuadro con RUC, tipo de comprobante y número. */
    private PdfPTable encabezadoSunat(Pedido p, Tienda tienda, Color color) {
        String[] emisor = emisorDe(tienda);   // razón social y RUC del dueño (V24)
        PdfPTable t = new PdfPTable(new float[] {58, 42});
        t.setWidthPercentage(100);

        PdfPCell izq = new PdfPCell();
        izq.setBorderWidth(0);
        izq.addElement(new Paragraph(emisor[0], FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, color)));
        if (!emisor[0].equalsIgnoreCase(tienda.getNombre())) {
            izq.addElement(new Paragraph(tienda.getNombre(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        }
        izq.addElement(menudo(correoYSitio(tienda)));
        t.addCell(izq);

        boolean factura = "F".equals(p.getTipoComprobante().trim());
        String numero = p.numeroComprobante();
        PdfPCell der = new PdfPCell();
        der.setBorderColor(color);
        der.setBorderWidth(1.5f);
        der.setPadding(8);
        for (Paragraph linea : List.of(
                centrado("R.U.C. " + (emisor[1] == null ? "-" : emisor[1]), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)),
                centrado(factura ? "FACTURA ELECTRÓNICA" : "BOLETA DE VENTA ELECTRÓNICA",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, color)),
                centrado(numero != null ? numero : "PENDIENTE DE PAGO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)))) {
            der.addElement(linea);
        }
        t.addCell(der);
        return t;
    }

    /** Op. gravada (el valor sin IGV), el IGV con su tasa y el importe total, como en la SUNAT. */
    private PdfPTable tablaDeTotalesSunat(Pedido p) {
        BigDecimal igv = p.getMontoImpuestos() == null ? BigDecimal.ZERO : p.getMontoImpuestos();
        BigDecimal gravada = p.getTotal().subtract(igv).setScale(2, RoundingMode.HALF_UP);
        String tasa = p.getIdImpuesto() == null ? "" : jdbc.query(
                "SELECT nombre, tasa FROM impuestos WHERE id_impuesto = ?",
                rs -> rs.next() ? rs.getString("nombre") + " (" + rs.getBigDecimal("tasa").stripTrailingZeros().toPlainString() + " %)" : "",
                p.getIdImpuesto());

        PdfPTable t = new PdfPTable(new float[] {70, 30});
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        fila(t, "Productos", dinero(p.getMoneda(), p.getSubtotal()), false);
        if (p.getDescuentoCupon() != null && p.getDescuentoCupon().signum() > 0) {
            fila(t, "Descuento", "- " + dinero(p.getMoneda(), p.getDescuentoCupon()), false);
        }
        fila(t, "Envío", dinero(p.getMoneda(), p.getCostoEnvio()), false);
        fila(t, "Op. gravada", dinero(p.getMoneda(), gravada), false);
        fila(t, tasa.isBlank() ? "IGV" : tasa, dinero(p.getMoneda(), igv), false);
        fila(t, "Importe total", dinero(p.getMoneda(), p.getTotal()), true);
        return t;
    }

    /** Razón social y RUC del dueño de la tienda; si aún no los registró, el nombre de la tienda. */
    private String[] emisorDe(Tienda tienda) {
        return propietarioRepository.findById(tienda.getIdPropietario())
                .map(o -> new String[] {o.getRazonSocial() == null ? tienda.getNombre() : o.getRazonSocial(), o.getRuc()})
                .orElse(new String[] {tienda.getNombre(), null});
    }

    private static Paragraph centrado(String texto, Font fuente) {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
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
