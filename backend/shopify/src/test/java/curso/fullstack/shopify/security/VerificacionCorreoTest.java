package curso.fullstack.shopify.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClientException;

/**
 * Verificación del correo al registrar un dueño y un cliente (VerificacionCorreoService).
 *
 * El envío está simulado (CorreoClient): el código se lee del asunto del correo que "se envió"
 * ("Tu código de Vendely: 123456"). Cada prueba usa un correo, usuario y DNI nuevos.
 */
class VerificacionCorreoTest extends PruebaIntegracion {

    private String correo;
    private String usuario;
    private String dni;

    @BeforeEach
    void datosNuevos() {
        long n = ThreadLocalRandom.current().nextLong(1_000_000_000L);
        correo = "verifica" + n + "@vendely.test";
        usuario = "verifica" + n;
        dni = String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        when(correos.estaConfigurado()).thenReturn(true);
    }

    // ------------------------------------------------------------------ camino feliz

    @Test
    void conElCodigoCorrectoSeCreaLaCuentaYElCodigoSeGasta() throws Exception {
        String codigo = pedirCodigo(correo);

        registrar(codigo).andExpect(status().isCreated());

        assertThat(loginRepository.existsByCorreoAndIdTiendaIsNull(correo)).isTrue();
        assertThat(filas(correo)).as("el código usado se borra").isZero();
    }

    @Test
    void elCorreoNoDistingueMayusculas() throws Exception {
        String codigo = pedirCodigo(correo.toUpperCase());

        registrar(codigo).andExpect(status().isCreated());
    }

