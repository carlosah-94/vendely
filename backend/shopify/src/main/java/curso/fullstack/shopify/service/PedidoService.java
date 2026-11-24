package curso.fullstack.shopify.service;

import curso.fullstack.shopify.repository.EnvioRepository;
import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.dto.EnvioPedidoRequest;
import curso.fullstack.shopify.dto.LineaVentaRequest;
import curso.fullstack.shopify.dto.LineaVentaResponse;
import curso.fullstack.shopify.dto.PedidoClienteRequest;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.VentaRequest;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.entity.*;
import curso.fullstack.shopify.evento.PedidoCreadoEvent;
import curso.fullstack.shopify.evento.PedidoEstadoCambiadoEvent;
import curso.fullstack.shopify.evento.StockCambiadoEvent;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.*;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PedidoService {

    private final VerificadorTienda verificadorTienda;
    private final LoginRepository loginRepository;
    private final DireccionRepository direccionRepository;
    private final VarianteRepository varianteRepository;
    private final PrecioRepository precioRepository;
    private final InventarioRepository inventarioRepository;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final TiendaRepository tiendaRepository;
    private final PagoRepository pagoRepository;
    private final OfertaRepository ofertaRepository;
    private final CuponService cuponService;
    private final CuponRepository cuponRepository;
    private final ApplicationEventPublisher eventos;
    private final EnvioRepository envioRepository;
    private final PlanService planService;

    @PersistenceContext
    private EntityManager em;

    public PedidoService(VerificadorTienda verificadorTienda, LoginRepository loginRepository,
                         DireccionRepository direccionRepository, VarianteRepository varianteRepository,
                         PrecioRepository precioRepository, InventarioRepository inventarioRepository,
                         PedidoRepository pedidoRepository, DetallePedidoRepository detallePedidoRepository,
                         TiendaRepository tiendaRepository, PagoRepository pagoRepository,
                         OfertaRepository ofertaRepository, CuponService cuponService,
                         CuponRepository cuponRepository, ApplicationEventPublisher eventos,
                         EnvioRepository envioRepository, PlanService planService) {
        this.eventos = eventos;
        this.envioRepository = envioRepository;
        this.planService = planService;
        this.cuponService = cuponService;
        this.cuponRepository = cuponRepository;
        this.ofertaRepository = ofertaRepository;
        this.tiendaRepository = tiendaRepository;
        this.pagoRepository = pagoRepository;
        this.verificadorTienda = verificadorTienda;
        this.loginRepository = loginRepository;
        this.direccionRepository = direccionRepository;
        this.varianteRepository = varianteRepository;
        this.precioRepository = precioRepository;
        this.inventarioRepository = inventarioRepository;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
    }

    /**
     * Registra una venta desde el panel: el dueño elige el cliente.
     * Todo o nada: si falta stock de un producto, se lanza ApiException
     * (RuntimeException) y @Transactional deshace también el stock ya descontado de los otros.
     */
    @Transactional
    public VentaResponse registrar(UsuarioAutenticado yo, Long idTienda, VentaRequest r) {
        // 1. La tienda es del dueño (404 / 403) y está activa
        Tienda tienda = verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        if (!tienda.estaActiva()) {
            throw new ApiException(HttpStatus.CONFLICT, "La tienda está suspendida");
        }

        // 2. El cliente es de ESTA tienda
        Login cliente = loginRepository.findById(r.idLogin())
                .filter(l -> l.getRol() == RolUsuario.CLIENTE && idTienda.equals(l.getIdTienda()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El cliente no existe en esta tienda"));

        return crearPedido(idTienda, cliente, r.idDireccionEnvio(), r.lineas(), null);
    }

    /**
     * Registra un pedido desde la tienda pública: el cliente es quien inició sesión
     * (idLogin y tienda salen del token, nunca del JSON).
     */
    @Transactional
    public VentaResponse registrarDesdeTienda(UsuarioAutenticado yo, PedidoClienteRequest r) {
        Tienda tienda = tiendaRepository.findById(yo.idTienda())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        if (!tienda.estaActiva()) {
            throw new ApiException(HttpStatus.CONFLICT, "La tienda está suspendida");
        }
        Login cliente = loginRepository.findById(yo.idLogin())
                .filter(l -> l.getRol() == RolUsuario.CLIENTE && yo.idTienda().equals(l.getIdTienda()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El cliente no existe en esta tienda"));

        return crearPedido(yo.idTienda(), cliente, r.idDireccionEnvio(), r.lineas(), r.codigoCupon());
    }

    /**
     * Lógica común de una venta (panel y tienda): valida la dirección, descuenta el stock,
     * toma el precio vigente y guarda pedido + líneas. Debe llamarse dentro de una transacción.
     */
    private VentaResponse crearPedido(Long idTienda, Login cliente, Long idDireccion, List<LineaVentaRequest> lineasPedidas,
                                      String codigoCupon) {
        // 1. La dirección es suya y está activa
        if (!direccionRepository.existsByIdDireccionAndIdLoginAndEstado(idDireccion, cliente.getIdLogin(), (short) 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La dirección de envío no es de este cliente");
        }
        // 1b. Una dirección en otro país solo si el plan incluye envíos internacionales. Las tarifas
        //     guardadas de un plan anterior no se usan (el trigger de la base las cobraría igual).
        envioRepository.cotizar(idTienda, cliente.getIdLogin(), idDireccion)
                .filter(c -> Boolean.TRUE.equals(c.getInternacional()))
                .filter(c -> !planService.permite(idTienda, FuncionPlan.ENVIO_INTERNACIONAL))
                .ifPresent(c -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Esta tienda no envía a " + c.getPais()
                            + " por ahora. Elige una dirección en el país de la tienda.");
                });

        // 2. Juntar líneas repetidas (la llave de detalle_pedido no permite la misma variante dos veces)
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (LineaVentaRequest l : lineasPedidas) {
            cantidades.merge(l.idVariante(), l.cantidad(), Integer::sum);
        }

        // 3. Por cada producto: validar, descontar stock, tomar el precio vigente y la mejor oferta vigente
        Ofertas ofertas = new Ofertas(idTienda);
        BigDecimal subtotal = BigDecimal.ZERO;
        List<DetallePedido> detalles = new ArrayList<>();
        List<LineaVentaResponse> lineas = new ArrayList<>();

        for (Map.Entry<Long, Integer> e : cantidades.entrySet()) {
            Long idVariante = e.getKey();
            int cantidad = e.getValue();

            VarianteRepository.Info info = varianteRepository.buscarInfo(idVariante)
                    .filter(i -> idTienda.equals(i.getIdTienda()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "El producto " + idVariante + " no existe en esta tienda"));
            if (info.getEstadoProducto() == null || info.getEstadoProducto() != 1) {
                throw new ApiException(HttpStatus.CONFLICT, info.getNombreProducto() + " no está a la venta");
            }

            Precio precio = precioRepository.vigente(idVariante)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                            info.getNombreProducto() + " no tiene un precio vigente"));

            if (inventarioRepository.descontar(idVariante, cantidad) == 0) {
                log.warn("Venta rechazada por stock insuficiente: tienda={}, variante={}, cantidad pedida={}",
                        idTienda, idVariante, cantidad);
                throw new ApiException(HttpStatus.CONFLICT, "Stock insuficiente de "
                        + info.getNombreProducto() + " (" + info.getNombreVariante() + ")");
            }
            Integer quedan = inventarioRepository.cantidadDe(idVariante);
            eventos.publishEvent(new StockCambiadoEvent(idTienda, idVariante, quedan == null ? 0 : quedan));

            // La oferta que más rebaja ese precio; el trigger T7 revisa que el descuento sea exactamente ese.
            Optional<OfertaRepository.Vigente> oferta = ofertas.mejor(info.getIdProducto(), precio.getMonto());
            BigDecimal descuento = oferta.map(o -> ofertas.descuento(o, precio.getMonto())).orElse(BigDecimal.ZERO);
            BigDecimal importe = precio.getMonto().subtract(descuento).multiply(BigDecimal.valueOf(cantidad));
            subtotal = subtotal.add(importe);

            DetallePedido d = new DetallePedido();
            d.setIdVariante(idVariante);
            d.setIdPrecio(precio.getIdPrecio());
            d.setNombreProducto(info.getNombreProducto());
            d.setNombreVariante(info.getNombreVariante());
            d.setCantidad(cantidad);
            oferta.ifPresent(o -> {
                d.setIdOferta(o.getIdOferta());
                d.setDescuentoUnitario(descuento);
            });
            detalles.add(d);

            lineas.add(new LineaVentaResponse(idVariante, info.getNombreProducto(), info.getNombreVariante(),
                    cantidad, precio.getMonto(), importe, descuento,
                    oferta.map(OfertaRepository.Vigente::getNombre).orElse(null)));
        }

        // 4. Código de descuento (opcional): se revisa con el subtotal real y rebaja el pedido completo.
        //    Si ya no sirve (venció, se agotó...), 400 y @Transactional devuelve el stock descontado.
        CuponService.Aplicado cupon = codigoCupon == null || codigoCupon.isBlank() ? null
                : cuponService.aplicar(idTienda, cliente.getIdLogin(), codigoCupon, subtotal);

        // 5. Guardar el pedido: Java manda el subtotal; el trigger pone número, envío, total e impuesto (IGV, IVA)
        Pedido p = new Pedido();
        p.setIdTienda(idTienda);
        p.setIdLogin(cliente.getIdLogin());
        p.setIdDireccionEnvio(idDireccion);
        p.setCorreoContacto(cliente.getCorreo());
        p.setSubtotal(subtotal);
        if (cupon != null) {
            p.setIdCupon(cupon.cupon().getIdCupon());
            p.setDescuentoCupon(cupon.descuento());
        }
        pedidoRepository.saveAndFlush(p);
        em.refresh(p);                       // lee lo que completó el trigger

        // 6. Guardar las líneas
        for (DetallePedido d : detalles) {
            d.setIdPedido(p.getIdPedido());
        }
        detallePedidoRepository.saveAll(detalles);
        eventos.publishEvent(new PedidoCreadoEvent(idTienda, p.getIdPedido()));

        log.info("Venta registrada: tienda={}, pedido #{} (id={}), {} producto(s), total {} {}",
                idTienda, p.getNumeroPedido(), p.getIdPedido(), detalles.size(), p.getTotal(), p.getMoneda());
        return VentaResponse.desde(p, lineas, "SIN_PAGO", cupon == null ? null : cupon.cupon().getCodigo());
    }

    // =====================================================================
    // Issue #8 · Consultar y eliminar ventas (panel del dueño)
    // =====================================================================

    /**
     * Lista de ventas de una tienda (RF3.2): número, fecha, cliente y total.
     * readOnly = true porque solo lee: Hibernate no revisa cambios y la consulta es más liviana.
     */
    @Transactional(readOnly = true)
    public List<PedidoResumenResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        // Primero se comprueba que la tienda sea del dueño: 404 si no existe, 403 si es de otro.
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        return resumenes(pedidoRepository.findByIdTiendaOrderByFechaCreacionDesc(idTienda));
    }

    /** Detalle de una venta con sus productos, cantidades y precios (RF3.2). */
    @Transactional(readOnly = true)
    public VentaResponse detalle(UsuarioAutenticado yo, Long idTienda, Long idPedido) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        // buscarEnTienda: si el pedido es de otra tienda responde 404 (no revela que existe).
        return conLineas(buscarEnTienda(idPedido, idTienda));
    }

    /**
     * Elimina una venta y devuelve su stock (RF3.4).
     * Todo va en UNA transacción: si algo falla a mitad (por ejemplo, al borrar), el stock
     * devuelto también se deshace y la base queda como estaba. Nunca "medio eliminada".
     */
    @Transactional
    public void eliminar(UsuarioAutenticado yo, Long idTienda, Long idPedido) {
        verificadorTienda.soloElDueno(yo, idTienda);
        Pedido p = buscarEnTienda(idPedido, idTienda);

        // Con un pago aprobado el dinero ya se cobró: borrar la venta descuadraría la caja.
        // Se responde 409 (conflicto con el estado actual) con un mensaje claro, sin tocar nada.
        if (pedidoRepository.tienePagoAprobado(idPedido)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El pedido #" + p.getNumeroPedido() + " ya tiene un pago aprobado: no se puede eliminar");
        }

        // 1. El stock vuelve ANTES de borrar: después, las líneas ya no existirían (cascade).
        inventarioRepository.devolverStock(idPedido);
        // 2. La FK de pagos no tiene cascade: los pagos pendientes o rechazados se quitan antes.
        pedidoRepository.borrarPagosNoAprobados(idPedido);
        // 3. Se borra el pedido; sus líneas (detalle_pedido) las borra la base con ON DELETE CASCADE.
        pedidoRepository.delete(p);
        // flush envía el DELETE ya, dentro de la transacción: si la base lo rechaza, el error sale
        // aquí y @Transactional deshace también el paso 1 (el stock no queda devuelto de más).
        pedidoRepository.flush();
        log.info("Venta eliminada y stock devuelto: tienda={}, pedido #{} (id={})",
                idTienda, p.getNumeroPedido(), idPedido);
    }

    // =====================================================================
    // Issue #3 · Seguimiento del envio
    // =====================================================================

    /**
     * Marca el pedido como ENVIADO, con su empresa y su número de seguimiento.
     *
     * Solo un pedido ya pagado puede enviarse: despachar mercadería sin haber cobrado es una
     * pérdida, y es la misma razón por la que la base no deja borrar un pedido pagado.
     */
    @Transactional
    public VentaResponse enviar(UsuarioAutenticado yo, Long idTienda, Long idPedido, EnvioPedidoRequest r) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        Pedido p = buscarEnTienda(idPedido, idTienda);
        exigirEstado(p, (short) 0, "Solo un pedido sin enviar puede marcarse como enviado");
        if (!pedidoRepository.tienePagoAprobado(idPedido)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El pedido #" + p.getNumeroPedido() + " todavía no tiene un pago aprobado");
        }
        p.setEmpresaEnvio(r.empresaEnvio().trim());
        p.setNumeroSeguimiento(r.numeroSeguimiento().trim());
        p.setFechaEnvio(OffsetDateTime.now());
        return cambiarEstado(p, (short) 1);
    }

    /** De ENVIADO a ENTREGADO: el cliente ya recibió su paquete. */
    @Transactional
    public VentaResponse entregar(UsuarioAutenticado yo, Long idTienda, Long idPedido) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        Pedido p = buscarEnTienda(idPedido, idTienda);
        exigirEstado(p, (short) 1, "Solo un pedido enviado puede marcarse como entregado");
        p.setFechaEntrega(OffsetDateTime.now());
        return cambiarEstado(p, (short) 2);
    }

    /**
     * Cancela un pedido que todavía no salió: el stock vuelve al inventario y, si ya se había
     * cobrado, el pago queda como REEMBOLSADO (el dinero lo devuelve el dueño en la pasarela;
     * Vendely solo lo registra). Un pedido ya enviado no se cancela: la mercadería está en camino.
     *
     * Todo en UNA transacción: nunca queda un pedido cancelado con el stock sin devolver.
     */
    @Transactional
    public VentaResponse cancelar(UsuarioAutenticado yo, Long idTienda, Long idPedido) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS);
        Pedido p = buscarEnTienda(idPedido, idTienda);
        exigirEstado(p, (short) 0, "Un pedido que ya se envió no se puede cancelar");

        inventarioRepository.devolverStock(idPedido);
        for (DetallePedido d : detallePedidoRepository.findByIdPedido(idPedido)) {
            Integer quedan = inventarioRepository.cantidadDe(d.getIdVariante());
            eventos.publishEvent(new StockCambiadoEvent(idTienda, d.getIdVariante(), quedan == null ? 0 : quedan));
        }
        pagoRepository.findFirstByIdPedidoAndEstadoOrderByIdPagoDesc(idPedido, Pago.APROBADO)
                .ifPresent(pago -> {
                    pago.setEstado(Pago.REEMBOLSADO);
                    pagoRepository.save(pago);
                    log.info("Pago marcado como reembolsado: pedido #{} (id={})", p.getNumeroPedido(), idPedido);
                });

        p.setFechaCancelacion(OffsetDateTime.now());
        return cambiarEstado(p, (short) 3);
    }

    /** 409 si el pedido no está en el estado que exige la acción, diciendo en cuál está. */
    private void exigirEstado(Pedido p, short esperado, String mensaje) {
        if (!Short.valueOf(esperado).equals(p.getEstado())) {
            throw new ApiException(HttpStatus.CONFLICT, mensaje + ". El pedido #" + p.getNumeroPedido()
                    + " está " + PedidoResumenResponse.nombreEstado(p.getEstado()));
        }
    }

    /**
     * Guarda el estado nuevo y publica PedidoEstadoCambiadoEvent. El evento se publica DENTRO
     * de la transacción pero los oyentes son @TransactionalEventListener: si algo falla y se
     * deshace el cambio, no se envía ningún correo de un envío que no ocurrió.
     */
    private VentaResponse cambiarEstado(Pedido p, short nuevo) {
        Short anterior = p.getEstado();
        p.setEstado(nuevo);
        pedidoRepository.saveAndFlush(p);
        eventos.publishEvent(new PedidoEstadoCambiadoEvent(p.getIdTienda(), p.getIdPedido(), anterior, nuevo));
        log.info("Pedido #{} (id={}): {} -> {}", p.getNumeroPedido(), p.getIdPedido(),
                PedidoResumenResponse.nombreEstado(anterior), PedidoResumenResponse.nombreEstado(nuevo));
        return conLineas(p);
    }

    // =====================================================================
    // Issue #8 · El cliente solo ve SUS pedidos
    // =====================================================================

    /**
     * "Mis pedidos". El idLogin y la tienda salen del TOKEN (yo), nunca de la URL ni del JSON:
     * así un cliente no puede pedir los pedidos de otro cambiando un número.
     */
    @Transactional(readOnly = true)
    public List<PedidoResumenResponse> misPedidos(UsuarioAutenticado yo) {
        return resumenes(pedidoRepository.findByIdTiendaAndIdLoginOrderByFechaCreacionDesc(yo.idTienda(), yo.idLogin()));
    }

    /** Detalle de uno de sus pedidos; si es de otro cliente, 404 (no se revela que existe). */
    @Transactional(readOnly = true)
    public VentaResponse miPedido(UsuarioAutenticado yo, Long idPedido) {
        Pedido p = buscarEnTienda(idPedido, yo.idTienda());
        if (!yo.idLogin().equals(p.getIdLogin())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe");
        }
        return conLineas(p);
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    /** Busca el pedido DENTRO de la tienda: si es de otra, 404. Es el candado multi-tienda. */
    private Pedido buscarEnTienda(Long idPedido, Long idTienda) {
        return pedidoRepository.findByIdPedidoAndIdTienda(idPedido, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El pedido no existe"));
    }

    /**
     * Arma la lista con el nombre del cliente. Los clientes se traen todos en UNA consulta
     * (findAllById) en vez de uno por pedido: con 100 pedidos serían 100 consultas (problema N+1).
     */
    private List<PedidoResumenResponse> resumenes(List<Pedido> pedidos) {
        Set<Long> ids = pedidos.stream().map(Pedido::getIdLogin).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Login> clientes = loginRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Login::getIdLogin, Function.identity()));
        Map<Long, Short> pagos = estadosDePago(pedidos.stream().map(Pedido::getIdPedido).toList());
        return pedidos.stream().map(p -> {
            Login c = clientes.get(p.getIdLogin());
            // id_login NULL = compra como invitado: se muestra su correo de contacto.
            String nombre = (c == null) ? "Invitado (" + p.getCorreoContacto() + ")"
                                        : c.getNombres() + " " + c.getPrimerApellido();
            return PedidoResumenResponse.desde(p, nombre,
                    PedidoResumenResponse.nombreEstadoPago(pagos.get(p.getIdPedido())));
        }).toList();
    }

    /**
     * Pedido + sus líneas con el precio que SE COBRÓ: se lee por id_precio (el guardado en la
     * venta), no el precio actual del producto, que pudo cambiar después.
     */
    private VentaResponse conLineas(Pedido p) {
        List<DetallePedido> detalles = detallePedidoRepository.findByIdPedido(p.getIdPedido());
        Map<Long, Precio> precios = precioRepository.findAllById(
                        detalles.stream().map(DetallePedido::getIdPrecio).toList()).stream()
                .collect(Collectors.toMap(Precio::getIdPrecio, Function.identity()));
        Map<Long, String> nombresOferta = ofertaRepository.findAllById(detalles.stream()
                        .map(DetallePedido::getIdOferta).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Oferta::getIdOferta, Oferta::getNombre));
        List<LineaVentaResponse> lineas = detalles.stream().map(d -> {
            BigDecimal precio = precios.get(d.getIdPrecio()).getMonto();
            BigDecimal descuento = d.getDescuentoUnitario() == null ? BigDecimal.ZERO : d.getDescuentoUnitario();
            return new LineaVentaResponse(d.getIdVariante(), d.getNombreProducto(), d.getNombreVariante(),
                    d.getCantidad(), precio, precio.subtract(descuento).multiply(BigDecimal.valueOf(d.getCantidad())),
                    descuento, nombresOferta.get(d.getIdOferta()));
        }).toList();
        Short estadoPago = estadosDePago(List.of(p.getIdPedido())).get(p.getIdPedido());
        String codigo = p.getIdCupon() == null ? null
                : cuponRepository.findById(p.getIdCupon()).map(Cupon::getCodigo).orElse(null);
        return VentaResponse.desde(p, lineas, PedidoResumenResponse.nombreEstadoPago(estadoPago), codigo);
    }

    /** Estado de pago de varios pedidos en UNA consulta (los que no tienen pagos no aparecen). */
    private Map<Long, Short> estadosDePago(List<Long> idsPedidos) {
        if (idsPedidos.isEmpty()) {
            return Map.of();
        }
        return pagoRepository.estadosDe(idsPedidos).stream()
                .collect(Collectors.toMap(PagoRepository.EstadoPedido::getIdPedido, PagoRepository.EstadoPedido::getEstado));
    }

    /**
     * Ofertas vigentes de la tienda durante la creación de UN pedido: los decimales de la moneda se
     * leen una vez y las ofertas de cada producto también (now() es el mismo en toda la transacción).
     */
    private final class Ofertas {
        private final Long idTienda;
        private final Map<Long, List<OfertaRepository.Vigente>> porProducto = new HashMap<>();
        private Integer decimales;

        Ofertas(Long idTienda) {
            this.idTienda = idTienda;
        }

        Optional<OfertaRepository.Vigente> mejor(Long idProducto, BigDecimal precio) {
            List<OfertaRepository.Vigente> vigentes = porProducto.computeIfAbsent(idProducto,
                    id -> ofertaRepository.vigentesDeProductos(idTienda, List.of(id)));
            return vigentes.isEmpty() ? Optional.empty() : CalculoOferta.mejor(vigentes, precio, decimales());
        }

        BigDecimal descuento(OfertaRepository.Vigente o, BigDecimal precio) {
            return CalculoOferta.descuento(o, precio, decimales());
        }

        private int decimales() {
            if (decimales == null) {
                Integer d = ofertaRepository.decimalesDeTienda(idTienda);
                decimales = d == null ? 2 : d;
            }
            return decimales;
        }
    }
}
