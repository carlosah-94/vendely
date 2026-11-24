package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Códigos de descuento: rebajan el pedido completo, respetan sus reglas (mínimo, usos, una vez
 * por cliente, fechas) y el total que guarda la base cuadra con el descuento.
 */
class CuponServiceTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;
    private long camisa;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
        camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 20);
    }

    @Test
    void unCodigoPorcentajeRebajaElPedidoYElTotalCuadra() throws Exception {
        long idCupon = crearCupon(Map.of("codigo", "VERANO10", "tipoDescuento", "PORCENTAJE", "valor", 10));

        // En minúsculas también sirve: los códigos se guardan en mayúsculas.
        long idPedido = comprar("verano10", 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoCupon").value("VERANO10"))
                .andExpect(jsonPath("$.descuentoCupon").value(10.0))
                .andReturn().getResponse().getContentAsString().transform(this::idPedido);

        Map<String, Object> p = jdbc.queryForMap(
                "SELECT subtotal, descuento_cupon, costo_envio, total, id_cupon FROM pedidos WHERE id_pedido = ?", idPedido);
        assertThat((BigDecimal) p.get("subtotal")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) p.get("descuento_cupon")).isEqualByComparingTo("10.00");
        assertThat(((Number) p.get("id_cupon")).longValue()).isEqualTo(idCupon);
        assertThat((BigDecimal) p.get("total"))
                .isEqualByComparingTo(new BigDecimal("90.00").add((BigDecimal) p.get("costo_envio")));
    }

    @Test
    void elCodigoSeAplicaSobreElPrecioYaRebajadoPorUnaOferta() throws Exception {
        long idProducto = jdbc.queryForObject("SELECT id_producto FROM variantes WHERE id_variante = ?", Long.class, camisa);
        jdbc.update("""
                INSERT INTO ofertas (id_tienda, nombre, motivo, tipo_descuento, valor, fecha_inicio)
                VALUES (?, 'Oferta', 2, 1, 20, now() - interval '1 hour')""", tienda.idTienda());
        jdbc.update("INSERT INTO oferta_productos (id_oferta, id_producto) SELECT max(id_oferta), ? FROM ofertas WHERE id_tienda = ?",
                idProducto, tienda.idTienda());
        crearCupon(Map.of("codigo", "MENOS5", "tipoDescuento", "MONTO_FIJO", "valor", 5));

        long idPedido = idPedido(comprar("MENOS5", 1).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // 50 - 20 % = 40 de subtotal; el código quita 5
        assertThat(jdbc.queryForObject("SELECT subtotal - descuento_cupon FROM pedidos WHERE id_pedido = ?",
                BigDecimal.class, idPedido)).isEqualByComparingTo("35.00");
    }

    @Test
    void sinLaCompraMinimaNoSeAplicaYElStockNoCambia() throws Exception {
        Map<String, Object> cupon = new HashMap<>(Map.of("codigo", "GRANDE", "tipoDescuento", "PORCENTAJE", "valor", 15));
        cupon.put("compraMinima", 200);
        crearCupon(cupon);

        comprar("GRANDE", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("compras desde")));
        assertThat(datos.stock(camisa)).isEqualTo(20);
    }

    @Test
    void unCodigoConUnUsoSeAgotaCuandoSePagaUnPedido() throws Exception {
        Map<String, Object> cupon = new HashMap<>(Map.of("codigo", "UNICO", "tipoDescuento", "MONTO_FIJO", "valor", 5));
        cupon.put("usosMaximos", 1);
        crearCupon(cupon);

        long primero = idPedido(comprar("UNICO", 1).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        // Mientras no se paga, no cuenta como uso
        validar("UNICO", "50").andExpect(status().isOk()).andExpect(jsonPath("$.descuento").value(5.0));

        pagar(primero);
        comprar("UNICO", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("agotó")));
    }

    @Test
    void unaVezPorClienteYUnCodigoPausadoOVencidoNoSirven() throws Exception {
        Map<String, Object> unaVez = new HashMap<>(Map.of("codigo", "BIENVENIDA", "tipoDescuento", "PORCENTAJE", "valor", 10));
        unaVez.put("unaVezPorCliente", true);
        crearCupon(unaVez);
        pagar(idPedido(comprar("BIENVENIDA", 1).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()));
        validar("BIENVENIDA", "50").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ya usaste el código BIENVENIDA"));

        long pausado = crearCupon(Map.of("codigo", "PAUSADO", "tipoDescuento", "PORCENTAJE", "valor", 10));
        mvc.perform(patch(ruta() + "/" + pausado + "/estado").header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}")).andExpect(status().isOk());
        validar("PAUSADO", "50").andExpect(status().isBadRequest());

        Map<String, Object> vencido = new HashMap<>(Map.of("codigo", "VIEJO", "tipoDescuento", "PORCENTAJE", "valor", 10));
        vencido.put("fechaInicio", OffsetDateTime.now().minusDays(5).toString());
        vencido.put("fechaFin", OffsetDateTime.now().minusDays(1).toString());
        crearCupon(vencido);
        validar("VIEJO", "50").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El código VIEJO ya venció"));
        validar("NOEXISTE", "50").andExpect(status().isBadRequest());
    }

    @Test
    void unCodigoUsadoNoSeBorraYNoSeRepiteElCodigo() throws Exception {
        long usado = crearCupon(Map.of("codigo", "USADO", "tipoDescuento", "PORCENTAJE", "valor", 10));
        comprar("USADO", 1).andExpect(status().isCreated());
        long libre = crearCupon(Map.of("codigo", "LIBRE", "tipoDescuento", "PORCENTAJE", "valor", 10));

        mvc.perform(delete(ruta() + "/" + usado).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isConflict());
        mvc.perform(delete(ruta() + "/" + libre).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isNoContent());
        enviarCupon(Map.of("codigo", "usado", "tipoDescuento", "PORCENTAJE", "valor", 5))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ apoyo

    private String ruta() {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/cupones";
    }

    private ResultActions enviarCupon(Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post(ruta()).header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    private long crearCupon(Map<String, Object> cuerpo) throws Exception {
        return objectMapper.readTree(enviarCupon(cuerpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("idCupon").asLong();
    }

    /** El cliente de la tienda compra desde la tienda pública con un código. */
    private ResultActions comprar(String codigo, int cantidad) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idDireccionEnvio", tienda.idDireccion());
        cuerpo.put("lineas", List.of(Map.of("idVariante", camisa, "cantidad", cantidad)));
        cuerpo.put("codigoCupon", codigo);
        return mvc.perform(post("/api/cliente/pedidos").header("Authorization", token(tienda.idCliente()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions validar(String codigo, String subtotal) throws Exception {
        return mvc.perform(post("/api/cliente/cupones/validar").header("Authorization", token(tienda.idCliente()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("codigo", codigo, "subtotal", new BigDecimal(subtotal)))));
    }

    private long idPedido(String json) {
        try {
            return objectMapper.readTree(json).get("idPedido").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un pago aprobado por el total: el trigger T10 revisa que cuadre con el total ya rebajado. */
    private void pagar(long idPedido) {
        jdbc.update("INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) VALUES (?, 3, ?, 1, now())",
                idPedido, totalDe(idPedido));
    }
}
