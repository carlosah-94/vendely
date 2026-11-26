package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.CuponAplicadoResponse;
import curso.fullstack.shopify.dto.CuponRequest;
import curso.fullstack.shopify.dto.CuponResponse;
import curso.fullstack.shopify.entity.Cupon;
import curso.fullstack.shopify.entity.Oferta;
import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.CuponRepository;
import curso.fullstack.shopify.repository.OfertaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Códigos de descuento ("VERANO10"), como los de Shopify: el dueño los crea en el panel y el
 * cliente los escribe en el checkout. Rebajan el subtotal del pedido (productos, ya con sus
 * ofertas automáticas) en un porcentaje o un monto fijo.
 *
 * Las reglas (activo, fechas, compra mínima, máximo de usos, una vez por cliente) se revisan en
 * {@link #aplicar}: al previsualizar en el checkout y otra vez al crear el pedido.
 */
@Slf4j
@Service
public class CuponService {

    private final VerificadorTienda verificadorTienda;
    private final CuponRepository cuponRepository;
    private final OfertaRepository ofertaRepository;

    public CuponService(VerificadorTienda verificadorTienda, CuponRepository cuponRepository,
                        OfertaRepository ofertaRepository) {
        this.verificadorTienda = verificadorTienda;
        this.cuponRepository = cuponRepository;
        this.ofertaRepository = ofertaRepository;
    }

    /** Un código que sí se puede usar en este pedido y cuánto rebaja. */
    public record Aplicado(Cupon cupon, BigDecimal descuento) {
        public CuponAplicadoResponse respuesta() {
            return new CuponAplicadoResponse(cupon.getCodigo(),
                    cupon.getTipoDescuento() == Oferta.PORCENTAJE ? "PORCENTAJE" : "MONTO_FIJO",
                    cupon.getValor(), descuento);
        }
    }

    // ------------------------------------------------------------------ checkout

    /** Vista previa para el checkout del cliente con sesión (tienda y cliente salen del token). */
    @Transactional(readOnly = true)
    public CuponAplicadoResponse validar(UsuarioAutenticado yo, String codigo, BigDecimal subtotal) {
        return aplicar(yo.idTienda(), yo.idLogin(), codigo, subtotal).respuesta();
    }

    /** Vista previa para un invitado: no hay cliente, así que un código "una vez por cliente" no aplica. */
    @Transactional(readOnly = true)
    public CuponAplicadoResponse validarInvitado(Long idTienda, String codigo, BigDecimal subtotal) {
        Aplicado a = aplicar(idTienda, null, codigo, subtotal);
        if (a.cupon().isUnaVezPorCliente()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese código es para clientes con cuenta: inicia sesión para usarlo");
        }
        return a.respuesta();
    }

    /**
     * Revisa el código para un pedido de la tienda con ese subtotal y calcula el descuento.
     * 400 si no existe o no sirve, con un mensaje que el cliente entiende.
     */
    public Aplicado aplicar(Long idTienda, Long idLogin, String codigo, BigDecimal subtotal) {
        String normalizado = normalizar(codigo);
        Cupon c = cuponRepository.findByIdTiendaAndCodigo(idTienda, normalizado)
                .orElseThrow(() -> rechazo("El código " + normalizado + " no existe"));
        OffsetDateTime ahora = OffsetDateTime.now();
        if (c.getEstado() != Cupon.ACTIVO || c.getFechaInicio().isAfter(ahora)) {
            throw rechazo("El código " + normalizado + " no está disponible");
        }
        if (c.getFechaFin() != null && !c.getFechaFin().isAfter(ahora)) {
            throw rechazo("El código " + normalizado + " ya venció");
        }
        if (c.getCompraMinima() != null && subtotal.compareTo(c.getCompraMinima()) < 0) {
            throw rechazo("El código " + normalizado + " es para compras desde " + c.getCompraMinima().toPlainString());
        }
        if (c.getUsosMaximos() != null && cuponRepository.usosDe(c.getIdCupon()) >= c.getUsosMaximos()) {
            throw rechazo("El código " + normalizado + " ya se agotó");
        }
        if (c.isUnaVezPorCliente() && idLogin != null && cuponRepository.usadoPorCliente(c.getIdCupon(), idLogin)) {
            throw rechazo("Ya usaste el código " + normalizado);
        }
        Integer d = ofertaRepository.decimalesDeTienda(idTienda);
        // Nunca rebaja más que el subtotal (CHECK ck_pedidos_cupon).
        BigDecimal descuento = CalculoOferta.descuento(c.getTipoDescuento(), c.getValor(), subtotal, d == null ? 2 : d)
                .min(subtotal);
        return new Aplicado(c, descuento);
    }

    // ------------------------------------------------------------------ panel

    @Transactional(readOnly = true)
    public List<CuponResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        List<Cupon> cupones = cuponRepository.findByIdTiendaOrderByFechaCreacionDesc(idTienda);
        if (cupones.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> usos = cuponRepository.usos(cupones.stream().map(Cupon::getIdCupon).toList()).stream()
                .collect(Collectors.toMap(CuponRepository.Uso::getIdCupon, CuponRepository.Uso::getUsos));
        OffsetDateTime ahora = OffsetDateTime.now();
        return cupones.stream().map(c -> CuponResponse.desde(c, usos.getOrDefault(c.getIdCupon(), 0L), ahora)).toList();
    }

    @Transactional
    public CuponResponse crear(UsuarioAutenticado yo, Long idTienda, CuponRequest r) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        String codigo = normalizar(r.codigo());
        if (cuponRepository.existsByIdTiendaAndCodigo(idTienda, codigo)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya tienes un código " + codigo);
        }
        Cupon c = new Cupon();
        c.setIdTienda(idTienda);
        c.setFechaInicio(r.fechaInicio() == null ? OffsetDateTime.now() : r.fechaInicio());
        copiar(c, r, codigo, idTienda);
        cuponRepository.saveAndFlush(c);
        log.info("Código de descuento creado: idCupon={}, tienda={}, codigo={}", c.getIdCupon(), idTienda, codigo);
        return CuponResponse.desde(c, 0, OffsetDateTime.now());
    }

    /**
     * Editar sí se puede aunque ya se haya usado: cada pedido guardó cuánto rebajó el código
     * (descuento_cupon), así que cambiar el valor no altera las ventas pasadas.
     */
    @Transactional
    public CuponResponse actualizar(UsuarioAutenticado yo, Long idTienda, Long idCupon, CuponRequest r) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        Cupon c = buscar(idTienda, idCupon);
        String codigo = normalizar(r.codigo());
        if (cuponRepository.existsByIdTiendaAndCodigoAndIdCuponNot(idTienda, codigo, idCupon)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya tienes otro código " + codigo);
        }
        if (r.fechaInicio() != null) {
            c.setFechaInicio(r.fechaInicio());
        }
        copiar(c, r, codigo, idTienda);
        cuponRepository.saveAndFlush(c);
        log.info("Código de descuento actualizado: idCupon={}, tienda={}", idCupon, idTienda);
        return CuponResponse.desde(c, cuponRepository.usosDe(idCupon), OffsetDateTime.now());
    }

    @Transactional
    public CuponResponse cambiarEstado(UsuarioAutenticado yo, Long idTienda, Long idCupon, boolean activo) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        Cupon c = buscar(idTienda, idCupon);
        c.setEstado(activo ? Cupon.ACTIVO : Cupon.PAUSADO);
        cuponRepository.saveAndFlush(c);
        log.info("Código de descuento {}: idCupon={}, tienda={}", activo ? "activado" : "pausado", idCupon, idTienda);
        return CuponResponse.desde(c, cuponRepository.usosDe(idCupon), OffsetDateTime.now());
    }

    /** Solo se borra un código que ningún pedido usó (los pedidos guardan cuál se usó). */
    @Transactional
    public void eliminar(UsuarioAutenticado yo, Long idTienda, Long idCupon) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PRODUCTOS);
        Cupon c = buscar(idTienda, idCupon);
        if (cuponRepository.enPedidos(idCupon)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Hay pedidos con este código: no se puede borrar, solo pausar");
        }
        cuponRepository.delete(c);
        log.info("Código de descuento eliminado: idCupon={}, tienda={}", idCupon, idTienda);
    }

    // ------------------------------------------------------------------ apoyo

    private Cupon buscar(Long idTienda, Long idCupon) {
        return cuponRepository.findByIdCuponAndIdTienda(idCupon, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El código no existe en esta tienda"));
    }

    /** Los códigos se guardan y se buscan en mayúsculas: "verano10" y "VERANO10" son el mismo. */
    static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    private static ApiException rechazo(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private void copiar(Cupon c, CuponRequest r, String codigo, Long idTienda) {
        boolean porcentaje = "PORCENTAJE".equals(r.tipoDescuento());
        if (porcentaje && r.valor().compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El porcentaje debe ser menor que 100");
        }
        Integer d = ofertaRepository.decimalesDeTienda(idTienda);
        int dec = d == null ? 2 : d;
        if (r.valor().stripTrailingZeros().scale() > (porcentaje ? 2 : dec)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, porcentaje ? "El porcentaje puede tener hasta 2 decimales"
                    : dec == 0 ? "En la moneda de la tienda el monto va sin decimales"
                               : "El monto puede tener hasta " + dec + " decimales");
        }
        if (r.compraMinima() != null && r.compraMinima().stripTrailingZeros().scale() > dec) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La compra mínima va con hasta " + dec + " decimales");
        }
        if (r.fechaFin() != null && !r.fechaFin().isAfter(c.getFechaInicio())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la de inicio");
        }
        c.setCodigo(codigo);
        c.setTipoDescuento(porcentaje ? Oferta.PORCENTAJE : Oferta.MONTO_FIJO);
        c.setValor(r.valor());
        c.setCompraMinima(r.compraMinima());
        c.setUsosMaximos(r.usosMaximos());
        c.setUnaVezPorCliente(Boolean.TRUE.equals(r.unaVezPorCliente()));
        c.setFechaFin(r.fechaFin());
        c.setEstado(r.activo() == null || r.activo() ? Cupon.ACTIVO : Cupon.PAUSADO);
    }
}
