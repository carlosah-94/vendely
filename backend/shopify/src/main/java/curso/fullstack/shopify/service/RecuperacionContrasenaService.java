package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.CodigoEnviadoResponse;
import curso.fullstack.shopify.dto.RestablecerContrasenaRequest;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "¿Olvidaste tu contraseña?" para el panel y para los clientes de una tienda.
 *
 *  1. {@link #enviarCodigo}: si hay una cuenta con ese correo, le llega un código de 6 dígitos
 *     (VerificacionCorreoService: 10 minutos, 5 intentos, un envío por minuto). La respuesta es la
 *     misma haya o no cuenta, para que nadie pueda averiguar qué correos están registrados.
 *  2. {@link #restablecer}: con el código correcto se guarda la contraseña nueva. Como en
 *     "Cambiar contraseña", contrasena_cambiada_en cierra las sesiones abiertas en otros equipos.
 *
 * La doble autenticación no se toca: si la cuenta la tiene, el login seguirá pidiendo el código
 * de la app.
 */
@Slf4j
@Service
public class RecuperacionContrasenaService {

    private final LoginRepository loginRepository;
    private final TiendaRepository tiendaRepository;
    private final VerificacionCorreoService verificacion;
    private final PasswordEncoder passwordEncoder;

    public RecuperacionContrasenaService(LoginRepository loginRepository, TiendaRepository tiendaRepository,
                                         VerificacionCorreoService verificacion, PasswordEncoder passwordEncoder) {
        this.loginRepository = loginRepository;
        this.tiendaRepository = tiendaRepository;
        this.verificacion = verificacion;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CodigoEnviadoResponse enviarCodigo(String correoCrudo, String subdominio) {
        String correo = normalizar(correoCrudo);
        Tienda tienda = tienda(subdominio);
        if (cuenta(correo, tienda).isEmpty()) {
            // Misma respuesta que si existiera: no se revela qué correos tienen cuenta.
            log.info("Recuperación pedida para un correo sin cuenta (tienda={})", tienda == null ? "-" : tienda.getIdTienda());
            return new CodigoEnviadoResponse(true, correo, VerificacionCorreoService.VIGENCIA.toSeconds(),
                    VerificacionCorreoService.ESPERA_REENVIO.toSeconds());
        }
        return verificacion.enviarRecuperacion(correo, tienda == null ? "Vendely" : tienda.getNombre());
    }

    @Transactional
    public void restablecer(RestablecerContrasenaRequest r) {
        String correo = normalizar(r.correo());
        Login login = cuenta(correo, tienda(r.subdominio()))
                // Sin cuenta no hubo código: el mismo mensaje que un código que no existe.
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Pide un código para restablecer tu contraseña",
                        Map.of(VerificacionCorreoService.CAMPO, "Pide un código para restablecer tu contraseña")));
        verificacion.comprobarRecuperacion(correo, r.codigo());

        login.setContrasenaHash(passwordEncoder.encode(r.contrasena()));
        login.setContrasenaCambiadaEn(OffsetDateTime.now());   // los tokens anteriores dejan de servir
        loginRepository.saveAndFlush(login);
        verificacion.consumirRecuperacion(correo);
        log.info("Contraseña restablecida con código: idLogin={}", login.getIdLogin());
    }

    // ------------------------------------------------------------------ apoyo

    /** Sin subdominio: la cuenta del panel (dueño, empleado o superadmin). Con subdominio: el cliente de esa tienda. */
    private Optional<Login> cuenta(String correo, Tienda tienda) {
        return tienda == null ? loginRepository.findByCorreoAndIdTiendaIsNull(correo)
                              : loginRepository.findByCorreoAndIdTienda(correo, tienda.getIdTienda());
    }

    private Tienda tienda(String subdominio) {
        if (subdominio == null || subdominio.isBlank()) {
            return null;
        }
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        if (!t.estaActiva()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La tienda está suspendida");
        }
        return t;
    }

    private static String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
