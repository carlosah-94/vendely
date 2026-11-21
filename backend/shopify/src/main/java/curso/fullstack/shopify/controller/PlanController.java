package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.MiPlanResponse;
import curso.fullstack.shopify.dto.PlanResponse;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import curso.fullstack.shopify.service.PlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan de una tienda (rol ADMIN_TIENDA, regla de SecurityConfig para /api/panel/**).
 *
 * GET /api/panel/tiendas/{id}/plan   plan actual, uso, vencimiento y los 3 planes
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/plan")
public class PlanController {

    private final VerificadorTienda verificadorTienda;
    private final PlanService planService;
    private final PlanRepository planRepository;

    public PlanController(VerificadorTienda verificadorTienda, PlanService planService, PlanRepository planRepository) {
        this.verificadorTienda = verificadorTienda;
        this.planService = planService;
        this.planRepository = planRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public MiPlanResponse miPlan(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);     // 404 si no existe, 403 si es de otro
        return new MiPlanResponse(
                PlanResponse.desde(planService.planDe(idTienda)),
                planService.productosUsados(idTienda),
                t.getPlanVenceEl(),
                planRepository.findAllByOrderByPrecioUsdAsc().stream()
                        .filter(p -> p.getEstado() != null && p.getEstado() == 1)
                        .map(PlanResponse::desde).toList());
    }
}
