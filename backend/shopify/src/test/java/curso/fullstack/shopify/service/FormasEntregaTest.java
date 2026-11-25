package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V18: además del courier, el delivery de la propia tienda a ciertos distritos y el recojo en el
 * local. Caso real: una pastelería que lleva sus tortas a Miraflores el mismo día por S/ 7, o el
 * cliente pasa a recogerlas gratis.
 */
class FormasEntregaTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;
    private long miraflores;   // el distrito de la dirección del cliente de prueba (ubigeo 150122)
    private long torta;

    @BeforeEach
    void pasteleria() {
        tienda = datos.nuevaTienda();
        miraflores = jdbc.queryForObject("SELECT id_division FROM divisiones WHERE id_pais = 'PE' AND codigo = '150122'",
                Long.class);
        torta = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
    }

    // ------------------------------------------------------------------ delivery

    @Test
    void conDeliveryAlDistritoElClienteLoEligeYPagaEseCosto() throws Exception {
        guardarDelivery(miraflores, "{\"costo\":7.00}").andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery", hasSize(1)))
                .andExpect(jsonPath("$.delivery[0].distrito").value("Miraflores"));

        cotizar().andExpect(jsonPath("$.opciones[*].tipo", hasItem("DELIVERY")))
                .andExpect(jsonPath("$.opciones[?(@.tipo == 'DELIVERY')].costo", hasItem(7.0)));

        String json = comprar("DELIVERY").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntrega").value("DELIVERY"))
                .andReturn().getResponse().getContentAsString();
        long idPedido = objectMapper.readTree(json).get("idPedido").asLong();
        assertThat(jdbc.queryForObject("SELECT costo_envio FROM pedidos WHERE id_pedido = ?", BigDecimal.class, idPedido))
                .isEqualByComparingTo("7.00");
    }

    @Test
    void sinDeliveryAEseDistritoNoSePuedeElegir() throws Exception {
        cotizar().andExpect(jsonPath("$.opciones[*].tipo", not(hasItem("DELIVERY"))));
        comprar("DELIVERY").andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("no hace delivery")));
    }

    @Test
    void unaZonaDebeSerUnDistritoDelPaisDeLaTienda() throws Exception {
        long lima = jdbc.queryForObject("SELECT id_division FROM divisiones WHERE id_pais = 'PE' AND nivel = 1 LIMIT 1",
                Long.class);
        guardarDelivery(lima, "{\"costo\":7.00}").andExpect(status().isNotFound());
        // La base también lo exige, aunque alguien se salte el backend.
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO zonas_delivery (id_tienda, id_division, costo) VALUES (?, ?, 5)", tienda.idTienda(), lima));
    }

    @Test
    void elDeliveryPuedeSerGratisDesdeUnMonto() throws Exception {
        guardarDelivery(miraflores, "{\"costo\":7.00,\"envioGratisDesde\":40.00}").andExpect(status().isOk());
        String json = comprar("DELIVERY").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(json).get("costoEnvio").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void alBorrarLaZonaYaNoSeOfrece() throws Exception {
        guardarDelivery(miraflores, "{\"costo\":7.00}").andExpect(status().isOk());
        mvc.perform(delete(ruta("/delivery/" + miraflores)).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.delivery", hasSize(0)));
        comprar("DELIVERY").andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ recojo

    @Test
    void conRecojoActivoElPedidoNoPagaEnvio() throws Exception {
        comprar("RECOJO").andExpect(status().isConflict());

        guardarRecojo("{\"activo\":true,\"direccion\":\"Av. Benavides 456, Miraflores\",\"horario\":\"Lun a sáb 10 a 7\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.recojo.activo").value(true));
        cotizar().andExpect(jsonPath("$.opciones[?(@.tipo == 'RECOJO')].detalle",
                hasItem(containsString("Benavides"))));

        String json = comprar("RECOJO").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntrega").value("RECOJO"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(json).get("costoEnvio").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void sinDireccionNoSeActivaElRecojo() throws Exception {
        guardarRecojo("{\"activo\":true}").andExpect(status().isBadRequest());
    }

    @Test
    void unPedidoParaRecogerSeMarcaListoSinNumeroDeSeguimiento() throws Exception {
        guardarRecojo("{\"activo\":true,\"direccion\":\"Av. Benavides 456\"}").andExpect(status().isOk());
        String json = comprar("RECOJO").andReturn().getResponse().getContentAsString();
        long idPedido = objectMapper.readTree(json).get("idPedido").asLong();
        pagar(idPedido);

        mvc.perform(patch(rutaPedido(idPedido)).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO"));
    }

    @Test
    void porCourierSiguenSiendoObligatoriosEmpresaYNumero() throws Exception {
        String json = comprar(null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoEntrega").value("ENVIO"))
                .andReturn().getResponse().getContentAsString();
        long idPedido = objectMapper.readTree(json).get("idPedido").asLong();
        pagar(idPedido);

        mvc.perform(patch(rutaPedido(idPedido)).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"empresaEnvio\":\"Olva\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("seguimiento")));
        mvc.perform(patch(rutaPedido(idPedido)).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empresaEnvio\":\"Olva\",\"numeroSeguimiento\":\"OLV1\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ apoyo

    private String ruta(String resto) {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/envios" + resto;
    }

    private String rutaPedido(long idPedido) {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/pedidos/" + idPedido + "/envio";
    }

    private ResultActions guardarDelivery(long idDivision, String cuerpo) throws Exception {
        return mvc.perform(put(ruta("/delivery/" + idDivision)).header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions guardarRecojo(String cuerpo) throws Exception {
        return mvc.perform(put(ruta("/recojo")).header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions cotizar() throws Exception {
        return mvc.perform(get("/api/cliente/direcciones/" + tienda.idDireccion() + "/envio")
                        .param("subtotal", "50.00")
                        .header("Authorization", token(tienda.idCliente())))
                .andExpect(status().isOk());
    }

    private ResultActions comprar(String tipoEntrega) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idDireccionEnvio", tienda.idDireccion());
        cuerpo.put("lineas", List.of(Map.of("idVariante", torta, "cantidad", 1)));
        if (tipoEntrega != null) cuerpo.put("tipoEntrega", tipoEntrega);
        return mvc.perform(post("/api/cliente/pedidos").header("Authorization", token(tienda.idCliente()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    private void pagar(long idPedido) {
        jdbc.update("INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) VALUES (?, 3, ?, 1, now())",
                idPedido, totalDe(idPedido));
    }
}
