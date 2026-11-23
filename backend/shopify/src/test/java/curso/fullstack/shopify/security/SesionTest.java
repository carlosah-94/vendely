package curso.fullstack.shopify.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sesión: JwtAuthFilter y AuthService (issue #9, bloque 5).
 *
 * JwtAuthFilter no se conforma con que la firma del token sea válida: en cada petición vuelve a
 * leer la cuenta, así que un token deja de servir si la cuenta se suspende o si cambió su
 * contraseña después de emitirlo. Se prueba contra GET /api/auth/yo, que exige sesión.
 */
class SesionTest extends PruebaIntegracion {

    private static final String RUTA_PROTEGIDA = "/api/auth/yo";

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
    }

    // ------------------------------------------------------------------ JwtAuthFilter

    @Test
    void unTokenValidoDaAcceso() throws Exception {
        conToken(token(tienda.idDueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(tienda.correoDueno()));
    }

    @Test
    void unTokenFirmadoConOtraClaveResponde401() throws Exception {
        var otraClave = Keys.hmacShaKeyFor("una-clave-distinta-que-no-es-la-del-servidor-1234".getBytes(StandardCharsets.UTF_8));
        String falso = Jwts.builder()
                .subject(String.valueOf(tienda.idDueno()))
                .claim("rol", "ADMIN_TIENDA")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otraClave)
                .compact();

        conToken("Bearer " + falso).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenAlteradoResponde401() throws Exception {
        String bueno = token(tienda.idDueno());
        // Se cambia el primer carácter de la firma (el último no sirve: puede llevar solo bits de relleno).
        int firma = bueno.lastIndexOf('.') + 1;
        char c = bueno.charAt(firma);
        String alterado = bueno.substring(0, firma) + (c == 'A' ? 'B' : 'A') + bueno.substring(firma + 1);

        conToken(alterado).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenVencidoResponde401() throws Exception {
        JwtService vencido = new JwtService("clave-de-las-pruebas-automatizadas-de-vendely-0123456789", -1000);
        String t = vencido.generarToken(loginRepository.findById(tienda.idDueno()).orElseThrow());

        conToken("Bearer " + t).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenEmitidoAntesDelCambioDeContrasenaResponde401() throws Exception {
        String anterior = token(tienda.idDueno());
        // La contraseña cambió "después": el token viejo ya no sirve aunque su firma sea válida.
        jdbc.update("UPDATE login SET contrasena_cambiada_en = now() + interval '5 seconds' WHERE id_login = ?",
                tienda.idDueno());

        conToken(anterior).andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarLaContrasenaPorLaApiInvalidaElTokenAnteriorYDaUnoNuevo() throws Exception {
        String anterior = token(tienda.idDueno());
        Thread.sleep(1100);   // el iat del token va en segundos: el cambio tiene que caer en otro segundo

        String respuesta = mvc.perform(post("/api/auth/cambiar-contrasena")
                        .header("Authorization", anterior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"" + DatosPrueba.CONTRASENA
                                + "\",\"contrasenaNueva\":\"OtraClave5678\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String nuevo = "Bearer " + objectMapper.readTree(respuesta).get("token").asText();

        conToken(anterior).andExpect(status().isUnauthorized());
        conToken(nuevo).andExpect(status().isOk());
    }

    @Test
    void unaCuentaSuspendidaConTokenValidoResponde401() throws Exception {
        String t = token(tienda.idDueno());
        jdbc.update("UPDATE login SET estado = 0 WHERE id_login = ?", tienda.idDueno());

        conToken(t).andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debes iniciar sesión"));
    }

    // ------------------------------------------------------------------ AuthService: inicio de sesión

    @Test
    void conLaContrasenaCorrectaEntraYRecibeUnTokenQueSirve() throws Exception {
        String respuesta = login(tienda.correoDueno(), DatosPrueba.CONTRASENA, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        conToken("Bearer " + objectMapper.readTree(respuesta).get("token").asText()).andExpect(status().isOk());
    }

    @Test
    void correoInexistenteYContrasenaIncorrectaDevuelvenElMismoMensaje() throws Exception {
        String mensaje = "Correo o contraseña incorrectos";

        // Si los mensajes fueran distintos, cualquiera podría averiguar qué correos están registrados.
        login("nadie-" + System.nanoTime() + "@prueba.pe", "Cualquiera123", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        login(tienda.correoDueno(), "ContrasenaMala1", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
    }

    @Test
    void unaCuentaSuspendidaNoPuedeIniciarSesion() throws Exception {
        jdbc.update("UPDATE login SET estado = 0 WHERE id_login = ?", tienda.idDueno());

        login(tienda.correoDueno(), DatosPrueba.CONTRASENA, null).andExpect(status().isForbidden());
    }

    @Test
    void unaCuentaSuspendidaConContrasenaIncorrectaNoRevelaQueEstaSuspendida() throws Exception {
        jdbc.update("UPDATE login SET estado = 0 WHERE id_login = ?", tienda.idDueno());

        // Primero se revisa la contraseña: sin ella no se sabe nada del estado de la cuenta.
        login(tienda.correoDueno(), "ContrasenaMala1", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));
    }

    @Test
    void elClienteIniciaSesionSoloEnSuTienda() throws Exception {
        login(tienda.correoCliente(), DatosPrueba.CONTRASENA, tienda.subdominio()).andExpect(status().isOk());

        // El mismo correo y la misma contraseña, pero en otra tienda: esa cuenta no existe allí.
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        login(tienda.correoCliente(), DatosPrueba.CONTRASENA, otra.subdominio())
                .andExpect(status().isUnauthorized());
        // Y tampoco en el panel (sin subdominio).
        login(tienda.correoCliente(), DatosPrueba.CONTRASENA, null)
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions conToken(String authorization) throws Exception {
        return mvc.perform(get(RUTA_PROTEGIDA).header("Authorization", authorization));
    }

    private ResultActions login(String correo, String contrasena, String subdominio) throws Exception {
        String cuerpo = objectMapper.writeValueAsString(subdominio == null
                ? java.util.Map.of("correo", correo, "contrasena", contrasena)
                : java.util.Map.of("correo", correo, "contrasena", contrasena, "subdominio", subdominio));
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
