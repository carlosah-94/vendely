package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.OfertaRequest;
import curso.fullstack.shopify.dto.OfertaResponse;
import curso.fullstack.shopify.entity.Oferta;
import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.OfertaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ofertas automáticas de una tienda (descuentos por porcentaje o monto fijo sobre algunos
 * productos, entre dos fechas), como los descuentos automáticos de Shopify.
 * Mientras una oferta está vigente, la tienda muestra el precio rebajado y PedidoService lo cobra.
 *
 * Una oferta con la que ya se vendió conserva sus condiciones (trigger T11): aquí se revisa antes
 * para responder con un mensaje claro en vez del error de la base.
 */
@Slf4j
@Service
public class OfertaService {

    private final VerificadorTienda verificadorTienda;
    private final OfertaRepository ofertaRepository;

    public OfertaService(VerificadorTienda verificadorTienda, OfertaRepository ofertaRepository) {
        this.verificadorTienda = verificadorTienda;
        this.ofertaRepository = ofertaRepository;
    }

    @Transactional(readOnly = true)
    public List<OfertaResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        List<Oferta> ofertas = ofertaRepository.findByIdTiendaOrderByFechaCreacionDesc(idTienda);
        if (ofertas.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> usos = ofertaRepository.usos(ofertas.stream().map(Oferta::getIdOferta).toList()).stream()
                .collect(Collectors.toMap(OfertaRepository.Uso::getIdOferta, OfertaRepository.Uso::getUnidades));
        OffsetDateTime ahora = OffsetDateTime.now();
        return ofertas.stream().map(o -> OfertaResponse.desde(o, usos.getOrDefault(o.getIdOferta(), 0L), ahora)).toList();
    }

    @Transactional
    public OfertaResponse crear(UsuarioAutenticado yo, Long idTienda, OfertaRequest r) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        Set<Long> productos = validar(idTienda, r);
        OffsetDateTime inicio = r.fechaInicio() == null ? OffsetDateTime.now() : r.fechaInicio();
        validarFechas(inicio, r.fechaFin());

