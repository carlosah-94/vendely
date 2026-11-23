package curso.fullstack.shopify.tiemporeal;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSockets con STOMP en /ws (issue #4).
 *
 * STOMP es un protocolo de mensajes encima del WebSocket: le da "canales" a los que uno se
 * suscribe, en lugar de un tubo de bytes. El broker es el simple de Spring, en memoria: alcanza
 * para un solo servidor, que es lo que hay en Render. Con varios servidores haría falta uno
 * externo (RabbitMQ, Redis) para que un aviso llegue a las conexiones de todos.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Los mismos orígenes que CORS: solo el frontend de Vendely puede abrir una conexión. */
    @Value("${app.cors.origenes:http://localhost:5173,http://localhost:5174}")
    private List<String> origenes;

    private final AutorizacionStomp autorizacion;

    public WebSocketConfig(AutorizacionStomp autorizacion) {
        this.autorizacion = autorizacion;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        // WebSocket directo, sin SockJS: todos los navegadores actuales lo soportan.
        registro.addEndpoint("/ws").setAllowedOrigins(origenes.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        // Latidos cada 10 s en los dos sentidos: así se detecta una conexión muerta, y un proxy
        // (como el de Render) no la cierra por inactividad mientras la pestaña está abierta.
        registro.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(programadorDeLatidos());
        registro.setUserDestinationPrefix("/user");
        // Sin setApplicationDestinationPrefixes: los clientes no le envían mensajes al servidor.
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(autorizacion);
    }

    /**
     * El reloj de los latidos. No se publica como bean a propósito: si lo fuera, Spring lo usaría
     * también para las tareas @Scheduled (la conciliación de pagos, los planes) y las pondría a
     * compartir un solo hilo con los latidos.
     */
    private static ThreadPoolTaskScheduler programadorDeLatidos() {
        ThreadPoolTaskScheduler reloj = new ThreadPoolTaskScheduler();
        reloj.setPoolSize(1);
        reloj.setThreadNamePrefix("ws-latido-");
        reloj.setDaemon(true);
        reloj.initialize();
        return reloj;
    }
}
