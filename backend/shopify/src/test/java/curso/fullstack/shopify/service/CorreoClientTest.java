package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * Qué servicio usa CorreoClient para mandar los correos. Prueba sin Spring ni internet:
 * Brevo y Resend son simulados.
 */
class CorreoClientTest {

    private final BrevoClient brevo = mock(BrevoClient.class);
    private final ResendClient resend = mock(ResendClient.class);
    private final CorreoClient correos = new CorreoClient(brevo, resend);

    @Test
    void conBrevoConfiguradoSeUsaBrevoAunqueTambienEsteResend() {
        when(brevo.estaConfigurado()).thenReturn(true);
        when(resend.estaConfigurado()).thenReturn(true);
        when(brevo.enviar("a@b.pe", "Asunto", "<p>Hola</p>", null, null)).thenReturn("id-brevo");

        assertThat(correos.enviar("a@b.pe", "Asunto", "<p>Hola</p>", null, null)).isEqualTo("id-brevo");
        assertThat(correos.servicio()).isEqualTo("Brevo");
        verify(resend, never()).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void sinBrevoSeUsaResend() {
        when(resend.estaConfigurado()).thenReturn(true);
        when(resend.enviar("a@b.pe", "Asunto", "<p>Hola</p>", null, null)).thenReturn("id-resend");

        assertThat(correos.enviar("a@b.pe", "Asunto", "<p>Hola</p>", null, null)).isEqualTo("id-resend");
        assertThat(correos.servicio()).isEqualTo("Resend");
        verify(brevo, never()).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void sinNingunoNoEstaConfigurado() {
        assertThat(correos.estaConfigurado()).isFalse();
        assertThat(correos.servicio()).isEqualTo("ninguno");
    }

    @Test
    void brevoSinCorreoRemitenteNoCuentaComoConfigurado() {
        assertThat(new BrevoClient("xkeysib-prueba", "", "Vendely").estaConfigurado()).isFalse();
        assertThat(new BrevoClient("", "yo@gmail.com", "Vendely").estaConfigurado()).isFalse();
        assertThat(new BrevoClient("xkeysib-prueba", "yo@gmail.com", "Vendely").estaConfigurado()).isTrue();
    }
}
