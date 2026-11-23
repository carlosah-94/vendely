package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import curso.fullstack.shopify.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Límites del plan (issue #9, bloque 4).
 *
 * El frontend reconoce un bloqueo por plan porque el mensaje del 403 contiene
 * "Mejora tu plan" (esErrorDePlan en frontend/src/services/api.js): con ese texto muestra el
 * aviso para mejorar en vez de la pantalla de acceso denegado. Por eso las pruebas comprueban
 * el texto, no solo el código.
 *
 * Planes (V5__planes_plantillas.sql): 1 Gratis (5 productos, nada extra) · 2 Básico (100
 * productos, personalizar y más métodos de pago) · 3 Pro (sin tope, todo).
 */
class PlanServiceTest extends PruebaIntegracion {

    private static final String MEJORA = "Mejora tu plan";

    @Autowired
    private PlanService planService;

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tiendaGratis() {
        tienda = datos.nuevaTienda();   // toda tienda nueva empieza en el plan Gratis
    }

    // ------------------------------------------------------------------ productos

    @Test
    void elPlanGratisCortaAlPasarElTopeDeProductos() throws Exception {
        for (int i = 0; i < 5; i++) {
            datos.nuevoProducto(tienda.idTienda(), "10.00", 1);
        }

        crearProducto()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString(MEJORA)))
                .andExpect(jsonPath("$.mensaje").value(containsString("hasta 5 productos")));

        assertThat(planService.productosUsados(tienda.idTienda())).isEqualTo(5);
    }

    @Test
    void conEspacioEnElPlanElProductoSeCrea() throws Exception {
        for (int i = 0; i < 4; i++) {
            datos.nuevoProducto(tienda.idTienda(), "10.00", 1);
        }

        crearProducto().andExpect(status().isCreated());   // es el quinto: todavía entra

        assertThat(planService.productosUsados(tienda.idTienda())).isEqualTo(5);
    }

    @Test
    void elPlanProNoTieneTopeDeProductos() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 3);
        for (int i = 0; i < 5; i++) {
            datos.nuevoProducto(tienda.idTienda(), "10.00", 1);
        }

        crearProducto().andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ funciones por plan

    /**
     * Qué permite cada plan. Una fila por (plan, función): true = permitido.
     * Si alguien cambia los planes en la base o en PlanService sin querer, esta tabla lo nota.
     */
    @ParameterizedTest(name = "plan {0} · {1} → {2}")
    @CsvSource({
            "1, PERSONALIZAR,        false",
            "1, PLANTILLAS,          false",
            "1, ENVIO_INTERNACIONAL, false",
            "1, METODOS_PAGO,        false",
            "2, PERSONALIZAR,        true",
            "2, PLANTILLAS,          false",
            "2, ENVIO_INTERNACIONAL, false",
            "2, METODOS_PAGO,        true",
            "3, PERSONALIZAR,        true",
            "3, PLANTILLAS,          true",
            "3, ENVIO_INTERNACIONAL, true",
            "3, METODOS_PAGO,        true"})
    void cadaPlanPermiteSoloLoSuyo(int plan, FuncionPlan funcion, boolean permitido) {
        datos.cambiarPlan(tienda.idTienda(), plan);

        assertThat(planService.permite(tienda.idTienda(), funcion)).isEqualTo(permitido);
        if (permitido) {
            assertThatCode(() -> planService.exigir(tienda.idTienda(), funcion)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> planService.exigir(tienda.idTienda(), funcion))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining(MEJORA)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void laComisionDependeDelPlan() {
        assertThat(planService.comision(tienda.idTienda())).isEqualByComparingTo("5.00");
        datos.cambiarPlan(tienda.idTienda(), 2);
        assertThat(planService.comision(tienda.idTienda())).isEqualByComparingTo("2.00");
        datos.cambiarPlan(tienda.idTienda(), 3);
        assertThat(planService.comision(tienda.idTienda())).isEqualByComparingTo("0.50");
    }

    // ------------------------------------------------------------------ los endpoints respetan el plan

    @Test
    void personalizarElColorConPlanGratisResponde403() throws Exception {
        actualizarTienda("\"colorPrincipal\":\"#1D4ED8\"")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString(MEJORA)));
    }

    @Test
    void personalizarElColorConPlanBasicoSePuede() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 2);
        actualizarTienda("\"colorPrincipal\":\"#1D4ED8\"").andExpect(status().isOk());
    }

    @Test
    void elegirPlantillaConPlanBasicoResponde403YConProSePuede() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 2);
        actualizarTienda("\"plantilla\":\"boutique\"")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString(MEJORA)));

        datos.cambiarPlan(tienda.idTienda(), 3);
        actualizarTienda("\"plantilla\":\"boutique\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantilla").value("boutique"));
    }

    @Test
    void elEnvioInternacionalSoloConPlanPro() throws Exception {
        tarifaInternacional("CL")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString(MEJORA)));

        datos.cambiarPlan(tienda.idTienda(), 3);
        tarifaInternacional("CL").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions crearProducto() throws Exception {
        return mvc.perform(post("/api/panel/tiendas/" + tienda.idTienda() + "/productos")
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Producto por la API " + System.nanoTime() + "\",\"precio\":25.00,\"stock\":3}"));
    }

    /** PUT de la tienda con los datos obligatorios más el campo que se quiere cambiar. */
    private ResultActions actualizarTienda(String campoExtra) throws Exception {
        return mvc.perform(put("/api/panel/tiendas/" + tienda.idTienda())
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Mi tienda\",\"correoContacto\":\"ventas@prueba.pe\"," + campoExtra + "}"));
    }

    private ResultActions tarifaInternacional(String pais) throws Exception {
        return mvc.perform(put("/api/panel/tiendas/" + tienda.idTienda() + "/envios/internacional/" + pais)
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"costo\":45.00,\"diasEntregaMin\":5,\"diasEntregaMax\":10}"));
    }
}
