package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.CotizacionEnvioResponse;
import curso.fullstack.shopify.dto.CotizacionEnvioResponse.OpcionEntrega;
import curso.fullstack.shopify.dto.EnviosResponse;
import curso.fullstack.shopify.dto.EnviosResponse.TarifaResponse;
import curso.fullstack.shopify.dto.PaisEnvioResponse;
import curso.fullstack.shopify.dto.PaisResponse;
import curso.fullstack.shopify.dto.RecojoRequest;
import curso.fullstack.shopify.dto.TarifaRequest;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.entity.TipoEntrega;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.DireccionRepository;
import curso.fullstack.shopify.repository.EnvioRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envíos de una tienda, nacionales e internacionales.
 *
 * La base ya hace cumplir las reglas al crear un pedido (trigger T2): el costo sale de la tarifa,
 * una venta al extranjero no lleva el impuesto de la tienda (IGV, IVA) y, si la tienda no tiene tarifa para ese país, el pedido
 * se rechaza. Este service le da al dueño las pantallas para configurar sus tarifas y al cliente
 * una cotización ANTES de pagar, con la misma regla.
 */
@Slf4j
@Service
public class EnvioService {

    private static final short ACTIVA = 1;
    private static final short PAUSADA = 0;

    private final VerificadorTienda verificadorTienda;
    private final TiendaRepository tiendaRepository;
    private final EnvioRepository envioRepository;
    private final DireccionRepository direccionRepository;
    private final PlanService planService;

