package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V17: el WhatsApp de la tienda (botón en la vitrina) y el kit de inicio según lo que vende.
 */
class WhatsappRubroTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void preparar() {
        tienda = datos.nuevaTienda();   // plan Gratis: el WhatsApp entra en todos los planes
    }

    // ------------------------------------------------------------------ WhatsApp

    @Test
    void elDuenoPoneSuWhatsappYLaVitrinaLoMuestra() throws Exception {
        actualizar("\"whatsapp\":\"+51987654321\"").andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").value("+51987654321"));
        vitrina().andExpect(jsonPath("$.whatsapp").value("+51987654321"));

        // Guardar otros datos sin mandar el WhatsApp no lo borra (null = no cambia).
        actualizar("\"descripcion\":\"Hola\"").andExpect(jsonPath("$.whatsapp").value("+51987654321"));

        // Vacío = quitar el botón.
        actualizar("\"whatsapp\":\"\"").andExpect(status().isOk());
        vitrina().andExpect(jsonPath("$.whatsapp").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unWhatsappSinCodigoDePaisSeRechaza() throws Exception {
        actualizar("\"whatsapp\":\"987654321\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.whatsapp").value(containsString("código de país")));
        actualizar("\"whatsapp\":\"+51 987 654 321\"").andExpect(status().isBadRequest());
    }

    @Test
    void laBaseTambienRechazaUnNumeroMalo() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE tiendas SET whatsapp = '987654321' WHERE id_tienda = ?", tienda.idTienda()));
    }

    // ------------------------------------------------------------------ kit por rubro

    @Test
    void unaPasteleriaEmpiezaConSusCategoriasYSuAnuncio() throws Exception {
        String sub = "pasteleria-" + System.nanoTime();
        crear(sub, "comida").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rubro").value("comida"))
                .andExpect(jsonPath("$.descripcion").value(containsString("anticipación")));

        long id = jdbc.queryForObject("SELECT id_tienda FROM tiendas WHERE subdominio = ?", Long.class, sub);
        List<String> slugs = jdbc.queryForList(
                "SELECT slug FROM categorias WHERE id_tienda = ? ORDER BY id_categoria", String.class, id);
        assertThat(slugs).containsExactly("tortas", "postres", "bocaditos", "pedidos-especiales");
        assertThat(jdbc.queryForObject("SELECT texto_anuncio FROM tiendas WHERE id_tienda = ?", String.class, id))
                .contains("24 horas");
    }

    @Test
    void lasTildesNoRompenElSlug() throws Exception {
        String sub = "tecno-" + System.nanoTime();
        crear(sub, "tecnologia").andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id_tienda FROM tiendas WHERE subdominio = ?", Long.class, sub);
        assertThat(jdbc.queryForList("SELECT slug FROM categorias WHERE id_tienda = ?", String.class, id))
                .contains("computo");
    }

    @Test
    void sinRubroOOtroLaTiendaEmpiezaEnBlanco() throws Exception {
        String sub = "en-blanco-" + System.nanoTime();
        crear(sub, null).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id_tienda FROM tiendas WHERE subdominio = ?", Long.class, sub);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categorias WHERE id_tienda = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void unRubroQueNoExisteSeRechaza() throws Exception {
        crear("rubro-raro-" + System.nanoTime(), "armas").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions actualizar(String campoExtra) throws Exception {
        return mvc.perform(put("/api/panel/tiendas/" + tienda.idTienda())
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Mi tienda\",\"correoContacto\":\"ventas@prueba.pe\"," + campoExtra + "}"));
    }

    private ResultActions vitrina() throws Exception {
        return mvc.perform(get("/api/public/tiendas/" + tienda.subdominio())).andExpect(status().isOk());
    }

    private ResultActions crear(String subdominio, String rubro) throws Exception {
        String r = rubro == null ? "" : ",\"rubro\":\"" + rubro + "\"";
        return mvc.perform(post("/api/panel/tiendas").header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Tienda nueva\",\"subdominio\":\"" + subdominio
                        + "\",\"correoContacto\":\"t@prueba.pe\"" + r + "}"));
    }
}
