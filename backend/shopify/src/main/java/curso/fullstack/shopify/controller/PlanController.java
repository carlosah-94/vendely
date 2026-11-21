package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ConfirmarPlanRequest;
import curso.fullstack.shopify.dto.MiPlanResponse;
import curso.fullstack.shopify.dto.PagarPlanRequest;
import curso.fullstack.shopify.dto.PagoPlanIniciadoResponse;
import curso.fullstack.shopify.dto.PlanResponse;
import curso.fullstack.shopify.dto.SuscripcionResponse;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import curso.fullstack.shopify.service.PlanService;
import curso.fullstack.shopify.service.SuscripcionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan de una tienda (rol ADMIN_TIENDA, regla de SecurityConfig para /api/panel/**).
 *
 * GET  /api/panel/tiendas/{id}/plan                       plan actual, uso, vencimiento y los 3 planes
 * POST /api/panel/tiendas/{id}/plan/pagar                 {idPlan} crea la orden de PayPal y devuelve el enlace
 * POST /api/panel/tiendas/{id}/plan/confirmar             {idSuscripcion} al volver de PayPal: verifica y activa
 * POST /api/panel/tiendas/{id}/plan/cancelar              {idSuscripcion} cancelo en PayPal: el plan no cambia
 * GET  /api/panel/tiendas/{id}/plan/suscripciones         historial de pagos de plan
 * GET  /api/panel/tiendas/{id}/plan/suscripciones/{idS}   un pago (para el comprobante)
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/plan")
public class PlanController {

    private final VerificadorTienda verificadorTienda;
    private final PlanService planService;
    private final PlanRepository planRepository;
    private final SuscripcionService suscripcionService;

    public PlanController(VerificadorTienda verificadorTienda, PlanService planService, PlanRepository planRepository,
                          SuscripcionService suscripcionService) {
        this.verificadorTienda = verificadorTienda;
        this.planService = planService;
        this.planRepository = planRepository;
        this.suscripcionService = suscripcionService;
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
                        .map(PlanResponse::desde).toList(),
                suscripcionService.opciones(idTienda));
    }

    @PostMapping("/pagar")
    public PagoPlanIniciadoResponse pagar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                          @Valid @RequestBody PagarPlanRequest r) {
        return suscripcionService.iniciar(yo, idTienda, r.idPlan());
    }

    @PostMapping("/confirmar")
    public SuscripcionResponse confirmar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                         @Valid @RequestBody ConfirmarPlanRequest r) {
        return suscripcionService.confirmar(yo, idTienda, r.idSuscripcion());
    }

    @PostMapping("/cancelar")
    public SuscripcionResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                        @Valid @RequestBody ConfirmarPlanRequest r) {
        return suscripcionService.cancelarPorUsuario(yo, idTienda, r.idSuscripcion());
    }

    @GetMapping("/suscripciones")
    public List<SuscripcionResponse> historial(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return suscripcionService.historial(yo, idTienda);
    }

    @GetMapping("/suscripciones/{idSuscripcion}")
    public SuscripcionResponse ver(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                   @PathVariable Long idSuscripcion) {
        return suscripcionService.ver(yo, idTienda, idSuscripcion);
    }
}
