package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.repository.LoginRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

/**
 * Los correos de la conversación con soporte (V17): cuando el dueño escribe, les llega a los
 * superadmin activos; cuando soporte responde, le llega al dueño.
 *
 * Va en segundo plano (@Async) y nunca lanza: el mensaje ya quedó guardado y se ve en el panel,
 * el correo es solo el aviso. Sin clave de correo, se queda en el log (modo simulado).
 */
@Slf4j
@Component
public class AvisoSoporte {

    private final CorreoClient correos;
    private final LoginRepository loginRepository;
    private final String frontendUrl;

    public AvisoSoporte(CorreoClient correos, LoginRepository loginRepository,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.correos = correos;
        this.loginRepository = loginRepository;
        this.frontendUrl = frontendUrl;
    }

    /** El dueño escribió: aviso a cada superadmin activo, con el mensaje y cómo contestarle. */
    @Async
    public void alEquipo(String tienda, String subdominio, String dueno, String correoDueno, String texto) {
        List<String> destinatarios = loginRepository
                .findByIdRolAndEstado(RolUsuario.SUPERADMIN.getId(), (short) 1).stream()
                .map(Login::getCorreo)
                .toList();
        String html = marco("Mensaje de " + tienda, """
                <p><strong>%s</strong> (%s) escribió desde su tienda <strong>%s</strong> (/tienda/%s):</p>
                %s
                <p><a href="%s/admin/soporte">Abrir Soporte a tiendas</a> para responder y entrar a la tienda.</p>
                """.formatted(e(dueno), e(correoDueno), e(tienda), e(subdominio), cita(texto), frontendUrl));
        for (String para : destinatarios) {
            enviar(para, "Soporte · " + tienda + " necesita ayuda", html);
        }
    }

    /** Soporte respondió: aviso al dueño. */
    @Async
    public void alDueno(String correoDueno, String tienda, String texto) {
        String html = marco("Soporte de Vendely te respondió", """
                <p>Sobre tu tienda <strong>%s</strong>:</p>
                %s
                <p><a href="%s/panel/soporte">Abrir Soporte de Vendely</a> para contestar.</p>
                """.formatted(e(tienda), cita(texto), frontendUrl));
        enviar(correoDueno, "Soporte de Vendely te respondió · " + tienda, html);
    }

    // ------------------------------------------------------------------ apoyo

    private void enviar(String para, String asunto, String html) {
        if (para == null || para.isBlank()) {
            return;
        }
        if (!correos.estaConfigurado()) {
            log.info("Correo de soporte simulado (falta BREVO_API_KEY o RESEND_API_KEY): asunto={}", asunto);
            return;
        }
        try {
            correos.enviar(para, asunto, html, null, null);
            log.info("Correo de soporte enviado con {}: asunto={}", correos.servicio(), asunto);
        } catch (RestClientException ex) {
            log.error("No se pudo enviar el correo de soporte: asunto={}, motivo={}", asunto, ex.getMessage());
        }
    }

    private static String marco(String titulo, String contenido) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:0 auto;color:#1f2937">
                  <div style="background:#101010;color:#ffffff;padding:20px 24px">
                    <h1 style="margin:0;font-size:20px">Vendely · Soporte</h1>
                  </div>
                  <div style="padding:24px;border:1px solid #e5e7eb;border-top:none;font-size:14px;line-height:1.5">
                    <h2 style="margin:0 0 12px;font-size:17px">%s</h2>
                    %s
                  </div>
                </div>
                """.formatted(e(titulo), contenido);
    }

    /** El mensaje tal cual lo escribieron, con sus saltos de línea. */
    private static String cita(String texto) {
        return "<blockquote style=\"margin:12px 0;padding:12px 16px;background:#f6f6f7;border-left:3px solid #101010\">"
                + e(texto).replace("\n", "<br>") + "</blockquote>";
    }

    private static String e(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
