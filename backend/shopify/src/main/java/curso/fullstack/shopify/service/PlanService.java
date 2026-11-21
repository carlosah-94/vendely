package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.repository.ProductoRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limites del plan de cada tienda. Es el "portero": antes de crear un producto, personalizar,
 * elegir plantilla, vender a otro pais o activar otro metodo de pago, se pregunta aqui
 * si el plan lo permite. Si no, responde 403 con "Mejora tu plan para usar esto".
 * Al bajar de plan no se borra nada: solo no se puede crear mas de lo que permite.
 */
@Slf4j
@Service
public class PlanService {

    public static final String MEJORA_TU_PLAN = "Mejora tu plan para usar esto";

    private final TiendaRepository tiendaRepository;
    private final PlanRepository planRepository;
    private final ProductoRepository productoRepository;

    public PlanService(TiendaRepository tiendaRepository, PlanRepository planRepository,
                       ProductoRepository productoRepository) {
        this.tiendaRepository = tiendaRepository;
        this.planRepository = planRepository;
        this.productoRepository = productoRepository;
    }

    /** Plan actual de la tienda. */
    @Transactional(readOnly = true)
    public Plan planDe(Long idTienda) {
        Tienda t = tiendaRepository.findById(idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        return planRepository.findById(t.getIdPlan())
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "El plan de la tienda no existe"));
    }

    /** Lanza 403 si el plan de la tienda no permite la funcion. */
    @Transactional(readOnly = true)
    public void exigir(Long idTienda, FuncionPlan funcion) {
        Plan p = planDe(idTienda);
        if (permite(p, funcion, idTienda)) {
            return;
        }
        log.info("Bloqueado por el plan: tienda={}, plan={}, funcion={}", idTienda, p.getNombre(), funcion);
        throw new ApiException(HttpStatus.FORBIDDEN, mensaje(p, funcion));
    }

    /** true si el plan permite la funcion (sin lanzar error): sirve para mostrar candados. */
    @Transactional(readOnly = true)
    public boolean permite(Long idTienda, FuncionPlan funcion) {
        return permite(planDe(idTienda), funcion, idTienda);
    }

    /** Porcentaje de comision de Vendely por venta segun el plan (5.00 = 5 %). */
    @Transactional(readOnly = true)
    public BigDecimal comision(Long idTienda) {
        return planDe(idTienda).getComision();
    }

    /** Cuantos productos tiene la tienda (activos y borradores). */
    @Transactional(readOnly = true)
    public long productosUsados(Long idTienda) {
        return productoRepository.countByIdTienda(idTienda);
    }

    // ------------------------------------------------------------------ apoyo

    private boolean permite(Plan p, FuncionPlan funcion, Long idTienda) {
        return switch (funcion) {
            case CREAR_PRODUCTO -> p.getMaxProductos() == null
                    || productoRepository.countByIdTienda(idTienda) < p.getMaxProductos();
            case PERSONALIZAR -> Boolean.TRUE.equals(p.getPermitePersonalizar());
            case PLANTILLAS -> Boolean.TRUE.equals(p.getPermitePlantillas());
            case ENVIO_INTERNACIONAL -> Boolean.TRUE.equals(p.getPermiteEnvioInternacional());
            case METODOS_PAGO -> Boolean.TRUE.equals(p.getPermiteMasMetodosPago());
        };
    }

    private static String mensaje(Plan p, FuncionPlan funcion) {
        String plan = "Tu plan " + p.getNombre();
        return switch (funcion) {
            case CREAR_PRODUCTO -> plan + " permite hasta " + p.getMaxProductos() + " productos. " + MEJORA_TU_PLAN;
            case PERSONALIZAR -> plan + " no incluye logo ni color propios. " + MEJORA_TU_PLAN;
            case PLANTILLAS -> plan + " no incluye elegir plantilla. " + MEJORA_TU_PLAN;
            case ENVIO_INTERNACIONAL -> plan + " no incluye envios internacionales. " + MEJORA_TU_PLAN;
            case METODOS_PAGO -> plan + " solo incluye PayPal. " + MEJORA_TU_PLAN;
        };
    }
}
