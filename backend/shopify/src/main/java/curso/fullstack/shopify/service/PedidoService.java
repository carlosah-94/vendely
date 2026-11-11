package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.LineaVentaRequest;
import curso.fullstack.shopify.dto.LineaVentaResponse;
import curso.fullstack.shopify.dto.PedidoClienteRequest;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.dto.VentaRequest;
import curso.fullstack.shopify.dto.VentaResponse;
import curso.fullstack.shopify.entity.*;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.*;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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

    @PersistenceContext
    private EntityManager em;

    public PedidoService(VerificadorTienda verificadorTienda, LoginRepository loginRepository,
                         DireccionRepository direccionRepository, VarianteRepository varianteRepository,
                         PrecioRepository precioRepository, InventarioRepository inventarioRepository,
                         PedidoRepository pedidoRepository, DetallePedidoRepository detallePedidoRepository,
                         TiendaRepository tiendaRepository, PagoRepository pagoRepository) {
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
        Tienda tienda = verificadorTienda.delDueno(yo, idTienda);
        if (!tienda.estaActiva()) {
            throw new ApiException(HttpStatus.CONFLICT, "La tienda está suspendida");
        }

        // 2. El cliente es de ESTA tienda
        Login cliente = loginRepository.findById(r.idLogin())
                .filter(l -> l.getRol() == RolUsuario.CLIENTE && idTienda.equals(l.getIdTienda()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El cliente no existe en esta tienda"));

        return crearPedido(idTienda, cliente, r.idDireccionEnvio(), r.lineas());
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

        return crearPedido(yo.idTienda(), cliente, r.idDireccionEnvio(), r.lineas());
    }

    /**
     * Lógica común de una venta (panel y tienda): valida la dirección, descuenta el stock,
     * toma el precio vigente y guarda pedido + líneas. Debe llamarse dentro de una transacción.
     */
    private VentaResponse crearPedido(Long idTienda, Login cliente, Long idDireccion, List<LineaVentaRequest> lineasPedidas) {
        // 1. La dirección es suya y está activa
        if (!direccionRepository.existsByIdDireccionAndIdLoginAndEstado(idDireccion, cliente.getIdLogin(), (short) 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La dirección de envío no es de este cliente");
        }

        // 2. Juntar líneas repetidas (la llave de detalle_pedido no permite la misma variante dos veces)
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (LineaVentaRequest l : lineasPedidas) {
            cantidades.merge(l.idVariante(), l.cantidad(), Integer::sum);
        }

        // 3. Por cada producto: validar, descontar stock y tomar el precio vigente
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

            BigDecimal importe = precio.getMonto().multiply(BigDecimal.valueOf(cantidad));
            subtotal = subtotal.add(importe);

            DetallePedido d = new DetallePedido();
            d.setIdVariante(idVariante);
            d.setIdPrecio(precio.getIdPrecio());
            d.setNombreProducto(info.getNombreProducto());
            d.setNombreVariante(info.getNombreVariante());
            d.setCantidad(cantidad);
            detalles.add(d);

            lineas.add(new LineaVentaResponse(idVariante, info.getNombreProducto(), info.getNombreVariante(),
                    cantidad, precio.getMonto(), importe));
        }

        // 4. Guardar el pedido: Java manda el subtotal; el trigger pone número, envío, total e IGV
        Pedido p = new Pedido();
        p.setIdTienda(idTienda);
        p.setIdLogin(cliente.getIdLogin());
        p.setIdDireccionEnvio(idDireccion);
        p.setCorreoContacto(cliente.getCorreo());
        p.setSubtotal(subtotal);
        pedidoRepository.saveAndFlush(p);
        em.refresh(p);                       // lee lo que completó el trigger

        // 5. Guardar las líneas
        for (DetallePedido d : detalles) {
            d.setIdPedido(p.getIdPedido());
        }
        detallePedidoRepository.saveAll(detalles);

        log.info("Venta registrada: tienda={}, pedido #{} (id={}), {} producto(s), total {} {}",
                idTienda, p.getNumeroPedido(), p.getIdPedido(), detalles.size(), p.getTotal(), p.getMoneda());
        return VentaResponse.desde(p, lineas, "SIN_PAGO");
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
        verificadorTienda.delDueno(yo, idTienda);
        return resumenes(pedidoRepository.findByIdTiendaOrderByFechaCreacionDesc(idTienda));
    }

    /** Detalle de una venta con sus productos, cantidades y precios (RF3.2). */
    @Transactional(readOnly = true)
    public VentaResponse detalle(UsuarioAutenticado yo, Long idTienda, Long idPedido) {
        verificadorTienda.delDueno(yo, idTienda);
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
        verificadorTienda.delDueno(yo, idTienda);
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
        List<LineaVentaResponse> lineas = detalles.stream().map(d -> {
            BigDecimal precio = precios.get(d.getIdPrecio()).getMonto();
            return new LineaVentaResponse(d.getIdVariante(), d.getNombreProducto(), d.getNombreVariante(),
                    d.getCantidad(), precio, precio.multiply(BigDecimal.valueOf(d.getCantidad())));
        }).toList();
        Short estadoPago = estadosDePago(List.of(p.getIdPedido())).get(p.getIdPedido());
        return VentaResponse.desde(p, lineas, PedidoResumenResponse.nombreEstadoPago(estadoPago));
    }

    /** Estado de pago de varios pedidos en UNA consulta (los que no tienen pagos no aparecen). */
    private Map<Long, Short> estadosDePago(List<Long> idsPedidos) {
        if (idsPedidos.isEmpty()) {
            return Map.of();
        }
        return pagoRepository.estadosDe(idsPedidos).stream()
                .collect(Collectors.toMap(PagoRepository.EstadoPedido::getIdPedido, PagoRepository.EstadoPedido::getEstado));
    }
}