package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.EmpleadoTienda;
import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.entity.Propietario;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.EmpleadoTiendaRepository;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.repository.PropietarioRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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
 */
@Component
public class VerificadorTienda {

    private static final String NO_ES_TUYA = "Esta tienda no es tuya";

    private final PropietarioRepository propietarioRepository;
    private final TiendaRepository tiendaRepository;
    private final EmpleadoTiendaRepository empleadoRepository;
    private final PlanRepository planRepository;

    public VerificadorTienda(PropietarioRepository propietarioRepository, TiendaRepository tiendaRepository,
            EmpleadoTiendaRepository empleadoRepository, PlanRepository planRepository) {
        this.propietarioRepository = propietarioRepository;
        this.tiendaRepository = tiendaRepository;
        this.empleadoRepository = empleadoRepository;
        this.planRepository = planRepository;
    }

    /**
     * Qué puede hacer una cuenta en una tienda. esDueno = todo; si no, solo sus permisos.
     */
    public record Acceso(Tienda tienda, boolean esDueno, Set<PermisoTienda> permisos) {
        public boolean puede(PermisoTienda permiso) {
            return esDueno || permisos.contains(permiso);
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
     * empleado activo. Un empleado suspendido, o de una tienda cuyo plan ya no incluye empleados,
     * tampoco entra.
     */
    public Acceso acceso(UsuarioAutenticado yo, Long idTienda) {
        Tienda tienda = buscar(idTienda);
        if (yo.rol() == RolUsuario.ADMIN_TIENDA) {
            if (!esDuenoDe(yo, tienda)) {
                throw new ApiException(HttpStatus.FORBIDDEN, NO_ES_TUYA);
            }
            return new Acceso(tienda, true, EnumSet.allOf(PermisoTienda.class));
        }
        if (yo.rol() != RolUsuario.EMPLEADO) {
            throw new ApiException(HttpStatus.FORBIDDEN, NO_ES_TUYA);
        }
        EmpleadoTienda empleado = empleadoRepository.findByIdLoginAndIdTienda(yo.idLogin(), idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, NO_ES_TUYA));
        if (!empleado.estaActivo()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El dueño suspendió tu acceso a esta tienda");
        }
        if (!planPermiteEmpleados(tienda)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El plan de esta tienda ya no incluye empleados");
        }
        return new Acceso(tienda, false, permisosDe(empleado));
    }

    /** Los permisos marcados de un empleado. */
    public static Set<PermisoTienda> permisosDe(EmpleadoTienda e) {
        return Arrays.stream(PermisoTienda.values()).filter(e::puede)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermisoTienda.class)));
    }

    /** true si el plan de la tienda incluye al menos un empleado. */
    public boolean planPermiteEmpleados(Tienda tienda) {
        return planRepository.findById(tienda.getIdPlan())
                .map(p -> p.getMaxEmpleados() != null && p.getMaxEmpleados() > 0)
                .orElse(false);
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
