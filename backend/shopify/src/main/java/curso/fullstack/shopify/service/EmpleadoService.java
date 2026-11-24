package curso.fullstack.shopify.service;

import java.util.List;
import java.util.ArrayList;
import curso.fullstack.shopify.dto.EmpleadoCrearRequest;
import curso.fullstack.shopify.dto.EmpleadoEditarRequest;
import curso.fullstack.shopify.dto.EmpleadoResponse;
import curso.fullstack.shopify.dto.EmpleadosResponse;
import curso.fullstack.shopify.entity.EmpleadoTienda;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.EmpleadoTiendaRepository;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empleados de la tienda (cuentas de personal, como en Shopify).
 *
 * Solo el dueño los agrega, cambia o quita (verificadorTienda.delDueno). Cada empleado tiene una
 * cuenta del panel con rol EMPLEADO y, por cada tienda donde trabaja, una fila en
 * empleados_tienda con sus permisos. Quitarlo de una tienda no borra su cuenta: puede seguir
 * trabajando en otra, o volver a esta.
 *
 * El plan pone el límite: Gratis 0, Básico 2, Pro 15 (planes.max_empleados).
 */
@Slf4j
@Service
public class EmpleadoService {

    private final EmpleadoTiendaRepository empleadoRepository;
    private final LoginRepository loginRepository;
    private final VerificadorTienda verificadorTienda;
    private final PlanService planService;
    private final PasswordEncoder passwordEncoder;

    public EmpleadoService(EmpleadoTiendaRepository empleadoRepository, LoginRepository loginRepository,
            VerificadorTienda verificadorTienda, PlanService planService, PasswordEncoder passwordEncoder) {
        this.empleadoRepository = empleadoRepository;
        this.loginRepository = loginRepository;
        this.verificadorTienda = verificadorTienda;
        this.planService = planService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public EmpleadosResponse listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.delDueno(yo, idTienda);
        Plan plan = planService.planDe(idTienda);
        // La lista viene ordenada por antigüedad: los primeros activos hasta el máximo del plan
        // pueden entrar; si la tienda bajó de plan, los que sobran quedan marcados (no se borran).
        int maximo = plan.getMaxEmpleados() == null ? 0 : plan.getMaxEmpleados();
        int activosVistos = 0;
        List<EmpleadoResponse> empleados = new ArrayList<>();
        for (EmpleadoTiendaRepository.Fila f : empleadoRepository.filasDeTienda(idTienda)) {
            boolean activo = f.getEstado() != null && f.getEstado() == 1;
            empleados.add(EmpleadoResponse.desde(f, !activo || activosVistos < maximo));
            if (activo) {
                activosVistos++;
            }
        }
        return new EmpleadosResponse(plan.getNombre(), maximo, activosVistos, empleados);
    }

    /**
     * Agrega un empleado. Si el correo ya es de un empleado de otra tienda, se usa esa cuenta;
     * si no existe, se crea con la contraseña que puso el dueño.
     */
    @Transactional
    public EmpleadoResponse crear(UsuarioAutenticado yo, Long idTienda, EmpleadoCrearRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        exigirAlgunPermiso(r.puedePedidos(), r.puedeProductos(), r.puedeInventario(), r.puedeClientes());
        planService.exigir(idTienda, FuncionPlan.AGREGAR_EMPLEADO);

        String correo = r.correo().trim().toLowerCase(Locale.ROOT);
        Optional<Login> existente = loginRepository.findByCorreoAndIdTiendaIsNull(correo);
        Login cuenta;
        if (existente.isPresent()) {
            cuenta = existente.get();
            if (cuenta.getRol() != RolUsuario.EMPLEADO) {
                // Un dueño o un administrador de Vendely no puede ser, además, empleado con la misma cuenta.
                throw new ApiException(HttpStatus.CONFLICT,
                        "Ese correo ya es de una cuenta de dueño o de administrador. Usa otro correo para el empleado");
            }
            if (empleadoRepository.findByIdLoginAndIdTienda(cuenta.getIdLogin(), idTienda).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "Esa persona ya es empleado de esta tienda");
            }
        } else {
            if (r.contrasena() == null || r.contrasena().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Escribe una contraseña inicial: el empleado la usará para entrar y luego la cambia");
            }
            cuenta = nuevaCuenta(correo, r);
        }