        Oferta o = new Oferta();
        o.setIdTienda(idTienda);
        o.setTipoDescuento(tipo(r.tipoDescuento()));
        o.setValor(r.valor());
        o.setFechaInicio(inicio);
        copiar(o, r, productos);
        ofertaRepository.saveAndFlush(o);
        log.info("Oferta creada: idOferta={}, tienda={}, {} producto(s)", o.getIdOferta(), idTienda, productos.size());
        return OfertaResponse.desde(o, 0, OffsetDateTime.now());
    }

    @Transactional
    public OfertaResponse actualizar(UsuarioAutenticado yo, Long idTienda, Long idOferta, OfertaRequest r) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        Oferta o = buscar(idTienda, idOferta);
        Set<Long> productos = validar(idTienda, r);
        short tipo = tipo(r.tipoDescuento());
        OffsetDateTime inicio = r.fechaInicio() == null ? o.getFechaInicio() : r.fechaInicio();
        validarFechas(inicio, r.fechaFin());

        if (ofertaRepository.usada(idOferta)) {
            // Lo que ya se vendió debe seguir cuadrando con la oferta (trigger T11).
            if (tipo != o.getTipoDescuento() || r.valor().compareTo(o.getValor()) != 0
                    || !inicio.isEqual(o.getFechaInicio())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Ya se vendió con esta oferta: su tipo, valor e inicio no se pueden cambiar. "
                                + "Puedes pausarla y crear otra.");
            }
            List<Long> quitados = ofertaRepository.productosVendidos(idOferta).stream()
                    .filter(p -> !productos.contains(p)).toList();
            if (!quitados.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Ya se vendieron productos con esta oferta: no se pueden quitar de ella");
            }
            if (r.fechaFin() != null && ofertaRepository.vendidaDesde(idOferta, r.fechaFin())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Hay ventas con esta oferta después de esa fecha de fin: elige una fecha posterior");
            }
        }
        o.setTipoDescuento(tipo);
        o.setValor(r.valor());
        o.setFechaInicio(inicio);
        copiar(o, r, productos);
        ofertaRepository.saveAndFlush(o);
        log.info("Oferta actualizada: idOferta={}, tienda={}", idOferta, idTienda);
        long unidades = ofertaRepository.usos(List.of(idOferta)).stream()
                .mapToLong(OfertaRepository.Uso::getUnidades).sum();
        return OfertaResponse.desde(o, unidades, OffsetDateTime.now());
    }

    /** Pausar (false) o reactivar (true) una oferta. Siempre se puede, aunque ya se haya vendido con ella. */
    @Transactional
    public OfertaResponse cambiarEstado(UsuarioAutenticado yo, Long idTienda, Long idOferta, boolean activa) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        Oferta o = buscar(idTienda, idOferta);
        o.setEstado(activa ? Oferta.ACTIVA : Oferta.PAUSADA);
        ofertaRepository.saveAndFlush(o);
        log.info("Oferta {}: idOferta={}, tienda={}", activa ? "activada" : "pausada", idOferta, idTienda);
        long unidades = ofertaRepository.usos(List.of(idOferta)).stream()
                .mapToLong(OfertaRepository.Uso::getUnidades).sum();
        return OfertaResponse.desde(o, unidades, OffsetDateTime.now());
    }

    /** Borra una oferta que nunca se usó. Si ya se vendió con ella, los pedidos la necesitan: solo se pausa. */
    @Transactional
    public void eliminar(UsuarioAutenticado yo, Long idTienda, Long idOferta) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        Oferta o = buscar(idTienda, idOferta);
        if (ofertaRepository.usada(idOferta)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Ya se vendió con esta oferta y los pedidos la necesitan: no se puede borrar, solo pausar");
        }
        ofertaRepository.delete(o);
        log.info("Oferta eliminada: idOferta={}, tienda={}", idOferta, idTienda);
    }

    // ------------------------------------------------------------------ apoyo

    private Oferta buscar(Long idTienda, Long idOferta) {
        return ofertaRepository.findByIdOfertaAndIdTienda(idOferta, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La oferta no existe en esta tienda"));
    }

    private static short tipo(String tipoDescuento) {
        return "PORCENTAJE".equals(tipoDescuento) ? Oferta.PORCENTAJE : Oferta.MONTO_FIJO;
    }

    /** Valor y productos válidos para la tienda; devuelve los productos sin repetir. */
    private Set<Long> validar(Long idTienda, OfertaRequest r) {
        BigDecimal valor = r.valor();
        if ("PORCENTAJE".equals(r.tipoDescuento())) {
            if (valor.compareTo(BigDecimal.valueOf(100)) >= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El porcentaje debe ser menor que 100");
            }
            if (valor.stripTrailingZeros().scale() > 2) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El porcentaje puede tener hasta 2 decimales");
            }
        } else {
            Integer decimales = ofertaRepository.decimalesDeTienda(idTienda);
            int dec = decimales == null ? 2 : decimales;
            if (valor.stripTrailingZeros().scale() > dec) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        dec == 0 ? "En la moneda de la tienda el monto va sin decimales"
                                 : "El monto puede tener hasta " + dec + " decimales");
            }
        }
        Set<Long> productos = new LinkedHashSet<>(r.productos());
        productos.remove(null);
        if (productos.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Elige al menos un producto");
        }
        if (ofertaRepository.contarProductosDeTienda(idTienda, productos) != productos.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Algún producto no es de esta tienda");
        }
        return productos;
    }

    private static void validarFechas(OffsetDateTime inicio, OffsetDateTime fin) {
        if (fin != null && !fin.isAfter(inicio)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la de inicio");
        }
    }

    private static void copiar(Oferta o, OfertaRequest r, Set<Long> productos) {
        o.setNombre(r.nombre().trim());
        o.setMotivo(OfertaResponse.codigoMotivo(r.motivo()));
        o.setFechaFin(r.fechaFin());
        o.setEstado(r.activa() == null || r.activa() ? Oferta.ACTIVA : Oferta.PAUSADA);
        // Se cambia el contenido del mismo Set: Hibernate borra solo los quitados e inserta los nuevos.
        o.getProductos().retainAll(productos);
        o.getProductos().addAll(productos);
    }
}
