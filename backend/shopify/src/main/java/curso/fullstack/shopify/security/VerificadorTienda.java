package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.EmpleadoTienda;
import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.entity.Propietario;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.EmpleadoTiendaRepository;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.entity.RegistroSoporte;
import curso.fullstack.shopify.repository.PropietarioRepository;
import curso.fullstack.shopify.repository.RegistroSoporteRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Comprueba quién puede entrar a una tienda desde el panel.
 *
 * El token no dice qué tienda se está administrando (un dueño puede tener varias y un empleado
 * puede trabajar en varias), así que TODA ruta del panel que reciba un idTienda pasa por aquí:
 *
 *     verificadorTienda.delDueno(yo, idTienda);                         // solo el dueño
 *     verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.PEDIDOS); // dueño o empleado con ese permiso
 *
 * Es como el portero de un edificio: tener llave del edificio (token) no te deja entrar a
 * cualquier departamento. El dueño entra al suyo con todas las llaves; un empleado, solo a las
 * habitaciones cuya llave le dio el dueño.
 *
 * Soporte de Vendely (SUPERADMIN) entra solo a las tiendas cuyo dueño le dio permiso y mientras
 * no venza (tiendas.soporte_hasta, V16): ahí puede lo mismo que el dueño, MENOS lo que pasa por
 * soloElDueno (plan y pagos, empleados, eliminar ventas, el propio permiso de soporte). Cada
 * cambio que hace queda anotado en soporte_registro para que el dueño lo vea.
 */
@Component
public class VerificadorTienda {

    private static final String NO_ES_TUYA = "Esta tienda no es tuya";
    static final String SIN_PERMISO_SOPORTE = "El dueño de esta tienda no ha dado acceso a soporte de Vendely, o ya venció";
    static final String SOPORTE_NO_PUEDE = "Esto lo hace solo el dueño de la tienda: soporte de Vendely no puede";
    /** Para anotar una sola vez cada petición de soporte, aunque se verifique varias veces. */
    private static final String YA_ANOTADO = VerificadorTienda.class.getName() + ".anotado";

    private final PropietarioRepository propietarioRepository;
    private final TiendaRepository tiendaRepository;
    private final EmpleadoTiendaRepository empleadoRepository;
    private final PlanRepository planRepository;
    private final RegistroSoporteRepository registroSoporteRepository;

    public VerificadorTienda(PropietarioRepository propietarioRepository, TiendaRepository tiendaRepository,
            EmpleadoTiendaRepository empleadoRepository, PlanRepository planRepository,
            RegistroSoporteRepository registroSoporteRepository) {
        this.registroSoporteRepository = registroSoporteRepository;
        this.propietarioRepository = propietarioRepository;
        this.tiendaRepository = tiendaRepository;
        this.empleadoRepository = empleadoRepository;
        this.planRepository = planRepository;
    }

    /**
     * Qué puede hacer una cuenta en una tienda. esDueno = todo; soporte = soporte de Vendely con
     * permiso del dueño (todos los permisos, pero no lo que es soloElDueno); si no, solo sus permisos.
     */
    public record Acceso(Tienda tienda, boolean esDueno, boolean soporte, Set<PermisoTienda> permisos) {
        public boolean puede(PermisoTienda permiso) {
            return esDueno || soporte || permisos.contains(permiso);
        }
    }

    /** El propietario de la cuenta que inició sesión (403 si la cuenta no es de un dueño). */
    public Propietario propietario(UsuarioAutenticado yo) {
        return propietarioRepository.findByIdLogin(yo.idLogin())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tu cuenta no está registrada como dueño de tienda"));
    }

