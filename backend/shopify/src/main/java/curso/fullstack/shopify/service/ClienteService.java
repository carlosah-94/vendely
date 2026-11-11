package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.ClienteActualizarRequest;
import curso.fullstack.shopify.dto.ClienteCrearRequest;
import curso.fullstack.shopify.dto.ClienteResponse;
import curso.fullstack.shopify.dto.ClienteResponse.DireccionResponse;
import curso.fullstack.shopify.dto.DireccionRequest;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.entity.Direccion;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.DireccionRepository;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.PedidoRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de clientes desde el panel del dueño (issue #9 · RF1 · rúbrica 1.1).
 *
 * Un cliente es una fila de "login" con rol CLIENTE (3) y la tienda donde está registrado.
 * Todas las operaciones empiezan con verificadorTienda.delDueno(...): así un dueño solo
 * gestiona los clientes de SUS tiendas (403 si la tienda es de otro, 404 si no existe).
 */
@Slf4j
@Service
public class ClienteService {

    private static final short CLIENTE = RolUsuario.CLIENTE.getId();
    private static final short ACTIVA = 1;

    private final VerificadorTienda verificadorTienda;
    private final LoginRepository loginRepository;
    private final DireccionRepository direccionRepository;
    private final PedidoRepository pedidoRepository;
    private final TiendaRepository tiendaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public ClienteService(VerificadorTienda verificadorTienda, LoginRepository loginRepository,
                          DireccionRepository direccionRepository, PedidoRepository pedidoRepository,
                          TiendaRepository tiendaRepository,
                          PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.tiendaRepository = tiendaRepository;
        this.verificadorTienda = verificadorTienda;
        this.loginRepository = loginRepository;
        this.direccionRepository = direccionRepository;
        this.pedidoRepository = pedidoRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------ consultar

    /** Lista de clientes de la tienda. Sin direcciones, para que la respuesta sea liviana. */
    @Transactional(readOnly = true)
    public List<ClienteResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.delDueno(yo, idTienda);
        return loginRepository.findByIdTiendaAndIdRolOrderByFechaCreacionDesc(idTienda, CLIENTE).stream()
                .map(l -> ClienteResponse.desde(l, null))
                .toList();
    }

    /** Ficha de un cliente con su dirección. */
    @Transactional(readOnly = true)
    public ClienteResponse ver(UsuarioAutenticado yo, Long idTienda, Long idCliente) {
        verificadorTienda.delDueno(yo, idTienda);
        Login cliente = buscarEnTienda(idCliente, idTienda);
        return ClienteResponse.desde(cliente, direccionDe(cliente.getIdLogin()));
    }

    // ------------------------------------------------------------------ crear

    /**
     * Crea la cuenta del cliente Y su dirección. Van en UNA transacción: si la dirección falla
     * (por ejemplo, distrito inválido), tampoco queda creada la cuenta. Nunca un cliente a medias.
     */
    @Transactional
    public ClienteResponse crear(UsuarioAutenticado yo, Long idTienda, ClienteCrearRequest r) {
        Tienda tienda = verificadorTienda.delDueno(yo, idTienda);
        // La base exige el correo en minúsculas (CHECK ck_login_correo); se normaliza aquí.
        String correo = normalizarCorreo(r.correo());

        // Correo único EN LA TIENDA: el mismo correo sí puede estar en otra tienda (cuentas por tienda).
        if (loginRepository.existsByCorreoAndIdTienda(correo, idTienda)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay un cliente con ese correo en esta tienda");
        }
        // Se valida la ubicación ANTES de crear nada, para responder 400 claro (la base también lo revisa).
        Direccion d = new Direccion();
        ubicar(d, r.direccion(), tienda.getIdPais());

        String usuario = elegirUsuario(r.usuario(), correo, idTienda);

        Login l = new Login();
        l.setRol(RolUsuario.CLIENTE);
        l.setIdTienda(idTienda);
        l.setUsuario(usuario);
        l.setCorreo(correo);
        // Nunca se guarda la contraseña real: BCrypt genera un hash con "sal" distinta cada vez,
        // así dos clientes con la misma contraseña tienen hashes distintos (criterio del issue).
        l.setContrasenaHash(passwordEncoder.encode(r.contrasena()));
        l.setNombres(r.nombres().trim());
        l.setPrimerApellido(r.primerApellido().trim());
        l.setSegundoApellido(vacioANull(r.segundoApellido()));
        l.setTelefono(r.telefono().trim());
        // saveAndFlush: se necesita el id_login ya generado para guardar la dirección.
        loginRepository.saveAndFlush(l);
        // refresh: lee lo que puso la base (fecha_creacion = DEFAULT now()); si no, saldría null.
        entityManager.refresh(l);

        d.setIdLogin(l.getIdLogin());
        // Destinatario y teléfono se toman del cliente para no pedirlos dos veces en el formulario.
        d.setDestinatario(l.getNombres() + " " + l.getPrimerApellido());
        d.setTelefono(l.getTelefono());
        d.setDireccion(r.direccion().direccion().trim());
        d.setReferencia(vacioANull(r.direccion().referencia()));
        d.setPredeterminada(true);                // es su primera dirección
        direccionRepository.saveAndFlush(d);

        log.info("Cliente creado desde el panel: idLogin={}, tienda={}", l.getIdLogin(), idTienda);
        return ClienteResponse.desde(l, aRespuesta(d));
    }

    // ------------------------------------------------------------------ editar

    @Transactional
    public ClienteResponse actualizar(UsuarioAutenticado yo, Long idTienda, Long idCliente, ClienteActualizarRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        Login l = buscarEnTienda(idCliente, idTienda);
        String correo = normalizarCorreo(r.correo());

        // El correo puede quedar igual; lo que no puede es repetir el de OTRO cliente de la tienda.
        if (loginRepository.existsByCorreoAndIdTiendaAndIdLoginNot(correo, idTienda, idCliente)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay otro cliente con ese correo en esta tienda");
        }

        l.setNombres(r.nombres().trim());
        l.setPrimerApellido(r.primerApellido().trim());
        l.setSegundoApellido(vacioANull(r.segundoApellido()));
        l.setCorreo(correo);
        l.setTelefono(r.telefono().trim());

        // Si viene una contraseña nueva, se hashea otra vez (nunca se guarda en texto plano) y se
        // marca la fecha del cambio: los tokens emitidos antes dejan de servir (lo revisa el filtro JWT).
        if (r.contrasena() != null) {
            l.setContrasenaHash(passwordEncoder.encode(r.contrasena()));
            l.setContrasenaCambiadaEn(OffsetDateTime.now());
        }
        loginRepository.saveAndFlush(l);

        log.info("Cliente actualizado desde el panel: idLogin={}, tienda={}, cambioContrasena={}",
                idCliente, idTienda, r.contrasena() != null);
        return ClienteResponse.desde(l, direccionDe(l.getIdLogin()));
    }

    // ------------------------------------------------------------------ eliminar

    /**
     * Elimina un cliente. Si tiene ventas, la FK pedidos → login lo impide: en vez de dejar que la
     * base falle con un error genérico, se comprueba antes y se responde 409 con un mensaje claro.
     * Sin ventas, se borran primero su carrito y sus direcciones (esas FK tampoco tienen cascade)
     * y luego la cuenta. Todo en una transacción: o se borra todo, o no se borra nada.
     */
    @Transactional
    public void eliminar(UsuarioAutenticado yo, Long idTienda, Long idCliente) {
        verificadorTienda.delDueno(yo, idTienda);
        Login l = buscarEnTienda(idCliente, idTienda);

        if (pedidoRepository.existsByIdLogin(idCliente)) {
            log.info("Eliminación de cliente rechazada, tiene ventas: idLogin={}, tienda={}", idCliente, idTienda);
            throw new ApiException(HttpStatus.CONFLICT, "El cliente tiene ventas asociadas");
        }

        loginRepository.borrarCarritoDeCliente(idCliente);
        direccionRepository.borrarDeCliente(idCliente);
        loginRepository.delete(l);
        // flush: el DELETE se envía ya; si la base lo rechazara, el error sale aquí y se deshace todo.
        loginRepository.flush();

        log.info("Cliente eliminado desde el panel: idLogin={}, tienda={}", idCliente, idTienda);
    }

    // ------------------------------------------------------------------ direcciones del propio cliente (tienda)

    /** Mis direcciones activas. El idLogin sale del token, nunca de la URL ni del JSON. */
    @Transactional(readOnly = true)
    public List<DireccionResponse> misDirecciones(UsuarioAutenticado yo) {
        return direccionRepository
                .findByIdLoginAndEstadoOrderByPredeterminadaDescIdDireccionDesc(yo.idLogin(), ACTIVA)
                .stream().map(this::aRespuesta).toList();
    }

    /**
     * Crea una dirección para el cliente que inició sesión: en el país de la tienda (distrito de la
     * lista oficial) o en otro país (ciudad escrita). Que la tienda envíe a ese país se revisa
     * al cotizar el envío y al crear el pedido, no aquí: el cliente puede guardar su dirección igual.
     */
    @Transactional
    public DireccionResponse crearMiDireccion(UsuarioAutenticado yo, DireccionRequest r) {
        Login cliente = loginRepository.findByIdLoginAndIdTiendaAndIdRol(yo.idLogin(), yo.idTienda(), CLIENTE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El cliente no existe en esta tienda"));
        Tienda tienda = tiendaRepository.findById(yo.idTienda())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));

        Direccion d = new Direccion();
        ubicar(d, r, tienda.getIdPais());
        // El teléfono de la dirección es obligatorio en la base: el enviado, o si no el de la cuenta.
        String telefono = vacioANull(r.telefono());
        if (telefono == null) {
            telefono = vacioANull(cliente.getTelefono());
        }
        if (telefono == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Indica un teléfono de contacto para el envío");
        }

        d.setIdLogin(cliente.getIdLogin());
        d.setDestinatario(cliente.getNombres() + " " + cliente.getPrimerApellido());
        d.setTelefono(telefono);
        d.setDireccion(r.direccion().trim());
        d.setReferencia(vacioANull(r.referencia()));
        // Solo puede haber una predeterminada activa por cliente (índice único): la primera lo es.
        d.setPredeterminada(direccionRepository
                .findByIdLoginAndEstadoOrderByPredeterminadaDescIdDireccionDesc(cliente.getIdLogin(), ACTIVA).isEmpty());
        direccionRepository.saveAndFlush(d);

        log.info("Dirección creada por el cliente: idLogin={}, tienda={}, idDireccion={}",
                cliente.getIdLogin(), yo.idTienda(), d.getIdDireccion());
        return aRespuesta(d);
    }

    // ------------------------------------------------------------------ apoyo

    /** El cliente solo si es de ESA tienda y tiene rol CLIENTE; si no, 404 (no se revela que existe). */
    private Login buscarEnTienda(Long idCliente, Long idTienda) {
        return loginRepository.findByIdLoginAndIdTiendaAndIdRol(idCliente, idTienda, CLIENTE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El cliente no existe en esta tienda"));
    }

    private DireccionResponse direccionDe(Long idLogin) {
        return direccionRepository.findFirstByIdLoginAndEstadoOrderByPredeterminadaDescIdDireccionDesc(idLogin, ACTIVA)
                .map(this::aRespuesta)
                .orElse(null);
    }

    private DireccionResponse aRespuesta(Direccion d) {
        String distrito = d.getIdDivision() == null ? null : direccionRepository.nombreDivision(d.getIdDivision());
        String pais = direccionRepository.pais(d.getIdPais()).map(DireccionRepository.PaisInfo::getNombre).orElse(d.getIdPais());
        return new DireccionResponse(d.getIdDireccion(), d.getIdDivision(), distrito, d.getDireccion(), d.getReferencia(),
                d.getIdPais(), pais, d.getCiudad(), d.getRegion(), d.getCodigoPostal());
    }

    /**
     * Completa el país y la ubicación de una dirección, con las mismas reglas que la base (T12):
     * - País de la tienda: distrito de la lista oficial, del último nivel. Sin ciudad ni región escritas.
     * - Otro país: ciudad obligatoria, región opcional y código postal si el país lo exige,
     *   con el formato del país. Sin distrito.
     * Se valida aquí para responder 400 con un mensaje claro en vez del error del trigger.
     */
    private void ubicar(Direccion d, DireccionRequest r, String paisTienda) {
        String idPais = (r.idPais() == null || r.idPais().isBlank()) ? paisTienda : r.idPais().trim();
        DireccionRepository.PaisInfo pais = direccionRepository.pais(idPais)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "El país no existe"));
        String codigoPostal = vacioANull(r.codigoPostal());
        if (codigoPostal != null) {
            codigoPostal = codigoPostal.toUpperCase(Locale.ROOT);
        }

        d.setIdPais(idPais);
        if (idPais.equals(paisTienda)) {
            if (r.idDistrito() == null || !direccionRepository.esDistritoValido(r.idDistrito(), idPais)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Elige el distrito de la lista oficial de " + pais.getNombre());
            }
            d.setIdDivision(r.idDistrito());
            d.setCiudad(null);
            d.setRegion(null);
        } else {
            if (pais.getEstado() != null && pais.getEstado() == 1) {
                // Un país con lista oficial exige elegir la división (T12); hoy solo Perú la tiene.
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Por ahora no se pueden registrar direcciones en " + pais.getNombre() + " desde esta tienda");
            }
            String ciudad = vacioANull(r.ciudad());
            if (ciudad == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Escribe la ciudad");
            }
            d.setIdDivision(null);
            d.setCiudad(ciudad);
            d.setRegion(vacioANull(r.region()));
        }

        if (codigoPostal == null && Boolean.TRUE.equals(pais.getRequiereCodigoPostal())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "En " + pais.getNombre() + " el código postal es obligatorio");
        }
        if (codigoPostal != null && !direccionRepository.codigoPostalValido(idPais, codigoPostal).orElse(true)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El código postal " + codigoPostal + " no tiene el formato de " + pais.getNombre());
        }
        d.setCodigoPostal(codigoPostal);
    }

    /**
     * "usuario" es obligatorio y único por tienda en la base, pero la rúbrica no lo pide en el
     * formulario. Si no viene, se genera con la parte del correo antes de la @ (maria@mail.pe → maria)
     * y, si ya existe, se le agrega un número (maria2, maria3…).
     */
    private String elegirUsuario(String pedido, String correo, Long idTienda) {
        if (pedido != null && !pedido.isBlank()) {
            String usuario = pedido.trim();
            if (loginRepository.existsByUsuarioAndIdTienda(usuario, idTienda)) {
                throw new ApiException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso en esta tienda");
            }
            return usuario;
        }
        // Solo caracteres permitidos y largo entre 3 y 46 (deja espacio para el número)
        String base = correo.substring(0, correo.indexOf('@')).replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.length() < 3) {
            base = "cliente" + base;
        }
        base = base.substring(0, Math.min(base.length(), 46));
        String usuario = base;
        for (int n = 2; loginRepository.existsByUsuarioAndIdTienda(usuario, idTienda); n++) {
            usuario = base + n;
        }
        return usuario;
    }

    private static String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
