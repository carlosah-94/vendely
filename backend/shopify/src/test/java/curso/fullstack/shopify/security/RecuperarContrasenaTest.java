package curso.fullstack.shopify.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * "¿Olvidaste tu contraseña?": con el código que llega al correo se pone una contraseña nueva,
 * sin revelar qué correos tienen cuenta y sin aceptar un código equivocado.
 */
class RecuperarContrasenaTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void datos() {
        tienda = datos.nuevaTienda();
        when(correos.estaConfigurado()).thenReturn(true);
        clearInvocations(correos);
    }

    @Test
    void elDuenoRecuperaSuContrasenaConElCodigoYEntraConLaNueva() throws Exception {
        String correo = tienda.correoDueno();
        String codigo = pedirCodigo(correo, null);

        restablecer(correo, null, codigo, "NuevaClave99").andExpect(status().isNoContent());

        login(correo, "NuevaClave99", null).andExpect(status().isOk());
        login(correo, DatosPrueba.CONTRASENA, null).andExpect(status().isUnauthorized());
        // El código ya se usó: no sirve otra vez.
        restablecer(correo, null, codigo, "OtraClave123").andExpect(status().isBadRequest());
    }

    @Test
    void unClienteRecuperaLaSuyaEnSuTiendaYSinSubdominioNoHayCuentaDelPanel() throws Exception {
        String correo = tienda.correoCliente();
        String codigo = pedirCodigo(correo, tienda.subdominio());

        restablecer(correo, null, codigo, "NuevaClave99").andExpect(status().isBadRequest());
        restablecer(correo, tienda.subdominio(), codigo, "NuevaClave99").andExpect(status().isNoContent());
        login(correo, "NuevaClave99", tienda.subdominio()).andExpect(status().isOk());
    }

    @Test
    void unCorreoSinCuentaRecibeLaMismaRespuestaPeroNingunCorreo() throws Exception {
        enviarCodigo("nadie" + System.nanoTime() + "@vendely.test", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requerido").value(true));
        verify(correos, never()).enviar(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void unCodigoEquivocadoNoCambiaLaContrasena() throws Exception {
        String correo = tienda.correoDueno();
        String codigo = pedirCodigo(correo, null);
        String otro = codigo.equals("000000") ? "111111" : "000000";

        restablecer(correo, null, otro, "NuevaClave99")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("El código no es correcto")));
        login(correo, DatosPrueba.CONTRASENA, null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions enviarCodigo(String correo, String subdominio) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo", correo);
        cuerpo.put("subdominio", subdominio);
        return mvc.perform(post("/api/auth/recuperar/codigo").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    /** Pide el código y lo lee del asunto del correo simulado ("Restablece tu contraseña de X: 123456"). */
    private String pedirCodigo(String correo, String subdominio) throws Exception {
        enviarCodigo(correo, subdominio).andExpect(status().isOk());
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        verify(correos).enviar(eq(correo), asunto.capture(), anyString(), isNull(), isNull());
        String texto = asunto.getValue();
        org.assertj.core.api.Assertions.assertThat(texto).startsWith("Restablece tu contraseña");
        return texto.substring(texto.length() - 6);
    }

    private ResultActions restablecer(String correo, String subdominio, String codigo, String contrasena) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo", correo);
        cuerpo.put("subdominio", subdominio);
        cuerpo.put("codigo", codigo);
        cuerpo.put("contrasena", contrasena);
        return mvc.perform(post("/api/auth/recuperar/restablecer").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions login(String correo, String contrasena, String subdominio) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo", correo);
        cuerpo.put("contrasena", contrasena);
        cuerpo.put("subdominio", subdominio);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }
}