    /**
     * La tienda, solo si es de este dueño: 404 si no existe, 403 si es de otro.
     * Para lo que hace solo el dueño (plan, personalizar, envíos, empleados, eliminar ventas):
     * un empleado recibe 403 aunque trabaje en la tienda.
     */
    public Tienda delDueno(UsuarioAutenticado yo, Long idTienda) {
        Tienda tienda = buscar(idTienda);
        if (yo.rol() == RolUsuario.SUPERADMIN) {
            return conSoporte(yo, tienda);
        }
        if (yo.rol() == RolUsuario.EMPLEADO) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    trabajaAqui(yo, idTienda) ? "Solo el dueño de la tienda puede hacer esto" : NO_ES_TUYA);
        }
        if (!esDuenoDe(yo, tienda)) {
            throw new ApiException(HttpStatus.FORBIDDEN, NO_ES_TUYA);
        }
        return tienda;
    }

    /**
     * Como delDueno, pero soporte de Vendely tampoco entra: plan y pagos, empleados, eliminar
     * ventas y el propio permiso de soporte son decisiones del dueño.
     */
    public Tienda soloElDueno(UsuarioAutenticado yo, Long idTienda) {
        if (yo.rol() == RolUsuario.SUPERADMIN) {
            buscar(idTienda);   // 404 si no existe
            throw new ApiException(HttpStatus.FORBIDDEN, SOPORTE_NO_PUEDE);
        }
        return delDueno(yo, idTienda);
    }

    /**
     * La tienda, si quien pregunta es el dueño o un empleado activo con ALGUNO de estos permisos.
     * Varios permisos sirven, por ejemplo, para leer la lista de productos: la necesita quien
     * edita productos, pero también quien registra una venta o cuenta el stock.
     */
    public Tienda conPermiso(UsuarioAutenticado yo, Long idTienda, PermisoTienda... alguno) {
        Acceso acceso = acceso(yo, idTienda);
        if (Arrays.stream(alguno).noneMatch(acceso::puede)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Tu cuenta de empleado no tiene permiso de " + alguno[0].getTexto() + " en esta tienda");
        }
        return acceso.tienda();
    }

    /**
     * Qué puede hacer esta cuenta en la tienda: 404 si no existe, 403 si no es el dueño ni un
     * empleado activo. Un empleado suspendido, o que quedó fuera del límite del plan (la tienda
     * bajó de plan y sobran empleados), tampoco entra.
     */
    public Acceso acceso(UsuarioAutenticado yo, Long idTienda) {
        Tienda tienda = buscar(idTienda);
        if (yo.rol() == RolUsuario.ADMIN_TIENDA) {
            if (!esDuenoDe(yo, tienda)) {
                throw new ApiException(HttpStatus.FORBIDDEN, NO_ES_TUYA);
            }
            return new Acceso(tienda, true, false, EnumSet.allOf(PermisoTienda.class));
        }
        if (yo.rol() == RolUsuario.SUPERADMIN) {
            return new Acceso(conSoporte(yo, tienda), false, true, EnumSet.allOf(PermisoTienda.class));
        }
        if (yo.rol() != RolUsuario.EMPLEADO) {
            throw new ApiException(HttpStatus.FORBIDDEN, NO_ES_TUYA);
        }
        EmpleadoTienda empleado = empleadoRepository.findByIdLoginAndIdTienda(yo.idLogin(), idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, NO_ES_TUYA));
        if (!empleado.estaActivo()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El dueño suspendió tu acceso a esta tienda");
        }
        int maximo = maxEmpleados(tienda);
        if (maximo == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El plan de esta tienda ya no incluye empleados");
        }
        if (!dentroDelPlan(empleado, maximo)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El plan de esta tienda ahora permite " + maximo
                    + (maximo == 1 ? " empleado" : " empleados") + " y tu cuenta quedó fuera del límite. "
                    + "Pide al dueño que suspenda a otro empleado o que mejore el plan");
        }
        return new Acceso(tienda, false, false, permisosDe(empleado));
    }

    /**
     * true si esta cuenta puede ver las finanzas de la tienda (costos y ganancias): el dueño, o soporte
     * de Vendely con el permiso del dueño. Un empleado nunca, aunque tenga permiso de productos o pedidos.
     * Se llama después de conPermiso/acceso, que ya comprobaron que la cuenta puede entrar a la tienda.
     */
    public boolean veFinanzas(UsuarioAutenticado yo, Long idTienda) {
        Acceso acceso = acceso(yo, idTienda);
        return acceso.esDueno() || acceso.soporte();
    }

    /** Los permisos marcados de un empleado. */
    public static Set<PermisoTienda> permisosDe(EmpleadoTienda e) {
        return Arrays.stream(PermisoTienda.values()).filter(e::puede)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermisoTienda.class)));
    }

    /** true si el plan de la tienda incluye al menos un empleado. */
    public boolean planPermiteEmpleados(Tienda tienda) {
        return maxEmpleados(tienda) > 0;
    }

    /**
     * true si este empleado activo puede entrar con el plan actual de la tienda. Si la tienda bajó
     * de plan y tiene más empleados activos que los que permite, entran los más antiguos; los demás
     * esperan a que el dueño suspenda a otros o mejore el plan. No se borra ni se suspende a nadie.
     */
    public boolean dentroDelPlan(EmpleadoTienda empleado, Tienda tienda) {
        return empleado.estaActivo() && dentroDelPlan(empleado, maxEmpleados(tienda));
    }

    private boolean dentroDelPlan(EmpleadoTienda empleado, int maximo) {
        return maximo > 0 && empleadoRepository.activosAntes(empleado.getIdTienda(), empleado.getIdLogin()) < maximo;
    }

    /** Cuántos empleados permite el plan de la tienda (0 si el plan no existe o no incluye). */
    private int maxEmpleados(Tienda tienda) {
        return planRepository.findById(tienda.getIdPlan())
                .map(p -> p.getMaxEmpleados() == null ? 0 : p.getMaxEmpleados().intValue())
                .orElse(0);
    }

    // ------------------------------------------------------------------ soporte de Vendely

    /** La tienda, si su dueño le dio permiso a soporte y no venció. Anota los cambios que hace. */
    private Tienda conSoporte(UsuarioAutenticado yo, Tienda tienda) {
        if (!tienda.soporteActivo()) {
            throw new ApiException(HttpStatus.FORBIDDEN, SIN_PERMISO_SOPORTE);
        }
        anotarCambio(yo, tienda);
        return tienda;
    }

    /**
     * Anota en soporte_registro la petición que está modificando algo (no las lecturas GET).
     * Si la petición falla, su transacción se deshace y la anotación también: queda solo lo que
     * de verdad cambió. Fuera de una petición HTTP (WebSocket) no hay nada que anotar.
     */
    private void anotarCambio(UsuarioAutenticado yo, Tienda tienda) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return;
        }
        HttpServletRequest peticion = atributos.getRequest();
        String metodo = peticion.getMethod();
        // Responder en la conversación con el dueño (V17) no cambia nada de la tienda.
        if ("GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo)
                || peticion.getRequestURI().endsWith("/soporte/mensajes")
                || peticion.getAttribute(YA_ANOTADO) != null) {
            return;
        }
        peticion.setAttribute(YA_ANOTADO, Boolean.TRUE);
        RegistroSoporte r = RegistroSoporte.de(tienda.getIdTienda(), yo.idLogin(), RegistroSoporte.CAMBIO);
        r.setMetodo(metodo);
        String ruta = peticion.getRequestURI();
        r.setRuta(ruta.length() > 300 ? ruta.substring(0, 300) : ruta);
        registroSoporteRepository.save(r);
    }

    // ------------------------------------------------------------------ apoyo

    private Tienda buscar(Long idTienda) {
        return tiendaRepository.findById(idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
    }

    /** 403 "no registrada como dueño" si la cuenta no tiene fila en propietarios. */
    private boolean esDuenoDe(UsuarioAutenticado yo, Tienda tienda) {
        return tienda.getIdPropietario().equals(propietario(yo).getIdPropietario());
    }

    private boolean trabajaAqui(UsuarioAutenticado yo, Long idTienda) {
        return empleadoRepository.findByIdLoginAndIdTienda(yo.idLogin(), idTienda)
                .filter(EmpleadoTienda::estaActivo).isPresent();
    }
}
