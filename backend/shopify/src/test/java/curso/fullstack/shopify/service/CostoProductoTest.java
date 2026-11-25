package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Costo por unidad del producto (V21), para que el dueño vea su ganancia.
 *
 * - Al dueño se le exige: sin costo no hay ganancia que calcular.
 * - Es un dato de las finanzas: un empleado no lo ve, no lo cambia, y el que ya había se conserva.
 * - Viaja con el precio: cambiar el costo cierra el precio vigente y crea otro, y las ventas pasadas
 *   conservan el costo de su día.
 * - La tienda pública nunca lo muestra.
 */
class CostoProductoTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
        datos.cambiarPlan(tienda.idTienda(), 2);   // Básico: con empleados
    }

    @Test
    void alDuenoSeLeExigeElCosto() throws Exception {
        crear(tienda.idDueno(), "{\"nombre\":\"Polo sin costo\",\"precio\":70.00,\"stock\":3}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("Falta el costo")));
    }

    @Test
    void elCostoNoPuedeSerNegativoNiTenerMasDeDosDecimales() throws Exception {
        crear(tienda.idDueno(), "{\"nombre\":\"Polo negativo\",\"precio\":70.00,\"costo\":-1,\"stock\":3}")
                .andExpect(status().isBadRequest());
        crear(tienda.idDueno(), "{\"nombre\":\"Polo decimales\",\"precio\":70.00,\"costo\":10.555,\"stock\":3}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void conCostoElProductoSeCreaYElDuenoLoVe() throws Exception {
        crear(tienda.idDueno(), "{\"nombre\":\"Polo con costo\",\"precio\":70.00,\"costo\":40.00,\"stock\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costo").value(40.00));

        mvc.perform(get(ruta()).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].costo").value(40.00));
    }

    @Test
    void conVariantesCadaUnaPuedeTenerSuCostoYSinCostoUsaElDelProducto() throws Exception {
        String cuerpo = """
                {"nombre":"Polo con tallas","precio":70.00,"costo":40.00,"stock":2,
                 "opciones":[{"nombre":"Talla","valores":["S","M"]}],
                 "variantes":[{"valores":["S"]},{"valores":["M"],"costo":45.00}]}""";
        JsonNode producto = leer(crear(tienda.idDueno(), cuerpo).andExpect(status().isCreated()));

        assertThat(producto.at("/variantes/0/costo").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(producto.at("/variantes/1/costo").decimalValue()).isEqualByComparingTo("45.00");
    }

    @Test
    void unCostoMayorQueElPrecioSePermiteEsUnaPerdidaNoUnError() throws Exception {
        crear(tienda.idDueno(), "{\"nombre\":\"Liquidacion\",\"precio\":20.00,\"costo\":30.00,\"stock\":3}")
                .andExpect(status().isCreated());
    }

    @Test
    void cambiarElCostoCierraElPrecioVigenteYCreaOtro() throws Exception {
        JsonNode producto = leer(crear(tienda.idDueno(),
                "{\"nombre\":\"Polo historial\",\"precio\":70.00,\"costo\":40.00,\"stock\":3}").andExpect(status().isCreated()));
        long id = producto.get("idProducto").asLong();
        long variante = producto.get("idVariante").asLong();

        // Mismo precio, costo nuevo: la base no deja editar el costo, así que se crea otro precio.
        actualizar(tienda.idDueno(), id,
                "{\"nombre\":\"Polo historial\",\"precio\":70.00,\"costo\":55.00,\"stock\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costo").value(55.00));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM precios WHERE id_variante = ?", Integer.class, variante))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT costo FROM precios WHERE id_variante = ? AND fecha_fin IS NOT NULL", BigDecimal.class, variante))
                .isEqualByComparingTo("40.00");
    }

    @Test
    void laBaseNoDejaEditarElCostoDeUnPrecioYaCreado() {
        long variante = datos.nuevoProductoConCosto(tienda.idTienda(), "70.00", "40.00", 5);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE precios SET costo = 1 WHERE id_variante = ?", variante));
    }

    @Test
    void elEmpleadoNoVeElCostoNiLoCambiaYElQueHabiaSeConserva() throws Exception {
        JsonNode producto = leer(crear(tienda.idDueno(),
                "{\"nombre\":\"Polo del empleado\",\"precio\":70.00,\"costo\":40.00,\"stock\":3}").andExpect(status().isCreated()));
        long id = producto.get("idProducto").asLong();
        long variante = producto.get("idVariante").asLong();
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), false, true, false, false);   // permiso de productos

        // No lo ve: ni en la lista ni al editar.
        mvc.perform(get(ruta()).header("Authorization", token(empleado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].costo").doesNotExist());

        // Edita el producto (cambia el precio) y manda un costo cualquiera: se ignora y se conserva el 40.
        actualizar(empleado, id, "{\"nombre\":\"Polo del empleado\",\"precio\":75.00,\"costo\":1.00,\"stock\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costo").doesNotExist())
                .andExpect(jsonPath("$.precio").value(75.00));

        assertThat(jdbc.queryForObject(
                "SELECT costo FROM precios WHERE id_variante = ? AND fecha_fin IS NULL", BigDecimal.class, variante))
                .isEqualByComparingTo("40.00");
    }

    @Test
    void elEmpleadoPuedeCrearUnProductoSinCostoYElDuenoLoVeSinCosto() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), false, true, false, false);

        crear(empleado, "{\"nombre\":\"Producto del empleado\",\"precio\":30.00,\"stock\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costo").doesNotExist());

        // El dueño lo ve sin costo (null): la pantalla le avisa que falta cargarlo.
        mvc.perform(get(ruta()).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].costo").doesNotExist());
    }

    @Test
    void laTiendaPublicaNuncaMuestraElCosto() throws Exception {
        crear(tienda.idDueno(), "{\"nombre\":\"Polo publico\",\"precio\":70.00,\"costo\":40.00,\"stock\":3}")
                .andExpect(status().isCreated());

        String json = mvc.perform(get("/api/public/tiendas/" + tienda.subdominio() + "/productos"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("Polo publico");
        assertThat(json).doesNotContain("40.00").doesNotContain("\"costo\":40");
    }

    @Test
    void laVentaConservaElCostoDeSuDiaAunqueDespuesCambie() throws Exception {
        JsonNode producto = leer(crear(tienda.idDueno(),
                "{\"nombre\":\"Polo vendido\",\"precio\":70.00,\"costo\":40.00,\"stock\":5}").andExpect(status().isCreated()));
        long id = producto.get("idProducto").asLong();
        long variante = producto.get("idVariante").asLong();
        long pedido = crearPedido(tienda, variante, 1);

        actualizar(tienda.idDueno(), id, "{\"nombre\":\"Polo vendido\",\"precio\":70.00,\"costo\":90.00,\"stock\":4}")
                .andExpect(status().isOk());

        // La línea del pedido sigue apuntando al precio de ese día, con su costo de 40.
        assertThat(jdbc.queryForObject("""
                SELECT pr.costo FROM detalle_pedido d JOIN precios pr ON pr.id_precio = d.id_precio
                 WHERE d.id_pedido = ?""", BigDecimal.class, pedido)).isEqualByComparingTo("40.00");
    }

    // ------------------------------------------------------------------ apoyo

    private String ruta() {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/productos";
    }

    private ResultActions crear(long idLogin, String json) throws Exception {
        return mvc.perform(post(ruta()).header("Authorization", token(idLogin))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions actualizar(long idLogin, long idProducto, String json) throws Exception {
        return mvc.perform(put(ruta() + "/" + idProducto).header("Authorization", token(idLogin))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
