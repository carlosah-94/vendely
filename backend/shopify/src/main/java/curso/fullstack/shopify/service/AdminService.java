package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.AdminCuentaResponse;
import curso.fullstack.shopify.dto.AdminResumenResponse;
import curso.fullstack.shopify.dto.AdminTiendaResponse;
import curso.fullstack.shopify.dto.PlanEditarRequest;
import curso.fullstack.shopify.dto.PlanResponse;
import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.AdminRepository;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panel del superadmin: ve todas las tiendas y cuentas de Vendely y puede suspenderlas.
 * Solo llega aquí el rol SUPERADMIN (SecurityConfig protege /api/admin/**).
 *
 * Suspender no borra nada, es como bajar la persiana de un local:
 * - Tienda suspendida: deja de verse en internet y no recibe pedidos (ya lo revisan
 *   ProductoService, TiendaService y PedidoService con estaActiva()).
 * - Cuenta desactivada: su token deja de servir en la siguiente petición (JwtAuthFilter)
 *   y no puede volver a iniciar sesión (CuentaUserDetails.isEnabled()).
 */
@Slf4j
@Service
public class AdminService {

    private static final short ACTIVO = 1;
    private static final short INACTIVO = 0;

    private final AdminRepository adminRepository;
    private final TiendaRepository tiendaRepository;
    private final LoginRepository loginRepository;
    private final PlanRepository planRepository;

    public AdminService(AdminRepository adminRepository, TiendaRepository tiendaRepository,
                        LoginRepository loginRepository, PlanRepository planRepository) {
        this.planRepository = planRepository;
        this.adminRepository = adminRepository;
        this.tiendaRepository = tiendaRepository;
        this.loginRepository = loginRepository;
    }

    @Transactional(readOnly = true)
    public AdminResumenResponse resumen() {
        return AdminResumenResponse.desde(adminRepository.resumen(), adminRepository.ventasPorMoneda(),
                adminRepository.ingresoPlanesDelMes(), adminRepository.comisionesDelMes(), adminRepository.tiendasPorPlan());
    }

    // ------------------------------------------------------------------ planes

    @Transactional(readOnly = true)
    public List<PlanResponse> planes() {
        return planRepository.findAllByOrderByPrecioUsdAsc().stream().map(PlanResponse::desde).toList();
    }

    /**
     * Cambia precio, limites y beneficios de un plan. Lo ya pagado no cambia: cada suscripcion
     * guarda el monto que se pago (monto_usd). El nuevo precio se cobra desde el proximo pago.
     */
    @Transactional
    public PlanResponse editarPlan(UsuarioAutenticado yo, Short idPlan, PlanEditarRequest r) {
        Plan p = planRepository.findById(idPlan)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El plan no existe"));
        boolean esGratis = idPlan == Plan.GRATIS;
        if (esGratis && r.precioUsd().signum() != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El plan Gratis siempre cuesta 0");
        }
        if (!esGratis && r.precioUsd().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Un plan pagado debe costar mas de 0");
        }
        p.setPrecioUsd(r.precioUsd());
        p.setMaxProductos(r.maxProductos());
        p.setComision(r.comision());
        p.setPermitePersonalizar(r.permitePersonalizar());
        p.setPermitePlantillas(r.permitePlantillas());
        p.setPermiteEnvioInternacional(r.permiteEnvioInternacional());
        p.setPermiteMasMetodosPago(r.permiteMasMetodosPago());
        planRepository.saveAndFlush(p);
        log.info("Superadmin {} cambio el plan {}: {} USD, max {} productos, comision {} %",
                yo.idLogin(), p.getNombre(), p.getPrecioUsd(), p.getMaxProductos(), p.getComision());
        return PlanResponse.desde(p);
    }

    @Transactional(readOnly = true)
    public List<AdminTiendaResponse> tiendas() {
        return adminRepository.tiendas().stream().map(AdminTiendaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCuentaResponse> cuentas() {
        return adminRepository.cuentas().stream().map(AdminCuentaResponse::desde).toList();
    }

    /** Suspende (activa = false) o reactiva (activa = true) una tienda. */
    @Transactional
    public void cambiarEstadoTienda(UsuarioAutenticado yo, Long idTienda, boolean activa) {
        Tienda t = tiendaRepository.findById(idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        t.setEstado(activa ? ACTIVO : INACTIVO);
        tiendaRepository.saveAndFlush(t);
        log.info("Superadmin {} {} la tienda {}", yo.idLogin(), activa ? "reactivó" : "suspendió", idTienda);
    }

    /**
     * Activa o desactiva una cuenta. Las cuentas de superadmin no se tocan desde aquí:
     * así nadie puede dejar a Vendely sin administradores por error (ni desactivarse a sí mismo).
     */
    @Transactional
    public void cambiarEstadoCuenta(UsuarioAutenticado yo, Long idLogin, boolean activa) {
        Login l = loginRepository.findById(idLogin)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La cuenta no existe"));
        if (l.getRol() == RolUsuario.SUPERADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, "Las cuentas de administrador de Vendely no se pueden desactivar");
        }
        l.setEstado(activa ? ACTIVO : INACTIVO);
        loginRepository.saveAndFlush(l);
        log.info("Superadmin {} {} la cuenta {} ({})", yo.idLogin(), activa ? "activó" : "desactivó",
                idLogin, l.getRol());
    }
}
