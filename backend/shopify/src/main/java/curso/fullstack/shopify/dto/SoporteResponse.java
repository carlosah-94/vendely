package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.RegistroSoporte;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * El acceso de soporte de una tienda: si está activo, hasta cuándo, y lo último que pasó.
 * activoHasta es null si soporte no puede entrar (nunca se dio, se quitó o venció).
 */
public record SoporteResponse(OffsetDateTime activoHasta, List<Registro> registro) {

    /**
     * Una línea del registro. accion: PERMITIDO y QUITADO (los hizo el dueño) o CAMBIO (lo hizo
     * soporte; metodo y ruta dicen qué petición fue).
     */
    public record Registro(String accion, String metodo, String ruta, OffsetDateTime fecha) {
        public static Registro desde(RegistroSoporte r) {
            return new Registro(r.getAccion(), r.getMetodo(), r.getRuta(), r.getFecha());
        }
    }
}
