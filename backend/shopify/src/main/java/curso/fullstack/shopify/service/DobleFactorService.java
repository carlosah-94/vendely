package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.DobleFactorInicioResponse;
import curso.fullstack.shopify.dto.UsuarioResponse;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.security.Totp;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doble autenticación (issue #4): además de la contraseña, el código de 6 dígitos de una app
 * como Google Authenticator.
 *
 * La activación tiene dos pasos a propósito. Primero se genera el secreto y se muestra el QR;
 * recién cuando la persona escribe un código correcto se activa. Si se activara al mostrar el
 * QR, alguien que lo escanea mal quedaría fuera de su propia cuenta en el siguiente login.
 *
 * El secreto nunca se escribe en el log y solo se devuelve una vez, al empezar la activación.
 */
@Slf4j
@Service
public class DobleFactorService {

    private static final String EMISOR = "Vendely";

    /**
     * Un código tiene un millón de combinaciones: probarlas todas es posible si nada lo frena.
     * Tras 5 códigos incorrectos seguidos, la cuenta espera 10 minutos antes de poder
     * intentarlo de nuevo. Se guarda en memoria: si el backend se reinicia, el contador vuelve
     * a cero, lo cual es aceptable para un freno (la contraseña sigue haciendo falta igual).
     */
    private static final int MAX_FALLOS = 5;
    private static final Duration ESPERA = Duration.ofMinutes(10);

    private record Fallos(int cuantos, Instant bloqueadoHasta) {
    }

    private final Map<Long, Fallos> fallos = new ConcurrentHashMap<>();

    private final LoginRepository loginRepository;
    private final PasswordEncoder passwordEncoder;

    public DobleFactorService(LoginRepository loginRepository, PasswordEncoder passwordEncoder) {
        this.loginRepository = loginRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------ activar (dos pasos)

    /** Paso 1: genera el secreto y devuelve lo que necesita el QR. Todavía no se exige. */
    @Transactional
    public DobleFactorInicioResponse iniciarActivacion(UsuarioAutenticado yo) {
        Login l = buscar(yo.idLogin());
        if (activa(l)) {
            throw new ApiException(HttpStatus.CONFLICT, "La doble autenticación ya está activa");
        }
        String secreto = Totp.generarSecreto();
        l.setTotpSecreto(secreto);
        l.setTotpUltimoPaso(null);
        loginRepository.saveAndFlush(l);
        log.info("Activación de doble autenticación iniciada: idLogin={}", l.getIdLogin());
        return new DobleFactorInicioResponse(secreto, Totp.enlaceParaApp(secreto, l.getCorreo(), EMISOR));
    }

    /** Paso 2: con un código correcto queda activa, y desde ahí el login lo pide. */
    @Transactional
    public UsuarioResponse confirmarActivacion(UsuarioAutenticado yo, String codigo) {
        Login l = buscar(yo.idLogin());
        if (activa(l)) {
            throw new ApiException(HttpStatus.CONFLICT, "La doble autenticación ya está activa");
        }
        if (l.getTotpSecreto() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Primero escanea el código QR");
        }
        long paso = comprobar(l, codigo, HttpStatus.BAD_REQUEST);
        l.setTotpActivo(true);
        l.setTotpUltimoPaso(paso);
        loginRepository.saveAndFlush(l);
        log.info("Doble autenticación activada: idLogin={}", l.getIdLogin());
        return UsuarioResponse.desde(l);
    }

    /**
     * La desactiva la propia persona. Pide la contraseña Y un código: si alguien deja la sesión
     * abierta en una computadora, quien se siente ahí no puede quitarle la protección.
     */
    @Transactional
    public UsuarioResponse desactivar(UsuarioAutenticado yo, String contrasena, String codigo) {
        Login l = buscar(yo.idLogin());
        if (!passwordEncoder.matches(contrasena, l.getContrasenaHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contraseña no es correcta");
        }
        if (!activa(l)) {
            throw new ApiException(HttpStatus.CONFLICT, "La doble autenticación no está activa");
        }
        comprobar(l, codigo, HttpStatus.BAD_REQUEST);
        limpiar(l);
        log.info("Doble autenticación desactivada por su dueño: idLogin={}", l.getIdLogin());
        return UsuarioResponse.desde(l);
    }

    /**
     * El superadmin la quita de una cuenta: es la salida cuando alguien pierde el teléfono.
     * No pide código (la persona justamente no lo tiene); queda registrado quién lo hizo.
     */
    @Transactional
    public void quitarComoAdmin(UsuarioAutenticado admin, Long idLogin) {
        Login l = buscar(idLogin);
        if (!activa(l)) {
            throw new ApiException(HttpStatus.CONFLICT, "Esa cuenta no tiene la doble autenticación activa");
        }
        limpiar(l);
        fallos.remove(idLogin);
        log.warn("El superadmin {} quitó la doble autenticación de la cuenta {}", admin.idLogin(), idLogin);
    }

    // ------------------------------------------------------------------ en el inicio de sesión

    /**
     * Lo llama AuthService cuando la contraseña ya fue correcta. Sin código, o con uno
     * incorrecto, responde 401 y no se entrega el token.
     *
     * En "campos.codigo" va el motivo ("requerido" o "incorrecto"): con eso el formulario sabe
     * que tiene que mostrar el campo del código, sin adivinar por el texto del mensaje.
     */
    @Transactional
    public void exigirEnLogin(Long idLogin, String codigo) {
        Login l = buscar(idLogin);
        if (!activa(l)) {
            return;
        }
        if (codigo == null || codigo.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Escribe el código de 6 dígitos de tu app",
                    Map.of("codigo", "requerido"));
        }
        long paso = comprobar(l, codigo, HttpStatus.UNAUTHORIZED);
        l.setTotpUltimoPaso(paso);
        loginRepository.saveAndFlush(l);
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * Comprueba el código con el freno de intentos. Devuelve el intervalo en que coincidió
     * (para guardarlo y que ese código no sirva de nuevo).
     */
    private long comprobar(Login l, String codigo, HttpStatus siFalla) {
        Fallos f = fallos.get(l.getIdLogin());
        if (f != null && f.bloqueadoHasta() != null && Instant.now().isBefore(f.bloqueadoHasta())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados códigos incorrectos. Espera unos minutos antes de intentarlo de nuevo");
        }
        long paso = Totp.verificar(l.getTotpSecreto(), codigo, Instant.now().getEpochSecond(), l.getTotpUltimoPaso());
        if (paso < 0) {
            int cuantos = (f == null || f.bloqueadoHasta() != null) ? 1 : f.cuantos() + 1;
            Instant hasta = cuantos >= MAX_FALLOS ? Instant.now().plus(ESPERA) : null;
            fallos.put(l.getIdLogin(), new Fallos(cuantos, hasta));
            // Se registra la cuenta y el número de intento, nunca el código ni el secreto.
            log.warn("Código de doble autenticación incorrecto: idLogin={}, intento {}", l.getIdLogin(), cuantos);
            throw new ApiException(siFalla, "El código no es correcto", Map.of("codigo", "incorrecto"));
        }
        fallos.remove(l.getIdLogin());
        return paso;
    }

    private void limpiar(Login l) {
        l.setTotpActivo(false);
        l.setTotpSecreto(null);
        l.setTotpUltimoPaso(null);
        loginRepository.saveAndFlush(l);
    }

    private static boolean activa(Login l) {
        return Boolean.TRUE.equals(l.getTotpActivo());
    }

    private Login buscar(Long idLogin) {
        return loginRepository.findById(idLogin)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La cuenta no existe"));
    }
}
