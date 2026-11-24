package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ofertas automáticas (panel → Descuentos): se aplican solas al vender, la tienda pública muestra
 * el precio rebajado, y una oferta con la que ya se vendió conserva sus condiciones.
 */
class OfertaServiceTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
    }

    @Test
    void unaOfertaPorcentajeRebajaLaVentaYQuedaEnCadaLinea() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long gorra = datos.nuevoProducto(tienda.idTienda(), "20.00", 10);
        long idOferta = crearOferta("PORCENTAJE", "20", null, null, producto(camisa));

        long idPedido = crearPedido(tienda, camisa, 2, gorra, 1);

        // camisa: 50 - 20 % = 40 × 2 = 80; gorra sin oferta: 20 → subtotal 100
        assertThat(subtotalDe(idPedido)).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT id_oferta FROM detalle_pedido WHERE id_pedido = ? AND id_variante = ?",
                Long.class, idPedido, camisa)).isEqualTo(idOferta);
        assertThat(jdbc.queryForObject("SELECT descuento_unitario FROM detalle_pedido WHERE id_pedido = ? AND id_variante = ?",
                BigDecimal.class, idPedido, camisa)).isEqualByComparingTo("10.00");
        assertThat(jdbc.queryForObject("SELECT id_oferta FROM detalle_pedido WHERE id_pedido = ? AND id_variante = ?",
                Long.class, idPedido, gorra)).isNull();
    }

    @Test
    void conDosOfertasSeAplicaLaQueMasRebaja() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        crearOferta("PORCENTAJE", "10", null, null, producto(camisa));       // rebaja 5
        long mejor = crearOferta("MONTO_FIJO", "15", null, null, producto(camisa)); // rebaja 15

        long idPedido = crearPedido(tienda, camisa, 1);

        assertThat(subtotalDe(idPedido)).isEqualByComparingTo("35.00");
        assertThat(jdbc.queryForObject("SELECT id_oferta FROM detalle_pedido WHERE id_pedido = ?",
                Long.class, idPedido)).isEqualTo(mejor);
    }

    @Test
    void unaOfertaPausadaProgramadaOVencidaNoSeAplica() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long pausada = crearOferta("PORCENTAJE", "50", null, null, producto(camisa));
        cambiarEstado(pausada, false).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PAUSADA"));
        crearOferta("PORCENTAJE", "50", OffsetDateTime.now().plusDays(1), null, producto(camisa));
        crearOferta("PORCENTAJE", "50", OffsetDateTime.now().minusDays(5), OffsetDateTime.now().minusDays(1),
                producto(camisa));

        long idPedido = crearPedido(tienda, camisa, 1);

        assertThat(subtotalDe(idPedido)).isEqualByComparingTo("50.00");
    }

    @Test
    void laTiendaPublicaMuestraElPrecioRebajadoYElDeAntesTachado() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long idProducto = producto(camisa);
        crearOferta("PORCENTAJE", "20", null, null, idProducto);

        mvc.perform(get("/api/public/tiendas/" + tienda.subdominio() + "/productos/" + idProducto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(40.0))
                .andExpect(jsonPath("$.precioComparacion").value(50.0))
                .andExpect(jsonPath("$.oferta.tipoDescuento").value("PORCENTAJE"))
                .andExpect(jsonPath("$.variantes[0].precio").value(40.0));
    }

    @Test
    void unaOfertaYaUsadaNoCambiaSuValorNiSeBorraPeroSiSePausa() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long idOferta = crearOferta("PORCENTAJE", "20", null, null, producto(camisa));
        crearPedido(tienda, camisa, 1);

        mvc.perform(put(ruta() + "/" + idOferta).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("PORCENTAJE", "30", null, null, producto(camisa))))
                .andExpect(status().isConflict());
        mvc.perform(delete(ruta() + "/" + idOferta).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isConflict());
        cambiarEstado(idOferta, false).andExpect(status().isOk());

        mvc.perform(get(ruta()).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unidadesVendidas").value(1))
                .andExpect(jsonPath("$[0].estado").value("PAUSADA"));
    }

    @Test
    void unaOfertaSinUsoSeBorra() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long idOferta = crearOferta("MONTO_FIJO", "5", null, null, producto(camisa));

        mvc.perform(delete(ruta() + "/" + idOferta).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ofertas WHERE id_oferta = ?", Integer.class, idOferta))
                .isZero();
    }

    @Test
    void noSeAceptaUnProductoDeOtraTiendaNiUnPorcentajeDe100() throws Exception {
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        long ajeno = datos.nuevoProducto(otra.idTienda(), "50.00", 10);
        long propio = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);

        enviar(json("PORCENTAJE", "20", null, null, producto(ajeno))).andExpect(status().isBadRequest());
        enviar(json("PORCENTAJE", "100", null, null, producto(propio))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ apoyo

    private String ruta() {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/ofertas";
    }

    private long producto(long idVariante) {
        return jdbc.queryForObject("SELECT id_producto FROM variantes WHERE id_variante = ?", Long.class, idVariante);
    }

    private BigDecimal subtotalDe(long idPedido) {
        return jdbc.queryForObject("SELECT subtotal FROM pedidos WHERE id_pedido = ?", BigDecimal.class, idPedido);
    }

    private String json(String tipo, String valor, OffsetDateTime inicio, OffsetDateTime fin, long... productos)
            throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Oferta de prueba");
        cuerpo.put("motivo", "TEMPORADA");
        cuerpo.put("tipoDescuento", tipo);
        cuerpo.put("valor", new BigDecimal(valor));
        cuerpo.put("fechaInicio", inicio == null ? null : inicio.toString());
        cuerpo.put("fechaFin", fin == null ? null : fin.toString());
        cuerpo.put("productos", java.util.Arrays.stream(productos).boxed().toList());
        return objectMapper.writeValueAsString(cuerpo);
    }

    private ResultActions enviar(String json) throws Exception {
        return mvc.perform(post(ruta()).header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long crearOferta(String tipo, String valor, OffsetDateTime inicio, OffsetDateTime fin, long... productos)
            throws Exception {
        return objectMapper.readTree(enviar(json(tipo, valor, inicio, fin, productos))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("idOferta").asLong();
    }

    private ResultActions cambiarEstado(long idOferta, boolean activa) throws Exception {
        return mvc.perform(patch(ruta() + "/" + idOferta + "/estado").header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("activa", activa))));
    }
}
