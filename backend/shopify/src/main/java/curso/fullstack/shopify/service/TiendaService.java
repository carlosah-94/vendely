package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.DisenoPro;
import curso.fullstack.shopify.dto.TiendaActualizarRequest;
import curso.fullstack.shopify.dto.TiendaCrearRequest;
import curso.fullstack.shopify.dto.TiendaPublicaResponse;
import curso.fullstack.shopify.dto.TiendaResponse;
import curso.fullstack.shopify.entity.EmpleadoTienda;
import curso.fullstack.shopify.entity.Propietario;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.DireccionRepository;
import curso.fullstack.shopify.repository.EmpleadoTiendaRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class TiendaService {

    /** Subdominios que Vendely se reserva para su propio uso. */
    private static final Set<String> RESERVADOS = Set.of(
            "www", "api", "app", "admin", "panel", "vendely", "mail", "correo",
            "soporte", "ayuda", "static", "cdn", "login", "registro", "tienda", "tiendas");

    private final TiendaRepository tiendaRepository;
    private final DireccionRepository direccionRepository;
    private final VerificadorTienda verificadorTienda;
    private final EntityManager entityManager;
    private final PlanService planService;
    private final MetodoPagoService metodoPagoService;
    private final EmpleadoTiendaRepository empleadoRepository;

    public TiendaService(TiendaRepository tiendaRepository, DireccionRepository direccionRepository,
            VerificadorTienda verificadorTienda, EntityManager entityManager, PlanService planService,
            MetodoPagoService metodoPagoService, EmpleadoTiendaRepository empleadoRepository) {
        this.metodoPagoService = metodoPagoService;
        this.empleadoRepository = empleadoRepository;
        this.planService = planService;
        this.tiendaRepository = tiendaRepository;
        this.direccionRepository = direccionRepository;
        this.verificadorTienda = verificadorTienda;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------- panel del dueño

    @Transactional
    public TiendaResponse crear(UsuarioAutenticado yo, TiendaCrearRequest r) {
        Propietario propietario = verificadorTienda.propietario(yo);
        String subdominio = r.subdominio().trim().toLowerCase(Locale.ROOT);

        if (RESERVADOS.contains(subdominio)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese subdominio está reservado por Vendely");
        }
        if (tiendaRepository.existsBySubdominio(subdominio)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ese subdominio ya está en uso");
        }
        // Solo en un país habilitado: con su lista oficial de ubicaciones, impuesto y moneda.
        // La base también lo exige (T12); aquí se valida para dar un mensaje claro.
        String idPais = (r.idPais() == null || r.idPais().isBlank()) ? "PE" : r.idPais().trim();
        DireccionRepository.PaisInfo pais = direccionRepository.pais(idPais)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "El país no existe"));
        if (pais.getEstado() == null || pais.getEstado() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Todavía no se pueden crear tiendas en " + pais.getNombre() + ". Por ahora: Perú y Chile");
        }

        Tienda t = new Tienda();
        t.setIdPropietario(propietario.getIdPropietario());
        t.setIdPais(idPais);
        t.setSubdominio(subdominio);
        copiarDatos(t, r.nombre(), r.descripcion(), r.correoContacto(), r.logoUrl(), r.colorPrincipal(),
                r.permiteInvitados());
        tiendaRepository.saveAndFlush(t);
        entityManager.refresh(t); // trae los valores que pone la base (fecha de creación)
        // Sin tarifa de envío la base rechaza cualquier venta: se crea una por defecto
        // para cada región.
        tiendaRepository.crearTarifasPorDefecto(t.getIdTienda());
        log.info("Tienda creada: idTienda={}, subdominio={}, pais={}, idPropietario={}",
                t.getIdTienda(), t.getSubdominio(), idPais, t.getIdPropietario());
        return TiendaResponse.desde(t, pais, planService.planDe(t.getIdTienda()));
    }

    /**
     * Las tiendas que la cuenta puede abrir en el panel: las suyas si es dueño; si es empleado,
     * aquellas donde está activo (y cuyo plan todavía incluye empleados); si es soporte de Vendely,
     * las que le dieron permiso y no ha vencido. Cada una dice qué puede hacer ahí, para que el
     * panel muestre solo esas opciones.
     */
    @Transactional(readOnly = true)
    public List<TiendaResponse> misTiendas(UsuarioAutenticado yo) {
        if (yo.rol() == RolUsuario.SUPERADMIN) {
            return tiendaRepository.findBySoporteHastaAfterOrderBySoporteHastaAsc(java.time.OffsetDateTime.now())
                    .stream()
                    .map(t -> responderConAcceso(verificadorTienda.acceso(yo, t.getIdTienda())))
                    .toList();
        }
        if (yo.rol() == RolUsuario.EMPLEADO) {
            return empleadoRepository.findByIdLoginAndEstadoOrderByIdTiendaAsc(yo.idLogin(), EmpleadoTienda.ACTIVO)
                    .stream()
                    .filter(e -> tiendaRepository.findById(e.getIdTienda())
                            .map(t -> verificadorTienda.dentroDelPlan(e, t)).orElse(false))
                    .map(e -> responderConAcceso(verificadorTienda.acceso(yo, e.getIdTienda())))
                    .toList();
        }
        Propietario propietario = verificadorTienda.propietario(yo);
        return tiendaRepository.findByIdPropietarioOrderByIdTiendaAsc(propietario.getIdPropietario())
                .stream().map(this::responder).toList();
    }

    @Transactional(readOnly = true)
    public TiendaResponse miTienda(UsuarioAutenticado yo, Long idTienda) {
        return responderConAcceso(verificadorTienda.acceso(yo, idTienda));
    }

    @Transactional
    public TiendaResponse actualizar(UsuarioAutenticado yo, Long idTienda, TiendaActualizarRequest r) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        // Logo y color propios solo con plan Basico o Pro. Nombre, descripcion y correo los edita cualquiera.
        if (cambia(t.getLogoUrl(), vacioANull(r.logoUrl()))
                || cambia(t.getColorPrincipal(), r.colorPrincipal() == null || r.colorPrincipal().isBlank()
                        ? null : r.colorPrincipal().trim().toUpperCase(Locale.ROOT))) {
            planService.exigir(idTienda, FuncionPlan.PERSONALIZAR);
        }
        // Moderna y Boutique solo con plan Pro; volver a la clasica siempre se puede.
        if (r.plantilla() != null && !r.plantilla().equals(t.getPlantilla())) {
            if (!"clasica".equals(r.plantilla())) {
                planService.exigir(idTienda, FuncionPlan.PLANTILLAS);
            }
            t.setPlantilla(r.plantilla());
        }
        personalizar(t, r);
        if (r.tema() != null && (cambia(t.getTema(), r.tema()) || t.getTemaAplicadoEl() == null)) {
            t.setTema(r.tema());
            t.setTemaAplicadoEl(java.time.OffsetDateTime.now());
        }
        copiarDatos(t, r.nombre(), r.descripcion(), r.correoContacto(), r.logoUrl(), r.colorPrincipal(),
                r.permiteInvitados());
        tiendaRepository.saveAndFlush(t);
        log.info("Tienda actualizada: idTienda={}", idTienda);
        // Con lo que puede quien la editó: el dueño, o soporte de Vendely (que no es el dueño).
        return yo.rol() == RolUsuario.SUPERADMIN ? responderConAcceso(verificadorTienda.acceso(yo, idTienda)) : responder(t);
    }

    /**
     * Opciones de diseño de la vitrina (V12). Cada campo null se deja como está. Como con el logo
     * y el color, el plan se exige solo si el valor CAMBIA: así una tienda que bajó de plan puede
     * seguir guardando su nombre o su correo aunque tenga guardadas opciones que ya no incluye.
     * Y volver a lo de por defecto (sin acento, sin portada, letra y botones "auto", fondo blanco)
     * siempre se puede: así cualquier plan puede aplicar el tema Esencial.
     *   - Secciones (garantías, destacado, categorías): cualquier plan.
     *   - Acento, anuncio, portada, letra y botones: PERSONALIZAR (Básico y Pro).
     *   - Fondo distinto de blanco: PLANTILLAS (Pro).
     */
    private void personalizar(Tienda t, TiendaActualizarRequest r) {
        Long id = t.getIdTienda();
        String acento = r.colorAcento() == null ? t.getColorAcento()
                : r.colorAcento().isBlank() ? null : r.colorAcento().trim().toUpperCase(Locale.ROOT);
        String anuncio = r.textoAnuncio() == null ? t.getTextoAnuncio() : vacioANull(r.textoAnuncio());
        String portada = r.imagenPortadaUrl() == null ? t.getImagenPortadaUrl() : vacioANull(r.imagenPortadaUrl());
        Boolean mostrarAnuncio = r.mostrarAnuncio() == null ? t.getMostrarAnuncio() : r.mostrarAnuncio();
        String fuente = r.fuente() == null ? t.getFuente() : r.fuente();
        String botones = r.estiloBotones() == null ? t.getEstiloBotones() : r.estiloBotones();

        if ((acento != null && cambia(t.getColorAcento(), acento))
                || (anuncio != null && cambia(t.getTextoAnuncio(), anuncio))
                || (portada != null && cambia(t.getImagenPortadaUrl(), portada))
                || (!mostrarAnuncio && !mostrarAnuncio.equals(t.getMostrarAnuncio()))
                || (!"auto".equals(fuente) && cambia(t.getFuente(), fuente))
                || (!"auto".equals(botones) && cambia(t.getEstiloBotones(), botones))) {
            planService.exigir(id, FuncionPlan.PERSONALIZAR);
        }
        if (r.fondo() != null && cambia(t.getFondo(), r.fondo()) && !"blanco".equals(r.fondo())) {
            planService.exigir(id, FuncionPlan.PLANTILLAS);
        }

        t.setColorAcento(acento);
        t.setTextoAnuncio(anuncio);
        t.setImagenPortadaUrl(portada);
        t.setMostrarAnuncio(mostrarAnuncio);
        t.setFuente(fuente);
        t.setEstiloBotones(botones);
        if (r.fondo() != null) t.setFondo(r.fondo());
        if (r.mostrarGarantias() != null) t.setMostrarGarantias(r.mostrarGarantias());
        if (r.mostrarDestacado() != null) t.setMostrarDestacado(r.mostrarDestacado());
        if (r.mostrarCategorias() != null) t.setMostrarCategorias(r.mostrarCategorias());

        if (r.disenoPro() != null) {
            DisenoPro nuevo = r.disenoPro().limpio();
            DisenoPro actual = t.getDisenoPro() == null ? DisenoPro.VACIO : t.getDisenoPro().limpio();
            if (nuevo.usaAlgo() && !nuevo.equals(actual)) {
                planService.exigir(id, FuncionPlan.PLANTILLAS);
            }
            t.setDisenoPro(nuevo.usaAlgo() ? nuevo : null);
        }
    }

    // ------------------------------------------------------------ vitrina pública

    @Transactional(readOnly = true)
    public TiendaPublicaResponse publica(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva) // una tienda suspendida no se muestra
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        // metodosPago: con qué puede pagar el cliente en esta tienda (servidor + plan).
        return TiendaPublicaResponse.desde(t, paisDe(t), planService.planDe(t.getIdTienda()),
                metodoPagoService.disponibles(t.getIdTienda()));
    }

    // ------------------------------------------------------------------ apoyo

    private TiendaResponse responder(Tienda t) {
        return TiendaResponse.desde(t, paisDe(t), planService.planDe(t.getIdTienda()));
    }

    private TiendaResponse responderConAcceso(VerificadorTienda.Acceso acceso) {
        Tienda t = acceso.tienda();
        return TiendaResponse.desde(t, paisDe(t), planService.planDe(t.getIdTienda()), acceso.esDueno(),
                acceso.soporte(), acceso.permisos());
    }

    /** Moneda, impuesto y divisiones del país de la tienda. */
    private DireccionRepository.PaisInfo paisDe(Tienda t) {
        return direccionRepository.pais(t.getIdPais())
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "El país de la tienda no existe"));
    }

    private static void copiarDatos(Tienda t, String nombre, String descripcion, String correo,
            String logoUrl, String color, Boolean permiteInvitados) {
        t.setNombre(nombre.trim());
        t.setDescripcion(vacioANull(descripcion));
        t.setCorreoContacto(correo.trim().toLowerCase(Locale.ROOT));
        t.setLogoUrl(vacioANull(logoUrl));
        t.setColorPrincipal(color == null || color.isBlank() ? null : color.trim().toUpperCase(Locale.ROOT));
        t.setPermiteInvitados(permiteInvitados == null || permiteInvitados);
    }

    private static boolean cambia(String actual, String nuevo) {
        return !java.util.Objects.equals(actual, nuevo);
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
