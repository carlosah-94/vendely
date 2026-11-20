package curso.fullstack.shopify.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Version PROVISIONAL: deja pasar todo y cobra 0 % de comision.
 * La version real (con la tabla planes) la hace el issue de Planes, sin cambiar estos metodos.
 */
@Service
public class PlanService {

    /** Lanza 403 si el plan de la tienda no permite la funcion. Por ahora no bloquea nada. */
    public void exigir(Long idTienda, FuncionPlan funcion) {
        // provisional: todas las funciones permitidas
    }

    /** Porcentaje de comision de Vendely por venta. Por ahora 0. */
    public BigDecimal comision(Long idTienda) {
        return BigDecimal.ZERO;
    }
}
