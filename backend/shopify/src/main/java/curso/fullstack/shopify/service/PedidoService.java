package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.LineaVentaRequest;
import curso.fullstack.shopify.dto.LineaVentaResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @PersistenceContext
    private EntityManager em;

    public PedidoService(VerificadorTienda verificadorTienda, LoginRepository loginRepository,
                         DireccionRepository direccionRepository, VarianteRepository varianteRepository,
                         PrecioRepository precioRepository, InventarioRepository inventarioRepository,
                         PedidoRepository pedidoRepository, DetallePedidoRepository detallePedidoRepository) {
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
     * Registra una venta. Todo o nada: si falta stock de un producto, se lanza ApiException
     * (RuntimeException) y @Transactional deshace también el stock ya descontado de los otros.
     */
    @Transactional
    public VentaResponse registrar(UsuarioAutenticado yo, Long idTienda, VentaRequest r) {
        // 1. La tienda es del dueño (404 / 403) y está activa
        Tienda tienda = verificadorTienda.delDueno(yo, idTienda);
        if (!tienda.estaActiva()) {
            throw new ApiException(HttpStatus.CONFLICT, "La tienda está suspendida");
        }

        // 2. El cliente es de ESTA tienda y la dirección es suya
        Login cliente = loginRepository.findById(r.idLogin())
                .filter(l -> l.getRol() == RolUsuario.CLIENTE && idTienda.equals(l.getIdTienda()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El cliente no existe en esta tienda"));
        if (!direccionRepository.existsByIdDireccionAndIdLoginAndEstado(r.idDireccionEnvio(), cliente.getIdLogin(), (short) 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La dirección de envío no es de este cliente");
        }

        // 3. Juntar líneas repetidas (la llave de detalle_pedido no permite la misma variante dos veces)
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (LineaVentaRequest l : r.lineas()) {
            cantidades.merge(l.idVariante(), l.cantidad(), Integer::sum);
        }

        // 4. Por cada producto: validar, descontar stock y tomar el precio vigente
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

        // 5. Guardar el pedido: Java manda el subtotal; el trigger pone número, envío, total e IGV
        Pedido p = new Pedido();
        p.setIdTienda(idTienda);
        p.setIdLogin(cliente.getIdLogin());
        p.setIdDireccionEnvio(r.idDireccionEnvio());
        p.setCorreoContacto(cliente.getCorreo());
        p.setSubtotal(subtotal);
        pedidoRepository.saveAndFlush(p);
        em.refresh(p);                       // lee lo que completó el trigger

        // 6. Guardar las líneas
        for (DetallePedido d : detalles) {
            d.setIdPedido(p.getIdPedido());
        }
        detallePedidoRepository.saveAll(detalles);

        return VentaResponse.desde(p, lineas);
    }
}