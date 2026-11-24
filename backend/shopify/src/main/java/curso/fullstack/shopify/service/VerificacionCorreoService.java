package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.CodigoEnviadoResponse;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.entity.VerificacionCorreo;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.repository.VerificacionCorreoRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Verificación del correo antes de crear una cuenta (como en Shopify): la del dueño de tienda
 * en Vendely y la del cliente en una tienda. Así cada cuenta queda con un correo que existe y
 * que es de quien se registra: sin el código que llega a ese buzón, la cuenta no se crea.
 *
 * Flujo:
 *  1. {@link #enviar}: el registro pide un código para su correo (con el subdominio si es un
 *     cliente); se genera uno de 6 dígitos, se guarda su hash y se envía con CorreoClient
 *     (Brevo o Resend).
 *  2. {@link #comprobar}: al crear la cuenta, AuthService exige ese código. Si no es correcto,
 *     no se crea nada.
 *  3. {@link #consumir}: con la cuenta creada, el código se borra (no sirve dos veces).
 *
 * Límites: el código dura 10 minutos, se acepta un máximo de 5 intentos por código y no se
 * envía otro al mismo correo antes de un minuto.
 *
 * El código prueba que el correo es tuyo, sin importar para qué cuenta se pidió: por eso hay
 * una sola fila por correo, aunque esa persona sea dueño en Vendely y cliente en otra tienda.
 *
 * Sin clave de correo (en local) no se envía nada: el código se escribe en el log del backend,
 * igual que los demás correos quedan "simulados". Se puede apagar todo con
 * VERIFICAR_CORREO=false (por ejemplo, para correr la colección de Postman).
 */
@Slf4j
@Service
public class VerificacionCorreoService {

    static final Duration VIGENCIA = Duration.ofMinutes(10);
    static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);
    static final int MAX_INTENTOS = 5;

    /** El campo del formulario al que el frontend le pone el error. */
    static final String CAMPO = "codigoVerificacion";

    private static final SecureRandom AZAR = new SecureRandom();

    private final VerificacionCorreoRepository verificaciones;
    private final LoginRepository loginRepository;
    private final TiendaRepository tiendaRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoClient correos;
    private final boolean activa;

    public VerificacionCorreoService(VerificacionCorreoRepository verificaciones, LoginRepository loginRepository,
            TiendaRepository tiendaRepository, PasswordEncoder passwordEncoder, CorreoClient correos,
            @Value("${app.registro.verificar-correo:true}") boolean activa) {
        this.verificaciones = verificaciones;
        this.loginRepository = loginRepository;
        this.tiendaRepository = tiendaRepository;
        this.passwordEncoder = passwordEncoder;
        this.correos = correos;
        this.activa = activa;
    }

    // ------------------------------------------------------------------ 1. enviar

    /** Código para el registro del dueño de tienda (sin subdominio). */
    @Transactional
    public CodigoEnviadoResponse enviar(String correoCrudo) {
        return enviar(correoCrudo, null);
    }

    /**
     * Envía el código. Sin subdominio es para el registro de un dueño; con subdominio, para el
     * registro de un cliente en esa tienda (el correo lleva el nombre de la tienda).
     */
    @Transactional
    public CodigoEnviadoResponse enviar(String correoCrudo, String subdominio) {
        String correo = normalizar(correoCrudo);
        Tienda tienda = subdominio == null || subdominio.isBlank() ? null : tiendaActiva(subdominio);
        if (!activa) {
            return new CodigoEnviadoResponse(false, correo, 0, 0);
        }
        // Si ya hay cuenta con ese correo, se avisa ahora y no después de escribir el código.
        // Un dueño es único en Vendely; un cliente, dentro de su tienda (puede serlo de varias).
        if (tienda == null && loginRepository.existsByCorreoAndIdTiendaIsNull(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "El correo ya está registrado", Map.of("correo", "El correo ya está registrado"));
        }
        if (tienda != null && loginRepository.existsByCorreoAndIdTienda(correo, tienda.getIdTienda())) {
            String mensaje = "El correo ya está registrado en esta tienda";
            throw new ApiException(HttpStatus.CONFLICT, mensaje, Map.of("correo", mensaje));
        }

        return generar(correo, tienda == null ? "Vendely" : tienda.getNombre(), false);
    }

    /**
     * Código para restablecer la contraseña (RecuperacionContrasenaService ya revisó que la cuenta
     * existe). A diferencia del registro, se exige SIEMPRE, aunque VERIFICAR_CORREO esté apagado:
     * sin él, cualquiera podría cambiar la contraseña de otra cuenta.
     */
    @Transactional
    public CodigoEnviadoResponse enviarRecuperacion(String correoCrudo, String para) {
        return generar(normalizar(correoCrudo), para, true);
    }

    /** Genera el código, guarda su hash y lo envía. Respeta la espera de un minuto entre envíos. */
    private CodigoEnviadoResponse generar(String correo, String para, boolean recuperacion) {
        OffsetDateTime ahora = OffsetDateTime.now();
        VerificacionCorreo v = verificaciones.bloquear(correo).orElseGet(VerificacionCorreo::new);
        if (v.getEnviadoEn() != null && v.getEnviadoEn().plus(ESPERA_REENVIO).isAfter(ahora)) {
            long faltan = Duration.between(ahora, v.getEnviadoEn().plus(ESPERA_REENVIO)).toSeconds() + 1;
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Ya te enviamos un código. Espera " + faltan + " segundos para pedir otro.");
        }

        String codigo = String.format("%06d", AZAR.nextInt(1_000_000));
        v.setCorreo(correo);
        v.setCodigoHash(passwordEncoder.encode(codigo));
        v.setExpiraEn(ahora.plus(VIGENCIA));
        v.setEnviadoEn(ahora);
        v.setIntentos((short) 0);              // un código nuevo vuelve a tener sus 5 intentos
        verificaciones.saveAndFlush(v);

        // Si el correo no sale, la transacción se deshace: no queda un código que nadie recibió
        // ni la espera de un minuto para pedir otro.
        mandar(correo, codigo, para, recuperacion);
        return new CodigoEnviadoResponse(true, correo, VIGENCIA.toSeconds(), ESPERA_REENVIO.toSeconds());
    }

    // ------------------------------------------------------------------ 2. comprobar

    /**
     * Comprueba el código antes de crear la cuenta. Va en su propia transacción
     * (REQUIRES_NEW) y no se deshace con el error: así un código equivocado SÍ suma un intento,
     * aunque la creación de la cuenta (la transacción de afuera) se cancele.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void comprobar(String correoCrudo, String codigo) {
        if (activa) {
            revisar(correoCrudo, codigo);
        }
    }

    /**
     * Comprueba el código para restablecer la contraseña: SIEMPRE, aunque la verificación del
     * registro esté apagada. Igual que {@link #comprobar}, un código equivocado suma un intento.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void comprobarRecuperacion(String correoCrudo, String codigo) {
        revisar(correoCrudo, codigo);
    }

    private void revisar(String correoCrudo, String codigo) {
        String correo = normalizar(correoCrudo);
        if (codigo == null || codigo.isBlank()) {
            throw error("Escribe el código que te enviamos a tu correo");
        }
        VerificacionCorreo v = verificaciones.bloquear(correo)
                .orElseThrow(() -> error("Pide un código para verificar tu correo"));
        if (v.getExpiraEn().isBefore(OffsetDateTime.now())) {
            throw error("El código venció. Pide uno nuevo.");
        }
        if (v.getIntentos() >= MAX_INTENTOS) {
            throw error("Escribiste mal el código demasiadas veces. Pide uno nuevo.");
        }
        if (!passwordEncoder.matches(codigo.trim(), v.getCodigoHash())) {
            v.setIntentos((short) (v.getIntentos() + 1));
            verificaciones.save(v);
            int quedan = MAX_INTENTOS - v.getIntentos();
            throw error(quedan > 0
                    ? "El código no es correcto. Te quedan " + quedan + (quedan == 1 ? " intento." : " intentos.")
                    : "Escribiste mal el código demasiadas veces. Pide uno nuevo.");
        }
    }

    // ------------------------------------------------------------------ 3. consumir

    /** Borra el código ya usado. Va en la misma transacción que crea la cuenta. */
    @Transactional
    public void consumir(String correoCrudo) {
        if (activa) {
            verificaciones.deleteById(normalizar(correoCrudo));
        }
    }

    /** Borra el código usado para restablecer la contraseña (siempre, esté o no activa la verificación). */
    @Transactional
    public void consumirRecuperacion(String correoCrudo) {
        verificaciones.deleteById(normalizar(correoCrudo));
    }

    /** Cada noche se borran los códigos vencidos hace más de un día que nadie usó. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Lima")
    @Transactional
    public void limpiarVencidos() {
        int borrados = verificaciones.borrarVencidos(OffsetDateTime.now().minusDays(1));
        if (borrados > 0) {
            log.info("Códigos de verificación vencidos borrados: {}", borrados);
        }
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * El asunto termina en el código ("Tu código de Moda Lima: 123456"): así se ve sin abrir el
     * correo, y las pruebas lo leen de ahí.
     */
    private void mandar(String correo, String codigo, String para, boolean recuperacion) {
        if (!correos.estaConfigurado()) {
            // Solo en local (sin BREVO_API_KEY ni RESEND_API_KEY): así se prueba el registro sin cuentas.
            log.warn("Sin servicio de correo: el código de verificación para {} es {} (no se envió ningún correo)",
                    ocultar(correo), codigo);
            return;
        }
        try {
            String asunto = recuperacion ? "Restablece tu contraseña de " + para + ": " + codigo
                                         : "Tu código de " + para + ": " + codigo;
            correos.enviar(correo, asunto, recuperacion ? htmlRecuperacion(codigo, para) : html(codigo, para), null, null);
            log.info("Código de verificación enviado a {} con {}", ocultar(correo), correos.servicio());
        } catch (RestClientException e) {
            log.error("No se pudo enviar el código de verificación a {}: {}", ocultar(correo), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No pudimos enviar el código. Inténtalo de nuevo en un momento.");
        }
    }

    private static String html(String codigo, String para) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;color:#1f2937">
                  <h2 style="margin:0 0 12px">Verifica tu correo</h2>
                  <p>Usa este código para terminar de crear tu cuenta en %s:</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;margin:24px 0">%s</p>
                  <p style="color:#6b7280;font-size:14px">Vence en %d minutos. Si no pediste crear una cuenta, ignora este correo.</p>
                </div>
                """.formatted(escapar(para), codigo, VIGENCIA.toMinutes());
    }

    private static String htmlRecuperacion(String codigo, String para) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;color:#1f2937">
                  <h2 style="margin:0 0 12px">Restablece tu contraseña</h2>
                  <p>Usa este código para crear una contraseña nueva en %s:</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;margin:24px 0">%s</p>
                  <p style="color:#6b7280;font-size:14px">Vence en %d minutos. Si no pediste cambiar tu contraseña, ignora este correo: tu cuenta sigue igual.</p>
                </div>
                """.formatted(escapar(para), codigo, VIGENCIA.toMinutes());
    }

    /** La tienda del subdominio: 404 si no existe, 403 si está suspendida (igual que AuthService). */
    private Tienda tiendaActiva(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        if (!t.estaActiva()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La tienda está suspendida");
        }
        return t;
    }

    /** El nombre de la tienda va dentro del HTML del correo: se escapan los caracteres especiales. */
    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** c***@vendely.pe: los logs no guardan correos completos. */
    private static String ocultar(String correo) {
        int arroba = correo.indexOf('@');
        return arroba <= 1 ? "***" : correo.charAt(0) + "***" + correo.substring(arroba);
    }

    private static ApiException error(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensaje, Map.of(CAMPO, mensaje));
    }

    private static String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
