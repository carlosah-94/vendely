package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.AdminCuentaResponse;
import curso.fullstack.shopify.dto.AdminResumenResponse;
import curso.fullstack.shopify.dto.AdminTiendaResponse;
import curso.fullstack.shopify.dto.BandejaSoporteResponse;
import curso.fullstack.shopify.dto.CambioEstadoRequest;
import curso.fullstack.shopify.dto.PlanEditarRequest;
import curso.fullstack.shopify.dto.PlanResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.AdminService;
import curso.fullstack.shopify.service.SoporteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import curso.fullstack.shopify.service.DobleFactorService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de Vendely (rol SUPERADMIN, regla de SecurityConfig para /api/admin/**).
 *
 * GET   /api/admin/resumen                  números generales de la plataforma
 * GET   /api/admin/tiendas                  todas las tiendas, con su dueño y sus números
 * PATCH /api/admin/tiendas/{id}/estado      suspender o reactivar una tienda  {"activa": false}
 * GET   /api/admin/cuentas                  todas las cuentas (dueños, clientes y superadmins)
 * PATCH /api/admin/cuentas/{id}/estado      desactivar o activar una cuenta   {"activa": false}
 * DELETE /api/admin/cuentas/{id}/doble-factor quitar la doble autenticacion (telefono perdido)
 * GET   /api/admin/planes                   los planes de Vendely
 * PUT   /api/admin/planes/{id}              cambiar precio, limites y beneficios de un plan
 * GET   /api/admin/soporte/bandeja          tiendas con acceso a soporte: mensajes sin responder y el último
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final DobleFactorService dobleFactorService;
    private final SoporteService soporteService;

    public AdminController(AdminService adminService, DobleFactorService dobleFactorService,
                           SoporteService soporteService) {
        this.adminService = adminService;
        this.dobleFactorService = dobleFactorService;
        this.soporteService = soporteService;
    }

    @GetMapping("/soporte/bandeja")
    public List<BandejaSoporteResponse> bandejaSoporte() {
        return soporteService.bandeja();
    }

    @GetMapping("/resumen")
    public AdminResumenResponse resumen() {
        return adminService.resumen();
    }

    @GetMapping("/tiendas")
    public List<AdminTiendaResponse> tiendas() {
        return adminService.tiendas();
    }

    /** 204 No Content: el cambio se hizo y no hay nada que devolver. */
    @PatchMapping("/tiendas/{idTienda}/estado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarEstadoTienda(@AuthenticationPrincipal UsuarioAutenticado yo,
                                    @PathVariable Long idTienda,
                                    @Valid @RequestBody CambioEstadoRequest request) {
        adminService.cambiarEstadoTienda(yo, idTienda, request.activa());
    }

    @GetMapping("/planes")
    public List<PlanResponse> planes() {
        return adminService.planes();
    }

    @PutMapping("/planes/{idPlan}")
    public PlanResponse editarPlan(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Short idPlan,
                                   @Valid @RequestBody PlanEditarRequest request) {
        return adminService.editarPlan(yo, idPlan, request);
    }

    @GetMapping("/cuentas")
    public List<AdminCuentaResponse> cuentas() {
        return adminService.cuentas();
    }

    @PatchMapping("/cuentas/{idLogin}/estado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarEstadoCuenta(@AuthenticationPrincipal UsuarioAutenticado yo,
                                    @PathVariable Long idLogin,
                                    @Valid @RequestBody CambioEstadoRequest request) {
        adminService.cambiarEstadoCuenta(yo, idLogin, request.activa());
    }

    /**
     * Quita la doble autenticacion de una cuenta: es la salida cuando alguien pierde el
     * telefono. /api/admin/** ya exige SUPERADMIN (SecurityConfig). Queda en el log quien lo hizo.
     */
    @DeleteMapping("/cuentas/{idLogin}/doble-factor")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarDobleFactor(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idLogin) {
        dobleFactorService.quitarComoAdmin(yo, idLogin);
    }
}
