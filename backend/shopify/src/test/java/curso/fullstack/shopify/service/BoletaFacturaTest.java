package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * V25: boleta (B001) y factura (F001) con la numeración de la SUNAT: SERIE-CORRELATIVO de 8
 * dígitos, asignada al aprobarse el pago, propia de cada tienda, sin repetirse ni saltarse.
 */
class BoletaFacturaTest extends PruebaIntegracion {

    private static final String RUC_CLIENTE = "20100066603";   // RUC con dígito verificador correcto

    private DatosPrueba.Tienda tienda;
    private long barato;   // S/ 50
    private long caro;     // S/ 800 (una boleta así debe identificar al cliente)

    @BeforeEach
    void preparar() {
        tienda = datos.nuevaTienda();
        barato = datos.nuevoProducto(tienda.idTienda(), "50.00", 20);
        caro = datos.nuevoProducto(tienda.idTienda(), "800.00", 5);
    }

    @Test
    void laBoletaRecibeSuNumeroAlPagarseYSiguenEnOrden() throws Exception {
        long p1 = crear(barato, null);
        verPedido(p1).andExpect(jsonPath("$.tipoComprobante").value("BOLETA"))
                .andExpect(jsonPath("$.numeroComprobante").value(nullValue()));   // aún sin pagar
        long p2 = crear(barato, null);
        pagar(p2);   // se paga primero el segundo: el número va por orden de pago
        pagar(p1);
        verPedido(p2).andExpect(jsonPath("$.numeroComprobante").value("B001-00000001"));
        verPedido(p1).andExpect(jsonPath("$.numeroComprobante").value("B001-00000002"));
    }

    @Test
    void laFacturaLlevaRucYRazonSocialYTieneSuPropiaSerie() throws Exception {
        long boleta = crear(barato, null);
        long factura = crear(barato, Map.of("tipo", "FACTURA", "tipoDocumento", "RUC",
                "numeroDocumento", RUC_CLIENTE, "razonSocial", "Gloria S.A."));
        pagar(boleta);
        pagar(factura);
        verPedido(factura).andExpect(jsonPath("$.tipoComprobante").value("FACTURA"))
                .andExpect(jsonPath("$.numeroComprobante").value("F001-00000001"))
                .andExpect(jsonPath("$.clienteDocNumero").value(RUC_CLIENTE))
                .andExpect(jsonPath("$.clienteRazonSocial").value("Gloria S.A."));
        // El PDF se llama como el comprobante.
        mvc.perform(get("/api/cliente/pedidos/" + factura + "/comprobante").header("Authorization", token(tienda.idCliente())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("F001-00000001.pdf")));
    }

    @Test
    void unaFacturaMalLlenadaSeRechaza() throws Exception {
        comprar(barato, Map.of("tipo", "FACTURA", "tipoDocumento", "RUC", "numeroDocumento", "20100066604",
                "razonSocial", "Gloria S.A.")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("no es válido")));
        comprar(barato, Map.of("tipo", "FACTURA", "tipoDocumento", "RUC", "numeroDocumento", RUC_CLIENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("razón social")));
        comprar(barato, Map.of("tipo", "BOLETA", "tipoDocumento", "RUC", "numeroDocumento", RUC_CLIENTE))
                .andExpect(status().isBadRequest());
        comprar(barato, Map.of("tipo", "BOLETA", "tipoDocumento", "DNI", "numeroDocumento", "1234567"))
                .andExpect(status().isBadRequest());
        comprar(barato, Map.of("tipo", "TICKET")).andExpect(status().isBadRequest());
        // Ningún intento fallido gastó stock.
        assertThat(datos.stock(barato)).isEqualTo(20);
    }

    @Test
    void unaBoletaDesde700SolesPideElDni() throws Exception {
        comprar(caro, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("DNI")));
        assertThat(datos.stock(caro)).isEqualTo(5);   // el stock se devolvió
        comprar(caro, Map.of("tipo", "BOLETA", "tipoDocumento", "DNI", "numeroDocumento", "45632187"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteDocNumero").value("45632187"));
    }

    @Test
    void cadaTiendaTieneSuPropiaNumeracion() throws Exception {
        long aqui = crear(barato, null);
        pagar(aqui);
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        long variante = datos.nuevoProducto(otra.idTienda(), "30.00", 5);
        long alla = crearPedido(otra, variante, 1);   // venta desde el panel: boleta
        pagar(alla);
        assertThat(numero(aqui)).isEqualTo("B001-00000001");
        assertThat(numero(alla)).isEqualTo("B001-00000001");
    }

    @Test
    void unComprobanteEmitidoNoSeCambia() throws Exception {
        long p = crear(barato, null);
        pagar(p);
        assertThrows(Exception.class, () -> jdbc.update("UPDATE pedidos SET comprobante_numero = 99 WHERE id_pedido = ?", p));
        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE pedidos SET cliente_doc_tipo = 'DNI', cliente_doc_numero = '12345678' WHERE id_pedido = ?", p));
        // Y nadie puede ponerse un número a mano al crear el pedido: la base lo ignora.
        assertThat(numero(crear(barato, null))).isNull();
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM series_comprobante WHERE id_tienda = ? AND serie = 'B001'",
                Integer.class, tienda.idTienda())).isEqualTo(1);
    }

    @Test
    void elImporteEnLetras() {
        assertThat(MontoEnLetras.de(new BigDecimal("110.00"), "PEN")).isEqualTo("CIENTO DIEZ CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("1521.50"), "PEN"))
                .isEqualTo("MIL QUINIENTOS VEINTIUNO CON 50/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("21000"), "PEN")).isEqualTo("VEINTIUN MIL CON 00/100 SOLES");
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions comprar(long variante, Map<String, String> comprobante) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idDireccionEnvio", tienda.idDireccion());
        cuerpo.put("lineas", List.of(Map.of("idVariante", variante, "cantidad", 1)));
        if (comprobante != null) cuerpo.put("comprobante", comprobante);
        return mvc.perform(post("/api/cliente/pedidos").header("Authorization", token(tienda.idCliente()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    private long crear(long variante, Map<String, String> comprobante) throws Exception {
        return objectMapper.readTree(comprar(variante, comprobante).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("idPedido").asLong();
    }

    private ResultActions verPedido(long idPedido) throws Exception {
        return mvc.perform(get("/api/cliente/pedidos/" + idPedido).header("Authorization", token(tienda.idCliente())))
                .andExpect(status().isOk());
    }

    private void pagar(long idPedido) {
        jdbc.update("INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) "
                + "SELECT id_pedido, 2, total, 1, now() FROM pedidos WHERE id_pedido = ?", idPedido);
    }

    private String numero(long idPedido) {
        return jdbc.queryForObject("""
                SELECT comprobante_serie || '-' || lpad(comprobante_numero::text, 8, '0') FROM pedidos WHERE id_pedido = ?""",
                String.class, idPedido);
    }
}
