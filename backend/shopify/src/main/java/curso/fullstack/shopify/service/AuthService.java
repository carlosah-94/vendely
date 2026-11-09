package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.AuthResponse;
import curso.fullstack.shopify.dto.CambioContrasenaRequest;
import curso.fullstack.shopify.dto.LoginRequest;
import curso.fullstack.shopify.dto.RegistroClienteRequest;
import curso.fullstack.shopify.dto.RegistroDuenoRequest;
import curso.fullstack.shopify.dto.UsuarioResponse;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.Propietario;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.PropietarioRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.CuentaUserDetails;
import curso.fullstack.shopify.security.JwtService;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.UsuarioDetailsService;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /**
     * Mismo mensaje si falla el correo o la contraseña: así no se revela qué
     * correos existen.
     */
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    private final LoginRepository loginRepository;
    private final PropietarioRepository propietarioRepository;
    private final TiendaRepository tiendaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(LoginRepository loginRepository, PropietarioRepository propietarioRepository,
            TiendaRepository tiendaRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, AuthenticationManager authenticationManager) {
        this.loginRepository = loginRepository;
        this.propietarioRepository = propietarioRepository;
        this.tiendaRepository = tiendaRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    // ------------------------------------------------------------------ registro

    @Transactional
    public AuthResponse registrarDueno(RegistroDuenoRequest r) {
        String correo = normalizarCorreo(r.correo());
        String documento = r.numeroDocumento().trim();
        validarDocumento(r.tipoDocumento(), documento);

        if (loginRepository.existsByCorreoAndIdTiendaIsNull(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }
        if (loginRepository.existsByUsuarioAndIdTiendaIsNull(r.usuario().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso");
        }
        if (propietarioRepository.existsByTipoDocumentoAndNumeroDocumento(r.tipoDocumento(), documento)) {
            throw new ApiException(HttpStatus.CONFLICT, "El documento ya está registrado");
        }

        Login login = nuevaCuenta(RolUsuario.ADMIN_TIENDA, null, r.usuario(), correo, r.contrasena(),
                r.nombres(), r.primerApellido(), r.segundoApellido(), r.telefono(), false);
        loginRepository.saveAndFlush(login);

        Propietario p = new Propietario();
        p.setIdLogin(login.getIdLogin());
        p.setTipoDocumento(r.tipoDocumento());
        p.setNumeroDocumento(documento);
        p.setRazonSocial(vacioANull(r.razonSocial()));
        propietarioRepository.save(p);

        return respuesta(login);
    }

    @Transactional
    public AuthResponse registrarCliente(RegistroClienteRequest r) {
        Tienda tienda = tiendaActiva(r.subdominio());
        String correo = normalizarCorreo(r.correo());

        if (loginRepository.existsByCorreoAndIdTienda(correo, tienda.getIdTienda())) {
            throw new ApiException(HttpStatus.CONFLICT, "El correo ya está registrado en esta tienda");
        }
        if (loginRepository.existsByUsuarioAndIdTienda(r.usuario().trim(), tienda.getIdTienda())) {
            throw new ApiException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso en esta tienda");
        }

        Login login = nuevaCuenta(RolUsuario.CLIENTE, tienda.getIdTienda(), r.usuario(), correo, r.contrasena(),
                r.nombres(), r.primerApellido(), r.segundoApellido(), r.telefono(),
                Boolean.TRUE.equals(r.aceptaMarketing()));
        loginRepository.saveAndFlush(login);
        return respuesta(login);
    }

    // ------------------------------------------------------------ inicio de sesión

    /**
     * El AuthenticationManager busca la cuenta con UsuarioDetailsService,
     * compara la contraseña con BCrypt y revisa si la cuenta está activa.
     */
    @Transactional(readOnly = true)
    public AuthResponse iniciarSesion(LoginRequest r) {
        String correo = normalizarCorreo(r.correo());
        Long idTienda = null;
        if (r.subdominio() != null && !r.subdominio().isBlank()) {
            idTienda = tiendaActiva(r.subdominio()).getIdTienda();
        }

        try {
            Authentication autenticacion = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            UsuarioDetailsService.clave(correo, idTienda), r.contrasena()));
            Login login = ((CuentaUserDetails) autenticacion.getPrincipal()).getLogin();
            return respuesta(login);
        } catch (DisabledException e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La cuenta está desactivada");
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        }
    }

    // --------------------------------------------------------------- mi cuenta

    @Transactional(readOnly = true)
    public UsuarioResponse miCuenta(UsuarioAutenticado yo) {
        return UsuarioResponse.desde(buscar(yo.idLogin()));
    }

    /**
     * Cambia la contraseña y devuelve un token nuevo: los tokens anteriores dejan
     * de servir.
     */
    @Transactional
    public AuthResponse cambiarContrasena(UsuarioAutenticado yo, CambioContrasenaRequest r) {
        Login login = buscar(yo.idLogin());
        if (!passwordEncoder.matches(r.contrasenaActual(), login.getContrasenaHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        login.setContrasenaHash(passwordEncoder.encode(r.contrasenaNueva()));
        login.setContrasenaCambiadaEn(OffsetDateTime.now());
        loginRepository.saveAndFlush(login);
        return respuesta(login);
    }

    // ------------------------------------------------------------------ apoyo

    private Login nuevaCuenta(RolUsuario rol, Long idTienda, String usuario, String correo, String contrasena,
            String nombres, String primerApellido, String segundoApellido, String telefono,
            boolean aceptaMarketing) {
        Login l = new Login();
        l.setRol(rol);
        l.setIdTienda(idTienda);
        l.setUsuario(usuario.trim());
        l.setCorreo(correo);
        l.setContrasenaHash(passwordEncoder.encode(contrasena));
        l.setNombres(nombres.trim());
        l.setPrimerApellido(primerApellido.trim());
        l.setSegundoApellido(vacioANull(segundoApellido));
        l.setTelefono(vacioANull(telefono));
        l.setAceptaMarketing(aceptaMarketing);
        return l;
    }

    private Tienda tiendaActiva(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        if (!t.estaActiva()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La tienda está suspendida");
        }
        return t;
    }

    private Login buscar(Long idLogin) {
        return loginRepository.findById(idLogin)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La cuenta no existe"));
    }

    private AuthResponse respuesta(Login login) {
        return new AuthResponse(jwtService.generarToken(login), "Bearer",
                jwtService.getExpiracionSegundos(), UsuarioResponse.desde(login));
    }

    /** La base exige el correo en minúsculas (CHECK ck_login_correo). */
    private static String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Para los documentos de Perú (1 DNI · 2 RUC · 3 CE) se valida aquí para dar un
     * mensaje claro.
     * Los demás tipos (RUT, RFC, NIT…) los valida la base con el patrón de
     * tipos_documento (trigger T13).
     */
    private static void validarDocumento(Short tipo, String numero) {
        boolean ok = switch (tipo) {
            case 1 -> numero.matches("^[0-9]{8}$"); // DNI
            case 2 -> numero.matches("^(10|15|17|20)[0-9]{9}$"); // RUC
            case 3 -> numero.matches("^[0-9]{9}$"); // carné de extranjería
            default -> true; // lo valida la base
        };
        if (!ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, switch (tipo) {
                case 1 -> "El DNI debe tener 8 dígitos";
                case 2 -> "El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20";
                case 3 -> "El carné de extranjería debe tener 9 dígitos";
                default -> "Tipo de documento inválido";
            });
        }
    }
}
