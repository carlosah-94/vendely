package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Mi perfil: cada cuenta ve y edita sus propios datos; el dueño, también los de su negocio. */
class PerfilServiceTest extends PruebaIntegracion {

    private static final String RUTA = "/api/cuenta/perfil";

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
    }

    @Test
    void elDuenoVeSusDatosYLosDeSuNegocioSinLaContrasena() throws Exception {
        mvc.perform(get(RUTA).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN_TIENDA"))
                .andExpect(jsonPath("$.correo").isNotEmpty())
                .andExpect(jsonPath("$.negocio.numeroDocumento").isNotEmpty())
                .andExpect(jsonPath("$.negocio.tipoDocumento").isNotEmpty())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.totpSecreto").doesNotExist());
    }

    @Test
    void elDuenoCambiaSuNombreTelefonoYRazonSocial() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombres", "  Lucía ");
        cuerpo.put("primerApellido", "Ramos");
        cuerpo.put("segundoApellido", "");
        cuerpo.put("telefono", "+51911222333");
        cuerpo.put("razonSocial", "Modas Lucía S.A.C.");
        cuerpo.put("telefonoComercial", "");

        mvc.perform(put(RUTA).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nombres").value("Lucía"))
                .andExpect(jsonPath("$.perfil.negocio.razonSocial").value("Modas Lucía S.A.C."));

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT nombres, segundo_apellido, telefono FROM login WHERE id_login = ?", tienda.idDueno());
        assertThat(fila.get("nombres")).isEqualTo("Lucía");
        assertThat(fila.get("segundo_apellido")).isNull();   // vacío = sin segundo apellido
        assertThat(fila.get("telefono")).isEqualTo("+51911222333");
    }

    @Test
    void unEmpleadoVeSuPerfilSinNegocioYUnTelefonoMalEscritoEsError() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        mvc.perform(get(RUTA).header("Authorization", token(empleado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("EMPLEADO"))
                .andExpect(jsonPath("$.negocio").doesNotExist());

        mvc.perform(put(RUTA).header("Authorization", token(empleado)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"Ana\",\"primerApellido\":\"Paz\",\"telefono\":\"987654321\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }
}