    public EnvioService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
                        EnvioRepository envioRepository, DireccionRepository direccionRepository,
                        PlanService planService) {
        this.planService = planService;
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.envioRepository = envioRepository;
        this.direccionRepository = direccionRepository;
    }

    // ------------------------------------------------------------------ panel del dueño

    @Transactional(readOnly = true)
    public EnviosResponse envios(UsuarioAutenticado yo, Long idTienda) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        return armar(t);
    }

    /** Crea o cambia la tarifa de una región del país de la tienda (departamento en Perú, región en Chile). */
    @Transactional
    public EnviosResponse guardarNacional(UsuarioAutenticado yo, Long idTienda, Long idDivision, TarifaRequest r) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        if (!envioRepository.esRegionDeLaTienda(idTienda, idDivision)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Esa región no es del país de la tienda");
        }
        validar(r);
        envioRepository.guardarNacional(idTienda, idDivision, r.costo(), r.envioGratisDesde(),
                r.diasEntregaMin(), r.diasEntregaMax(), estado(r));
        log.info("Tarifa nacional guardada: tienda={}, region={}, activa={}", idTienda, idDivision, estado(r) == ACTIVA);
        return armar(t);
    }

    /** Crea o cambia la tarifa a otro país. Con ella activa, la tienda empieza a vender a ese país. */
    @Transactional
    public EnviosResponse guardarInternacional(UsuarioAutenticado yo, Long idTienda, String idPais, TarifaRequest r) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        planService.exigir(idTienda, FuncionPlan.ENVIO_INTERNACIONAL);   // solo plan Pro
        String pais = idPais.trim().toUpperCase(Locale.ROOT);
        if (direccionRepository.pais(pais).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El país no existe");
        }
        if (pais.equals(t.getIdPais())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Es el país de la tienda: sus envíos se configuran por región en \"Envíos nacionales\"");
        }
        validar(r);
        envioRepository.guardarInternacional(idTienda, pais, r.costo(), r.envioGratisDesde(),
                r.diasEntregaMin(), r.diasEntregaMax(), estado(r));
        log.info("Tarifa internacional guardada: tienda={}, pais={}, activa={}", idTienda, pais, estado(r) == ACTIVA);
        return armar(t);
    }

    /** Deja de enviar a un país. Los pedidos que ya se hicieron no cambian (guardan su costo de envío). */
    @Transactional
    public EnviosResponse borrarInternacional(UsuarioAutenticado yo, Long idTienda, String idPais) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        String pais = idPais.trim().toUpperCase(Locale.ROOT);
        if (envioRepository.borrarInternacional(idTienda, pais) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "La tienda no tiene una tarifa para ese país");
        }
        log.info("Tarifa internacional eliminada: tienda={}, pais={}", idTienda, pais);
        return armar(t);
    }

    // ------------------------------------------------------------------ tienda pública y cliente

    /**
     * Costo de envío a una dirección del cliente que inició sesión. subtotal sirve para saber si
     * aplica el envío gratis; el monto final lo vuelve a calcular la base al crear el pedido.
     */
    @Transactional(readOnly = true)
    public CotizacionEnvioResponse cotizar(UsuarioAutenticado yo, Long idDireccion, BigDecimal subtotal) {
        return cotizar(yo.idTienda(), yo.idLogin(), idDireccion, subtotal);
    }

    /**
     * Lo mismo para cualquier comprador: idLogin del cliente, o null para la dirección de un
     * invitado (que el controlador ya verificó con su clave).
     */
    @Transactional(readOnly = true)
    public CotizacionEnvioResponse cotizar(Long idTienda, Long idLogin, Long idDireccion, BigDecimal subtotal) {
        EnvioRepository.Cotizacion c = envioRepository.cotizar(idTienda, idLogin, idDireccion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La dirección no existe"));
        boolean internacional = Boolean.TRUE.equals(c.getInternacional());

        // Si la tienda bajó a un plan sin envíos internacionales, sus tarifas siguen guardadas
        // (vuelven a usarse si mejora el plan), pero no se vende al extranjero.
        if (internacional && !planService.permite(idTienda, FuncionPlan.ENVIO_INTERNACIONAL)) {
            return new CotizacionEnvioResponse(false, true, c.getIdPais(), c.getPais(), null, null, null, null,
                    "Esta tienda no envía a " + c.getPais() + " por ahora. Elige una dirección en "
                            + paisDeLaTienda(idTienda) + ".", List.of());
        }

        // Delivery de la tienda y recojo en el local (V18): solo dentro del país de la tienda.
        List<OpcionEntrega> locales = internacional ? List.of()
                : opcionesLocales(idTienda, idLogin, idDireccion, subtotal);

        if (c.getCosto() == null) {
            if (!locales.isEmpty()) {
                return new CotizacionEnvioResponse(true, false, c.getIdPais(), c.getPais(), null, null, null, null,
                        "Esta tienda no envía por courier a esa zona, pero puedes elegir otra forma de entrega.", locales);
            }
            String mensaje = internacional
                    ? "Esta tienda todavía no envía a " + c.getPais() + ". Elige otra dirección o escribe a la tienda."
                    : "Esta tienda todavía no envía a esa zona. Elige otra dirección.";
            return new CotizacionEnvioResponse(false, internacional, c.getIdPais(), c.getPais(),
                    null, null, null, null, mensaje, List.of());
        }

        BigDecimal costo = conGratis(c.getCosto(), c.getEnvioGratisDesde(), subtotal);
        String mensaje = null;
        if (internacional) {
            // El impuesto del país de la tienda (IGV en Perú, IVA en Chile) no se cobra en una exportación.
            String impuesto = tiendaRepository.findById(idTienda)
                    .flatMap(t -> direccionRepository.pais(t.getIdPais()))
                    .map(DireccionRepository.PaisInfo::getImpuesto)
                    .orElse("impuesto");
            mensaje = "Envío internacional a " + c.getPais() + ". El precio no incluye " + impuesto
                    + ": los impuestos de importación de tu país los pagas al recibir el pedido.";
        }
        List<OpcionEntrega> opciones = new ArrayList<>();
        opciones.add(new OpcionEntrega(TipoEntrega.ENVIO.name(),
                internacional ? "Envío internacional" : TipoEntrega.ENVIO.nombre(), costo,
                plazo(c.getDiasEntregaMin(), c.getDiasEntregaMax())));
        opciones.addAll(locales);
        return new CotizacionEnvioResponse(true, internacional, c.getIdPais(), c.getPais(), costo,
                c.getEnvioGratisDesde(), c.getDiasEntregaMin(), c.getDiasEntregaMax(), mensaje, opciones);
    }

    /** Delivery al distrito de esa dirección (si la tienda lo atiende) y recojo en el local (si lo ofrece). */
    private List<OpcionEntrega> opcionesLocales(Long idTienda, Long idLogin, Long idDireccion, BigDecimal subtotal) {
        List<OpcionEntrega> opciones = new ArrayList<>();
        envioRepository.deliveryPara(idTienda, idLogin, idDireccion).ifPresent(z -> opciones.add(
                new OpcionEntrega(TipoEntrega.DELIVERY.name(), TipoEntrega.DELIVERY.nombre(),
                        conGratis(z.getCosto(), z.getEnvioGratisDesde(), subtotal),
                        "La tienda lo lleva a " + z.getDistrito())));
        tiendaRepository.findById(idTienda)
                .filter(t -> Boolean.TRUE.equals(t.getRecojoActivo()))
                .ifPresent(t -> opciones.add(new OpcionEntrega(TipoEntrega.RECOJO.name(), TipoEntrega.RECOJO.nombre(),
                        BigDecimal.ZERO, t.getRecojoDireccion()
                                + (t.getRecojoHorario() == null ? "" : " · " + t.getRecojoHorario()))));
        return opciones;
    }

    private static BigDecimal conGratis(BigDecimal costo, BigDecimal gratisDesde, BigDecimal subtotal) {
        return gratisDesde != null && subtotal != null && subtotal.compareTo(gratisDesde) >= 0 ? BigDecimal.ZERO : costo;
    }

    private static String plazo(Short min, Short max) {
        if (min == null && max == null) return null;
        if (min == null || min.equals(max)) return "Llega en " + (max == null ? min : max) + " día(s)";
        if (max == null) return "Llega desde " + min + " día(s)";
        return "Llega de " + min + " a " + max + " días";
    }

    // ------------------------------------------------------------ delivery y recojo (V18)

    /** Crea o cambia el costo del delivery a un distrito. Todos los planes: es la venta del barrio. */
    @Transactional
    public EnviosResponse guardarDelivery(UsuarioAutenticado yo, Long idTienda, Long idDivision, TarifaRequest r) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        if (!envioRepository.esDistritoDeLaTienda(idTienda, idDivision)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Elige un distrito del país de la tienda");
        }
        envioRepository.guardarZona(idTienda, idDivision, r.costo(), r.envioGratisDesde(), estado(r));
        log.info("Zona de delivery guardada: tienda={}, distrito={}, activa={}", idTienda, idDivision, estado(r) == ACTIVA);
        return armar(t);
    }

    /** Deja de llevar pedidos a ese distrito. Los pedidos ya hechos guardan su costo. */
    @Transactional
    public EnviosResponse borrarDelivery(UsuarioAutenticado yo, Long idTienda, Long idDivision) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        if (envioRepository.borrarZona(idTienda, idDivision) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "La tienda no hace delivery a ese distrito");
        }
        log.info("Zona de delivery borrada: tienda={}, distrito={}", idTienda, idDivision);
        return armar(t);
    }

    /** Recojo en el local: gratis para el cliente. Para activarlo hace falta la dirección. */
    @Transactional
    public EnviosResponse guardarRecojo(UsuarioAutenticado yo, Long idTienda, RecojoRequest r) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        String direccion = r.direccion() == null || r.direccion().isBlank() ? null : r.direccion().trim();
        String horario = r.horario() == null || r.horario().isBlank() ? null : r.horario().trim();
        if (r.activo() && direccion == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escribe la dirección donde tus clientes recogen sus pedidos");
        }
        t.setRecojoActivo(r.activo());
        t.setRecojoDireccion(direccion);
        t.setRecojoHorario(horario);
        tiendaRepository.saveAndFlush(t);
        log.info("Recojo en tienda: tienda={}, activo={}", idTienda, r.activo());
        return armar(t);
    }

    /** Países a los que vende una tienda (para mostrarlo en la tienda pública). */
    @Transactional(readOnly = true)
    public List<PaisEnvioResponse> paisesDeEnvio(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        boolean internacional = planService.permite(t.getIdTienda(), FuncionPlan.ENVIO_INTERNACIONAL);
        return envioRepository.paisesDeEnvio(t.getIdTienda()).stream()
                .filter(p -> internacional || !Boolean.TRUE.equals(p.getInternacional()))
                .map(p -> new PaisEnvioResponse(p.getIdPais(), p.getNombre(), Boolean.TRUE.equals(p.getInternacional())))
                .toList();
    }

    /** Todos los países, para los selectores. */
    @Transactional(readOnly = true)
    public List<PaisResponse> paises() {
        return direccionRepository.paises().stream().map(PaisResponse::desde).toList();
    }

    // ------------------------------------------------------------------ apoyo

    private EnviosResponse armar(Tienda t) {
        DireccionRepository.PaisInfo pais = direccionRepository.pais(t.getIdPais())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El país de la tienda no existe"));
        return new EnviosResponse(t.getIdPais(), pais.getNombre(), pais.getIdMoneda(),
                envioRepository.nacionales(t.getIdTienda()).stream().map(TarifaResponse::desde).toList(),
                envioRepository.internacionales(t.getIdTienda()).stream().map(TarifaResponse::desde).toList(),
                planService.permite(t.getIdTienda(), FuncionPlan.ENVIO_INTERNACIONAL),
                envioRepository.zonasDelivery(t.getIdTienda()).stream()
                        .map(z -> new EnviosResponse.ZonaDelivery(z.getIdDivision(), z.getDistrito(), z.getProvincia(),
                                z.getCosto(), z.getEnvioGratisDesde(), z.getEstado() == ACTIVA ? "ACTIVA" : "PAUSADA"))
                        .toList(),
                new EnviosResponse.Recojo(Boolean.TRUE.equals(t.getRecojoActivo()), t.getRecojoDireccion(),
                        t.getRecojoHorario()));
    }

    /** "Perú": el nombre del país de la tienda, para los mensajes. */
    private String paisDeLaTienda(Long idTienda) {
        return tiendaRepository.findById(idTienda)
                .flatMap(t -> direccionRepository.pais(t.getIdPais()))
                .map(DireccionRepository.PaisInfo::getNombre)
                .orElse("el país de la tienda");
    }

    /** Reglas que la base también revisa (CHECK), validadas antes para dar un mensaje claro. */
    private static void validar(TarifaRequest r) {
        if (r.diasEntregaMin() != null && r.diasEntregaMax() != null && r.diasEntregaMin() > r.diasEntregaMax()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Los días mínimos no pueden ser más que los máximos");
        }
    }

    private static short estado(TarifaRequest r) {
        return Boolean.FALSE.equals(r.activa()) ? PAUSADA : ACTIVA;
    }
}
