package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.TiendaActualizarRequest;
import curso.fullstack.shopify.dto.TiendaCrearRequest;
import curso.fullstack.shopify.dto.TiendaPublicaResponse;
import curso.fullstack.shopify.dto.TiendaResponse;
import curso.fullstack.shopify.entity.Propietario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.DireccionRepository;
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

    public TiendaService(TiendaRepository tiendaRepository, DireccionRepository direccionRepository,
            VerificadorTienda verificadorTienda, EntityManager entityManager) {
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
        return TiendaResponse.desde(t, pais);
    }

    @Transactional(readOnly = true)
    public List<TiendaResponse> misTiendas(UsuarioAutenticado yo) {
        Propietario propietario = verificadorTienda.propietario(yo);
        return tiendaRepository.findByIdPropietarioOrderByIdTiendaAsc(propietario.getIdPropietario())
                .stream().map(this::responder).toList();
    }

    @Transactional(readOnly = true)
    public TiendaResponse miTienda(UsuarioAutenticado yo, Long idTienda) {
        return responder(verificadorTienda.delDueno(yo, idTienda));
    }

    @Transactional
    public TiendaResponse actualizar(UsuarioAutenticado yo, Long idTienda, TiendaActualizarRequest r) {
        Tienda t = verificadorTienda.delDueno(yo, idTienda);
        copiarDatos(t, r.nombre(), r.descripcion(), r.correoContacto(), r.logoUrl(), r.colorPrincipal(),
                r.permiteInvitados());
        tiendaRepository.saveAndFlush(t);
        log.info("Tienda actualizada: idTienda={}", idTienda);
        return responder(t);
    }

    // ------------------------------------------------------------ vitrina pública

    @Transactional(readOnly = true)
    public TiendaPublicaResponse publica(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva) // una tienda suspendida no se muestra
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        return TiendaPublicaResponse.desde(t, paisDe(t));
    }

    // ------------------------------------------------------------------ apoyo

    private TiendaResponse responder(Tienda t) {
        return TiendaResponse.desde(t, paisDe(t));
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

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
