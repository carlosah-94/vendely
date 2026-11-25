package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V19: un producto puede pedirle un dato al cliente ("¿Qué texto va en la torta?"). Desde la tienda
 * la respuesta es obligatoria y queda guardada en la línea del pedido para que el dueño la vea.
 */
class PreguntaClienteTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;
    private long torta;

    @BeforeEach
    void tortaConPregunta() {
        tienda = datos.nuevaTienda();
        torta = datos.nuevoProducto(tienda.idTienda(), "80.00", 5);
        jdbc.update("""
                UPDATE productos SET pregunta_cliente = '¿Qué texto va en la torta?'
                 WHERE id_producto = (SELECT id_producto FROM variantes WHERE id_variante = ?)""", torta);
    }

    @Test
    void sinRespuestaNoSeCompra() throws Exception {
        comprar(null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("¿Qué texto va en la torta?")));
        // Y el stock no se tocó: la venta se deshizo completa.
        assertThat(jdbc.queryForObject("SELECT cantidad_disponible FROM inventario WHERE id_variante = ?",
                Integer.class, torta)).isEqualTo(5);
    }

    @Test
    void laRespuestaQuedaEnElPedidoYLaVeElDueno() throws Exception {
        String json = comprar("Feliz cumpleaños, Ana").andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineas[0].datoCliente").value("Feliz cumpleaños, Ana"))
                .andReturn().getResponse().getContentAsString();
        long idPedido = objectMapper.readTree(json).get("idPedido").asLong();

        mvc.perform(get("/api/panel/tiendas/" + tienda.idTienda() + "/pedidos/" + idPedido)
                        .header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineas[0].datoCliente").value("Feliz cumpleaños, Ana"));
    }

    @Test
    void dosTortasConTextosDistintosGuardanLosDos() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idDireccionEnvio", tienda.idDireccion());
        cuerpo.put("lineas", List.of(
                Map.of("idVariante", torta, "cantidad", 1, "datoCliente", "Feliz día, Ana"),
                Map.of("idVariante", torta, "cantidad", 1, "datoCliente", "Felicidades, Luis")));
        mvc.perform(post("/api/cliente/pedidos").header("Authorization", token(tienda.idCliente()))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineas[0].cantidad").value(2))
                .andExpect(jsonPath("$.lineas[0].datoCliente").value("Feliz día, Ana | Felicidades, Luis"));
    }

    @Test
    void elDuenoQueRegistraUnaVentaNoNecesitaResponder() throws Exception {
        crearPedido(tienda, torta, 1);   // venta desde el panel, sin dato del cliente
    }

    @Test
    void laTiendaMuestraLaPregunta() throws Exception {
        long idProducto = jdbc.queryForObject("SELECT id_producto FROM variantes WHERE id_variante = ?", Long.class, torta);
        mvc.perform(get("/api/panel/tiendas/" + tienda.idTienda() + "/productos/" + idProducto)
                        .header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preguntaCliente").value("¿Qué texto va en la torta?"));
    }

    private ResultActions comprar(String dato) throws Exception {
        Map<String, Object> linea = new HashMap<>();
        linea.put("idVariante", torta);
        linea.put("cantidad", 1);
        if (dato != null) linea.put("datoCliente", dato);
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idDireccionEnvio", tienda.idDireccion());
        cuerpo.put("lineas", List.of(linea));
        return mvc.perform(post("/api/cliente/pedidos").header("Authorization", token(tienda.idCliente()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }
}
