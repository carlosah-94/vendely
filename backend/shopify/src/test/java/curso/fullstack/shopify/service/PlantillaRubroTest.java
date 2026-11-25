package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V22: plantillas por rubro. La de su rubro la usa cualquier plan; cambiarle colores o letra
 * pide plan Básico; la de otro rubro, plan Pro.
 */
class PlantillaRubroTest extends PruebaIntegracion {

    private static final String HORNO = "\"tema\":\"horno\",\"plantilla\":\"clasica\",\"colorPrincipal\":\"#C2410C\","
            + "\"colorAcento\":\"#F59E0B\",\"fuente\":\"expresiva\",\"estiloBotones\":\"redondo\",\"fondo\":\"blanco\"";
    private static final String CIRCUITO = "\"tema\":\"circuito\",\"plantilla\":\"clasica\",\"colorPrincipal\":\"#4F46E5\","
            + "\"colorAcento\":\"#22D3EE\",\"fuente\":\"moderna\",\"estiloBotones\":\"suave\",\"fondo\":\"blanco\"";

    private DatosPrueba.Tienda tienda;   // plan Gratis

    @BeforeEach
    void preparar() {
        tienda = datos.nuevaTienda();
        jdbc.update("UPDATE tiendas SET rubro = 'comida' WHERE id_tienda = ?", tienda.idTienda());
    }

    @Test
    void unaPasteleriaNaceConLaPlantillaHornoYSeVeAunqueSeaGratis() throws Exception {
        String sub = "horno-" + System.nanoTime();
        crear(sub, "comida").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tema").value("horno"))
                .andExpect(jsonPath("$.colorPrincipal").value("#C2410C"))
                .andExpect(jsonPath("$.diseno.fuente").value("expresiva"));

        // La vitrina (plan Gratis) muestra sus colores y su letra, pero sigue con el sello.
        mvc.perform(get("/api/public/tiendas/" + sub)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("horno"))
                .andExpect(jsonPath("$.colorPrincipal").value("#C2410C"))
                .andExpect(jsonPath("$.diseno.colorAcento").value("#F59E0B"))
                .andExpect(jsonPath("$.diseno.fuente").value("expresiva"))
                .andExpect(jsonPath("$.diseno.mostrarSeccionRubro").value(true))
                .andExpect(jsonPath("$.selloVendely").value(true));
    }

    @Test
    void cadaRubroNaceConSuPropiaPlantilla() throws Exception {
        String sub = "tecno-" + System.nanoTime();
        crear(sub, "tecnologia").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tema").value("circuito"))
                .andExpect(jsonPath("$.colorPrincipal").value("#4F46E5"));
        String deporte = "gym-" + System.nanoTime();
        crear(deporte, "deporte").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tema").value("impulso"))
                .andExpect(jsonPath("$.diseno.fuente").value("impacto"));
    }

    @Test
    void otroRubroEmpiezaConElDisenoDeSiempre() throws Exception {
        String sub = "otro-" + System.nanoTime();
        crear(sub, "otro").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tema").value(nullValue()))
                .andExpect(jsonPath("$.colorPrincipal").value(nullValue()));
    }

    @Test
    void elPlanGratisPuedeAplicarLaPlantillaDeSuRubro() throws Exception {
        actualizar(HORNO).andExpect(status().isOk()).andExpect(jsonPath("$.tema").value("horno"));
        vitrina().andExpect(jsonPath("$.tema").value("horno"))
                .andExpect(jsonPath("$.colorPrincipal").value("#C2410C"));
    }

    @Test
    void laPlantillaDeOtroRubroPidePlanPro() throws Exception {
        actualizar(CIRCUITO).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT tema FROM tiendas WHERE id_tienda = ?", String.class, tienda.idTienda()))
                .isNotEqualTo("circuito");
    }

    @Test
    void cambiarleElColorALaPlantillaPidePlanBasico() throws Exception {
        actualizar(HORNO).andExpect(status().isOk());
        actualizar(HORNO.replace("#C2410C", "#123456")).andExpect(status().isForbidden());
        // Guardar otros datos sin tocar el diseño sigue funcionando en Gratis.
        actualizar(HORNO + ",\"descripcion\":\"Tortas por encargo\"").andExpect(status().isOk());
    }

    @Test
    void laSeccionDelRubroSePuedeOcultarEnCualquierPlan() throws Exception {
        actualizar("\"mostrarSeccionRubro\":false").andExpect(status().isOk())
                .andExpect(jsonPath("$.diseno.mostrarSeccionRubro").value(false));
        vitrina().andExpect(jsonPath("$.diseno.mostrarSeccionRubro").value(false));
    }

    @Test
    void laVitrinaMuestraElRecojoSoloSiEstaActivo() throws Exception {
        vitrina().andExpect(jsonPath("$.recojoDireccion").value(nullValue()));
        jdbc.update("UPDATE tiendas SET recojo_activo = true, recojo_direccion = 'Av. Central 123', "
                + "recojo_horario = 'Lun a sáb, 9 a 6' WHERE id_tienda = ?", tienda.idTienda());
        vitrina().andExpect(jsonPath("$.recojoDireccion").value("Av. Central 123"))
                .andExpect(jsonPath("$.recojoHorario").value("Lun a sáb, 9 a 6"));
    }

    @Test
    void unTemaQueNoExisteSeRechaza() throws Exception {
        actualizar("\"tema\":\"neon\"").andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE tiendas SET tema = 'neon' WHERE id_tienda = ?", tienda.idTienda()));
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions actualizar(String campos) throws Exception {
        return mvc.perform(put("/api/panel/tiendas/" + tienda.idTienda())
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Mi tienda\",\"correoContacto\":\"ventas@prueba.pe\"," + campos + "}"));
    }

    private ResultActions vitrina() throws Exception {
        return mvc.perform(get("/api/public/tiendas/" + tienda.subdominio())).andExpect(status().isOk());
    }

    private ResultActions crear(String subdominio, String rubro) throws Exception {
        return mvc.perform(post("/api/panel/tiendas").header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Tienda nueva\",\"subdominio\":\"" + subdominio
                        + "\",\"correoContacto\":\"t@prueba.pe\",\"rubro\":\"" + rubro + "\"}"));
    }
}
