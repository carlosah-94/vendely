package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.EnvioRepository;
import java.math.BigDecimal;
import java.util.List;

/** Configuración de envíos de una tienda: tarifas por región de su país y por país extranjero. */
public record EnviosResponse(
        String idPais,          // país de la tienda
        String pais,
        String moneda,          // los costos van en esta moneda
        List<TarifaResponse> nacionales,
        List<TarifaResponse> internacionales
) {
    /**
     * Una tarifa. idZona = id de la región (nacional) o código del país (internacional).
     * estado: "ACTIVA", "PAUSADA" o "SIN_TARIFA" (región sin configurar: no se envía ahí).
     */
    public record TarifaResponse(String idZona, String zona, BigDecimal costo, BigDecimal envioGratisDesde,
                                 Short diasEntregaMin, Short diasEntregaMax, String estado) {
        public static TarifaResponse desde(EnvioRepository.Tarifa t) {
            String estado = t.getEstado() == null ? "SIN_TARIFA" : (t.getEstado() == 1 ? "ACTIVA" : "PAUSADA");
            return new TarifaResponse(t.getIdZona(), t.getZona(), t.getCosto(), t.getEnvioGratisDesde(),
                    t.getDiasEntregaMin(), t.getDiasEntregaMax(), estado);
        }
    }
}