        EmpleadoTienda e = new EmpleadoTienda();
        e.setIdLogin(cuenta.getIdLogin());
        e.setIdTienda(idTienda);
        copiarPermisos(e, r.puedePedidos(), r.puedeProductos(), r.puedeInventario(), r.puedeClientes());
        empleadoRepository.saveAndFlush(e);
        log.info("Empleado agregado: tienda={}, idLogin={}, cuentaNueva={}, permisos={}",
                idTienda, cuenta.getIdLogin(), existente.isEmpty(), VerificadorTienda.permisosDe(e));
        return buscarFila(idTienda, cuenta.getIdLogin());
    }

    /** Cambia los permisos o suspende / reactiva al empleado en esta tienda. */
    @Transactional
    public EmpleadoResponse editar(UsuarioAutenticado yo, Long idTienda, Long idLogin, EmpleadoEditarRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        EmpleadoTienda e = buscar(idTienda, idLogin);
        exigirAlgunPermiso(r.puedePedidos(), r.puedeProductos(), r.puedeInventario(), r.puedeClientes());
        boolean reactiva = !e.estaActivo() && Boolean.TRUE.equals(r.activo());
        if (reactiva) {
            // Volver a activarlo ocupa un lugar del plan, igual que agregarlo.
            planService.exigir(idTienda, FuncionPlan.AGREGAR_EMPLEADO);
        }
        copiarPermisos(e, r.puedePedidos(), r.puedeProductos(), r.puedeInventario(), r.puedeClientes());
        e.setEstado(Boolean.TRUE.equals(r.activo()) ? EmpleadoTienda.ACTIVO : EmpleadoTienda.SUSPENDIDO);
        empleadoRepository.saveAndFlush(e);
        log.info("Empleado actualizado: tienda={}, idLogin={}, activo={}, permisos={}",
                idTienda, idLogin, e.estaActivo(), VerificadorTienda.permisosDe(e));
        return buscarFila(idTienda, idLogin);
    }

    /** Lo quita de esta tienda. Su cuenta sigue existiendo (puede trabajar en otra tienda). */
    @Transactional
    public void quitar(UsuarioAutenticado yo, Long idTienda, Long idLogin) {
        verificadorTienda.delDueno(yo, idTienda);
        empleadoRepository.delete(buscar(idTienda, idLogin));
        empleadoRepository.flush();
        log.info("Empleado quitado: tienda={}, idLogin={}", idTienda, idLogin);
    }

    // ------------------------------------------------------------------ apoyo

    private Login nuevaCuenta(String correo, EmpleadoCrearRequest r) {
        Login l = new Login();
        l.setRol(RolUsuario.EMPLEADO);
        l.setIdTienda(null);   // cuenta del panel, como la del dueño
        l.setUsuario(usuarioLibre(correo));
        l.setCorreo(correo);
        l.setContrasenaHash(passwordEncoder.encode(r.contrasena()));
        l.setNombres(r.nombres().trim());
        l.setPrimerApellido(r.primerApellido().trim());
        l.setAceptaMarketing(false);
        return loginRepository.saveAndFlush(l);
    }

    /**
     * El nombre de usuario sale de la parte del correo antes de la @ (ana.torres@... -> ana.torres).
     * Si ya existe entre las cuentas del panel, se le agrega un número: ana.torres2, ana.torres3...
     */
    private String usuarioLibre(String correo) {
        String base = correo.substring(0, correo.indexOf('@')).replaceAll("[^a-z0-9._-]", "");
        if (base.length() < 3) {
            base = "empleado";
        }
        base = base.length() > 45 ? base.substring(0, 45) : base;   // la columna admite 50 y el número va al final
        String candidato = base;
        for (int n = 2; loginRepository.existsByUsuarioAndIdTiendaIsNull(candidato); n++) {
            candidato = base + n;
        }
        return candidato;
    }

    private EmpleadoTienda buscar(Long idTienda, Long idLogin) {
        return empleadoRepository.findByIdLoginAndIdTienda(idLogin, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ese empleado no trabaja en esta tienda"));
    }

    private EmpleadoResponse buscarFila(Long idTienda, Long idLogin) {
        return empleadoRepository.filasDeTienda(idTienda).stream()
                .filter(f -> f.getIdLogin().equals(idLogin))
                .findFirst().map(EmpleadoResponse::desde)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ese empleado no trabaja en esta tienda"));
    }

    private static void exigirAlgunPermiso(Boolean... permisos) {
        for (Boolean p : permisos) {
            if (Boolean.TRUE.equals(p)) {
                return;
            }
        }
        // La base también lo exige (CHECK ck_empleados_permiso); aquí se avisa con un mensaje claro.
        throw new ApiException(HttpStatus.BAD_REQUEST, "Marca al menos un permiso para el empleado");
    }

    private static void copiarPermisos(EmpleadoTienda e, Boolean pedidos, Boolean productos, Boolean inventario,
            Boolean clientes) {
        e.setPuedePedidos(Boolean.TRUE.equals(pedidos));
        e.setPuedeProductos(Boolean.TRUE.equals(productos));
        e.setPuedeInventario(Boolean.TRUE.equals(inventario));
        e.setPuedeClientes(Boolean.TRUE.equals(clientes));
    }
}
