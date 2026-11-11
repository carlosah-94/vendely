package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.CotizacionEnvioResponse;
import curso.fullstack.shopify.dto.EnviosResponse;
import curso.fullstack.shopify.dto.EnviosResponse.TarifaResponse;
import curso.fullstack.shopify.dto.PaisEnvioResponse;
import curso.fullstack.shopify.dto.PaisResponse;
import curso.fullstack.shopify.dto.TarifaRequest;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.DireccionRepository;
import curso.fullstack.shopify.repository.EnvioRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
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
 * una venta al extranjero no lleva IGV y, si la tienda no tiene tarifa para ese país, el pedido
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

    public EnvioService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
                        EnvioRepository envioRepository, DireccionRepository direccionRepository) {
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

    /** Crea o cambia la tarifa de una región del país de la tienda (departamento en Perú). */
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
        EnvioRepository.Cotizacion c = envioRepository.cotizar(yo.idTienda(), yo.idLogin(), idDireccion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La dirección no existe"));
        boolean internacional = Boolean.TRUE.equals(c.getInternacional());

        if (c.getCosto() == null) {
            String mensaje = internacional
                    ? "Esta tienda todavía no envía a " + c.getPais() + ". Elige otra dirección o escribe a la tienda."
                    : "Esta tienda todavía no envía a esa zona. Elige otra dirección.";
            return new CotizacionEnvioResponse(false, internacional, c.getIdPais(), c.getPais(),
                    null, null, null, null, mensaje);
        }

        BigDecimal costo = c.getCosto();
        if (c.getEnvioGratisDesde() != null && subtotal != null && subtotal.compareTo(c.getEnvioGratisDesde()) >= 0) {
            costo = BigDecimal.ZERO;
        }
        String mensaje = internacional
                ? "Envío internacional a " + c.getPais() + ". El precio no incluye IGV: los impuestos de "
                  + "importación de tu país los pagas al recibir el pedido."
                : null;
        return new CotizacionEnvioResponse(true, internacional, c.getIdPais(), c.getPais(), costo,
                c.getEnvioGratisDesde(), c.getDiasEntregaMin(), c.getDiasEntregaMax(), mensaje);
    }

    /** Países a los que vende una tienda (para mostrarlo en la tienda pública). */
    @Transactional(readOnly = true)
    public List<PaisEnvioResponse> paisesDeEnvio(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        return envioRepository.paisesDeEnvio(t.getIdTienda()).stream()
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
                envioRepository.internacionales(t.getIdTienda()).stream().map(TarifaResponse::desde).toList());
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
