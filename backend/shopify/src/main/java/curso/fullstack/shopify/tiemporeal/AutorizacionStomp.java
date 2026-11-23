package curso.fullstack.shopify.tiemporeal;

import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.security.JwtAuthFilter;
import curso.fullstack.shopify.security.JwtService;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * La puerta de los WebSockets: quién se conecta y qué puede escuchar.
 *
 * El token no puede ir en la cabecera HTTP de la conexión, porque el navegador no deja agregar
 * cabeceras al abrir un WebSocket. Por eso /ws está abierto en SecurityConfig y el token viaja
 * en el primer mensaje STOMP (CONNECT). Se valida con las mismas reglas que JwtAuthFilter: firma,
 * vencimiento, cuenta activa y que no sea anterior a un cambio de contraseña.
 *
 * Una conexión sin token se acepta, pero queda como visitante: solo puede escuchar el stock
 * público. Con un token inválido se rechaza la conexión.
 */
@Slf4j
@Component
public class AutorizacionStomp implements ChannelInterceptor {

    private final JwtService jwtService;
    private final LoginRepository loginRepository;
    private final VerificadorTienda verificadorTienda;

    public AutorizacionStomp(JwtService jwtService, LoginRepository loginRepository,
                             VerificadorTienda verificadorTienda) {
        this.jwtService = jwtService;
        this.loginRepository = loginRepository;
        this.verificadorTienda = verificadorTienda;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;   // latidos (heartbeats) y mensajes internos
        }
        switch (stomp.getCommand()) {
            case CONNECT -> identificar(stomp);
            case SUBSCRIBE -> autorizar(stomp);
            // Los clientes solo escuchan. Si alguien manda algo, se corta: no hay nada que recibir.
            case SEND -> throw new MessageDeliveryException("Los canales de tiempo real son de solo lectura");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK…: no necesitan permiso.
            }
        }
        return message;
    }

    /** CONNECT: si trae token, se valida y la conexión queda a nombre de esa cuenta. */
    private void identificar(StompHeaderAccessor stomp) {
        String cabecera = stomp.getFirstNativeHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            return;   // visitante: podrá escuchar solo los canales públicos
        }
        try {
            Claims claims = jwtService.leerToken(cabecera.substring(7));
            Login login = loginRepository.findById(Long.valueOf(claims.getSubject()))
                    .filter(Login::estaActivo)
                    .filter(l -> JwtAuthFilter.tokenPosteriorAlCambio(claims, l))
                    .orElseThrow(() -> new MessageDeliveryException("La sesión ya no es válida"));
            stomp.setUser(new UsuarioConectado(new UsuarioAutenticado(
                    login.getIdLogin(), login.getRol(), login.getIdTienda(), login.getCorreo())));
        } catch (JwtException | IllegalArgumentException e) {
            // Igual que en las peticiones HTTP: se registra el tipo de problema, nunca el token.
            log.warn("Conexión de tiempo real rechazada: {}", e.getClass().getSimpleName());
            throw new MessageDeliveryException("La sesión no es válida");
        }
    }

    /** SUBSCRIBE: Canales decide; aquí solo se le da la forma de saber si la tienda es suya. */
    private void autorizar(StompHeaderAccessor stomp) {
        UsuarioAutenticado usuario = stomp.getUser() instanceof UsuarioConectado c ? c.usuario() : null;
        String destino = stomp.getDestination();
        if (!Canales.puedeEscuchar(usuario, destino, this::esDueno)) {
            log.warn("Suscripción rechazada: {} a {}",
                    usuario == null ? "visitante" : "idLogin=" + usuario.idLogin(), destino);
            throw new MessageDeliveryException("No tienes permiso para escuchar este canal");
        }
    }

    private boolean esDueno(UsuarioAutenticado usuario, Long idTienda) {
        try {
            verificadorTienda.delDueno(usuario, idTienda);
            return true;
        } catch (ApiException e) {
            return false;   // 404 (no existe) o 403 (es de otro): en los dos casos, no
        }
    }
}