    @Test
    void sinResendNoSeEnviaNadaPeroElCodigoQuedaGuardado() throws Exception {
        when(correos.estaConfigurado()).thenReturn(false);

        enviarCodigo(correo).andExpect(status().isOk()).andExpect(jsonPath("$.requerido").value(true));

        verify(correos, never()).enviar(anyString(), anyString(), anyString(), any(), any());
        assertThat(filas(correo)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ sin código o código malo

    @Test
    void sinCodigoNoSeCreaLaCuenta() throws Exception {
        pedirCodigo(correo);

        registrar(null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.codigoVerificacion").exists());
        assertThat(loginRepository.existsByCorreoAndIdTiendaIsNull(correo)).isFalse();
    }

    @Test
    void sinHaberPedidoCodigoNoSeCreaLaCuenta() throws Exception {
        registrar("123456").andExpect(status().isBadRequest());
        assertThat(loginRepository.existsByCorreoAndIdTiendaIsNull(correo)).isFalse();
    }

    @Test
    void cadaCodigoEquivocadoSumaUnIntentoYAlQuintoNiElCorrectoSirve() throws Exception {
        String codigo = pedirCodigo(correo);
        String malo = codigo.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            registrar(malo).andExpect(status().isBadRequest());
        }
        // Los intentos se guardan aunque el registro falle (la comprobación va en su propia transacción).
        assertThat(jdbc.queryForObject("SELECT intentos FROM verificaciones_correo WHERE correo = ?", Integer.class, correo))
                .isEqualTo(5);

        registrar(codigo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Escribiste mal el código demasiadas veces. Pide uno nuevo."));
        assertThat(loginRepository.existsByCorreoAndIdTiendaIsNull(correo)).isFalse();
    }

    @Test
    void unCodigoVencidoNoSirve() throws Exception {
        String codigo = pedirCodigo(correo);
        jdbc.update("UPDATE verificaciones_correo SET expira_en = now() - interval '1 minute' WHERE correo = ?", correo);

        registrar(codigo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El código venció. Pide uno nuevo."));
    }

    // ------------------------------------------------------------------ reenvío

    @Test
    void noSeEnviaOtroCodigoAntesDeUnMinuto() throws Exception {
        pedirCodigo(correo);

        enviarCodigo(correo).andExpect(status().isTooManyRequests());
    }

    @Test
    void alPedirOtroCodigoElAnteriorDejaDeServir() throws Exception {
        String primero = pedirCodigo(correo);
        jdbc.update("UPDATE verificaciones_correo SET enviado_en = now() - interval '2 minutes' WHERE correo = ?", correo);
        String segundo = pedirCodigo(correo);

        if (!primero.equals(segundo)) {          // 1 en un millón de que salga el mismo
            registrar(primero).andExpect(status().isBadRequest());
        }
        registrar(segundo).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ otros errores

    @Test
    void unCorreoQueYaTieneCuentaNoRecibeCodigo() throws Exception {
        String existente = datos.nuevaTienda().correoDueno();

        enviarCodigo(existente)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.correo").exists());
        verify(correos, never()).enviar(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void siElUsuarioYaExisteElCodigoNoSeGasta() throws Exception {
        String codigo = pedirCodigo(correo);
        String usuarioOcupado = usuario;
        usuario = "dueno";                       // usuario de prueba de db/migration-local

        registrar(codigo).andExpect(status().isConflict());

        assertThat(filas(correo)).isEqualTo(1);
        usuario = usuarioOcupado;
        registrar(codigo).andExpect(status().isCreated());
    }

    @Test
    void siElCorreoNoSaleResponde502YNoQuedaCodigo() throws Exception {
        when(correos.enviar(eq(correo), anyString(), anyString(), isNull(), isNull()))
                .thenThrow(new RestClientException("Resend no responde"));

        enviarCodigo(correo).andExpect(status().isBadGateway());

        assertThat(filas(correo)).as("se puede volver a pedir enseguida").isZero();
    }

    // ------------------------------------------------------------------ cliente de una tienda

    @Test
    void elClienteConElCodigoCorrectoCreaSuCuentaEnLaTienda() throws Exception {
        DatosPrueba.Tienda t = datos.nuevaTienda();
        String codigo = pedirCodigo(correo, t.subdominio());

        registrarCliente(t.subdominio(), codigo).andExpect(status().isCreated());

        assertThat(loginRepository.existsByCorreoAndIdTienda(correo, t.idTienda())).isTrue();
        assertThat(filas(correo)).as("el código usado se borra").isZero();
    }

    @Test
    void elCorreoDelCodigoDelClienteLlevaElNombreDeLaTienda() throws Exception {
        DatosPrueba.Tienda t = datos.nuevaTienda();
        enviarCodigo(correo, t.subdominio()).andExpect(status().isOk());

        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        verify(correos).enviar(eq(correo), asunto.capture(), anyString(), isNull(), isNull());
        String nombre = jdbc.queryForObject("SELECT nombre FROM tiendas WHERE id_tienda = ?", String.class, t.idTienda());
        assertThat(asunto.getValue()).startsWith("Tu código de " + nombre + ": ");
    }

    @Test
    void elClienteSinCodigoNoSeRegistra() throws Exception {
        DatosPrueba.Tienda t = datos.nuevaTienda();
        pedirCodigo(correo, t.subdominio());

        registrarCliente(t.subdominio(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.codigoVerificacion").exists());
        assertThat(loginRepository.existsByCorreoAndIdTienda(correo, t.idTienda())).isFalse();
    }

    @Test
    void elClienteConUnCodigoEquivocadoNoSeRegistra() throws Exception {
        DatosPrueba.Tienda t = datos.nuevaTienda();
        String codigo = pedirCodigo(correo, t.subdominio());
        String malo = codigo.equals("000000") ? "111111" : "000000";

        registrarCliente(t.subdominio(), malo).andExpect(status().isBadRequest());
        assertThat(loginRepository.existsByCorreoAndIdTienda(correo, t.idTienda())).isFalse();
    }

    @Test
    void unCorreoQueYaEsClienteDeEsaTiendaNoRecibeCodigo() throws Exception {
        DatosPrueba.Tienda t = datos.nuevaTienda();
        String delCliente = t.correoCliente();

        enviarCodigo(delCliente, t.subdominio())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.correo").value("El correo ya está registrado en esta tienda"));
        verify(correos, never()).enviar(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void elMismoCorreoPuedeSerClienteDeOtraTiendaODuenoYRecibeCodigo() throws Exception {
        DatosPrueba.Tienda una = datos.nuevaTienda();
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        correo = una.correoCliente();

        String codigo = pedirCodigo(correo, otra.subdominio());
        registrarCliente(otra.subdominio(), codigo).andExpect(status().isCreated());
    }

    @Test
    void elCodigoParaUnaTiendaQueNoExisteResponde404() throws Exception {
        enviarCodigo(correo, "no-existe-esta-tienda").andExpect(status().isNotFound());
        verify(correos, never()).enviar(anyString(), anyString(), anyString(), any(), any());
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions enviarCodigo(String paraCorreo) throws Exception {
        return enviarCodigo(paraCorreo, null);
    }

    /** Con subdominio, el código es para registrarse como cliente de esa tienda. */
    private ResultActions enviarCodigo(String paraCorreo, String subdominio) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo", paraCorreo);
        if (subdominio != null) {
            cuerpo.put("subdominio", subdominio);
        }
        return mvc.perform(post("/api/auth/registro/codigo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private String pedirCodigo(String paraCorreo) throws Exception {
        return pedirCodigo(paraCorreo, null);
    }

    /** Pide el código y lo lee del asunto del correo que se "envió" (simulado). */
    private String pedirCodigo(String paraCorreo, String subdominio) throws Exception {
        enviarCodigo(paraCorreo, subdominio).andExpect(status().isOk());
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        verify(correos, org.mockito.Mockito.atLeastOnce())
                .enviar(eq(correo), asunto.capture(), anyString(), isNull(), isNull());
        String texto = asunto.getValue();
        return texto.substring(texto.length() - 6);
    }

    private ResultActions registrarCliente(String subdominio, String codigo) throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("subdominio", subdominio);
        r.put("usuario", usuario);
        r.put("correo", correo);
        r.put("contrasena", "Clave1234");
        r.put("nombres", "Cliente");
        r.put("primerApellido", "Verifica");
        r.put("telefono", "+51987654321");
        r.put("codigoVerificacion", codigo);
        return mvc.perform(post("/api/auth/registro/cliente")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)));
    }

    private ResultActions registrar(String codigo) throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("usuario", usuario);
        r.put("correo", correo);
        r.put("contrasena", "Clave1234");
        r.put("nombres", "Prueba");
        r.put("primerApellido", "Verifica");
        r.put("tipoDocumento", 1);               // DNI
        r.put("numeroDocumento", dni);
        r.put("codigoVerificacion", codigo);
        return mvc.perform(post("/api/auth/registro/dueno")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)));
    }

    private int filas(String deCorreo) {
        return jdbc.queryForObject("SELECT count(*) FROM verificaciones_correo WHERE correo = ?", Integer.class, deCorreo);
    }
}
