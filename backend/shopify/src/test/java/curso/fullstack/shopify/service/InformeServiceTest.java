package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Informes del panel: solo cuentan los pedidos pagados, agrupan por día y por producto, y
 * distinguen a los clientes nuevos de los que ya habían comprado.
 */
class InformeServiceTest extends PruebaIntegracion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
    }

    @Test
    void soloCuentaLosPedidosPagadosYSumaPorProducto() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long gorra = datos.nuevoProducto(tienda.idTienda(), "20.00", 10);
        long p1 = pagado(crearPedido(tienda, camisa, 2));
        long p2 = pagado(crearPedido(tienda, gorra, 1));
        crearPedido(tienda, camisa, 1);                      // sin pagar: no cuenta

        JsonNode informe = informe("");

        BigDecimal ventas = totalDe(p1).add(totalDe(p2));
        assertThat(informe.at("/actual/pedidos").asLong()).isEqualTo(2);
        assertThat(informe.at("/actual/ventas").decimalValue()).isEqualByComparingTo(ventas);
        assertThat(informe.at("/actual/unidades").asLong()).isEqualTo(3);
        assertThat(informe.at("/actual/clientes").asLong()).isEqualTo(1);
        assertThat(informe.at("/actual/ticketPromedio").decimalValue())
                .isEqualByComparingTo(ventas.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP));
        assertThat(informe.at("/anterior/pedidos").asLong()).isZero();

        // Productos: primero el que más dinero dejó (camisa: 2 × 50 = 100)
        assertThat(informe.at("/productos/0/unidades").asLong()).isEqualTo(2);
        assertThat(informe.at("/productos/0/ingresos").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(informe.at("/productos/1/ingresos").decimalValue()).isEqualByComparingTo("20.00");

        // Por día: los últimos 30 días, hoy con los 2 pedidos
        JsonNode dias = informe.get("porDia");
        assertThat(dias).hasSize(30);
        assertThat(dias.get(29).get("fecha").asText()).isEqualTo(LocalDate.now(LIMA).toString());
        assertThat(dias.get(29).get("pedidos").asLong()).isEqualTo(2);

        assertThat(informe.at("/metodosPago/0/metodo").asText()).isEqualTo("MERCADO_PAGO");
        assertThat(informe.at("/metodosPago/0/pedidos").asLong()).isEqualTo(2);
        assertThat(informe.at("/estados/0/estado").asText()).isEqualTo("SIN_ENVIAR");
        assertThat(informe.at("/clientes/nuevos").asLong()).isEqualTo(1);
        assertThat(informe.at("/clientes/recurrentes").asLong()).isZero();
    }

    @Test
    void laGananciaDescuentaElCostoYSacaelImpuestoYElEnvio() throws Exception {
        // Precio 50 con IGV (18 %), costo 30. Dos unidades: subtotal 100, envío 10, total 110, IGV 16.78.
        long camisa = datos.nuevoProductoConCosto(tienda.idTienda(), "50.00", "30.00", 10);
        pagado(crearPedido(tienda, camisa, 2));

        JsonNode g = informe("").at("/actual/ganancia");

        // Ingresos sin IGV ni envío: 100 × (110 - 16.78) / 110 = 84.75. Costo: 2 × 30 = 60.
        assertThat(g.get("ingresosNetos").decimalValue()).isEqualByComparingTo("84.75");
        assertThat(g.get("costo").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(g.get("ganancia").decimalValue()).isEqualByComparingTo("24.75");
        assertThat(g.get("margen").decimalValue()).isEqualByComparingTo("29.2");
        assertThat(g.get("pedidosConCosto").asLong()).isEqualTo(1);
        assertThat(g.get("pedidosSinCosto").asLong()).isZero();
    }

    @Test
    void unPedidoConUnProductoSinCostoNoInflaLaGanancia() throws Exception {
        long conCosto = datos.nuevoProductoConCosto(tienda.idTienda(), "50.00", "30.00", 10);
        long sinCosto = datos.nuevoProducto(tienda.idTienda(), "20.00", 10);   // anterior a V21: sin costo
        pagado(crearPedido(tienda, conCosto, 1));
        pagado(crearPedido(tienda, sinCosto, 1));

        JsonNode informe = informe("");

        assertThat(informe.at("/actual/pedidos").asLong()).isEqualTo(2);
        assertThat(informe.at("/actual/ganancia/pedidosConCosto").asLong()).isEqualTo(1);
        assertThat(informe.at("/actual/ganancia/pedidosSinCosto").asLong()).isEqualTo(1);
        // Solo cuenta el pedido con costo: 50 × (60 - 9.15) / 60 = 42.37 menos 30 = 12.37 (aprox.)
        assertThat(informe.at("/actual/ganancia/costo").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(informe.at("/actual/ganancia/ganancia").decimalValue()).isBetween(new BigDecimal("12.30"), new BigDecimal("12.45"));
        // El producto sin costo sale en la lista pero sin ganancia.
        assertThat(informe.at("/productos/0/ganancia").isNull()).isFalse();
        assertThat(informe.at("/productos/1/ganancia").isNull()).isTrue();
    }

    @Test
    void elInventarioMuestraElDineroInvertidoYLaGananciaPotencial() throws Exception {
        datos.nuevoProductoConCosto(tienda.idTienda(), "59.00", "20.00", 10);   // capital 200, venta 590
        datos.nuevoProducto(tienda.idTienda(), "10.00", 5);                      // sin costo: se avisa, no entra

        JsonNode inv = informe("").get("inventario");

        assertThat(inv.get("capitalInvertido").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(inv.get("valorDeVenta").decimalValue()).isEqualByComparingTo("590.00");
        // 590 / 1.18 = 500 sin IGV; menos el capital de 200 = 300.
        assertThat(inv.get("gananciaPotencial").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(inv.get("productosSinCosto").asLong()).isEqualTo(1);
        assertThat(inv.get("unidades").asLong()).isEqualTo(15);
    }

    @Test
    void unClienteQueYaHabiaCompradoAntesEsRecurrente() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long viejo = pagado(crearPedido(tienda, camisa, 1));
        pagado(crearPedido(tienda, camisa, 1));
        // Informe de hoy solamente: el primer pedido se movió a hace 40 días.
        jdbc.update("UPDATE pedidos SET fecha_creacion = now() - interval '40 days' WHERE id_pedido = ?", viejo);

        String hoy = LocalDate.now(LIMA).toString();
        JsonNode informe = informe("&desde=" + hoy + "&hasta=" + hoy);

        assertThat(informe.at("/actual/pedidos").asLong()).isEqualTo(1);
        assertThat(informe.at("/clientes/recurrentes").asLong()).isEqualTo(1);
        assertThat(informe.at("/clientes/nuevos").asLong()).isZero();
        assertThat(informe.get("porDia")).hasSize(1);
    }

    @Test
    void unEmpleadoNoVeLosInformesYUnRangoInvertidoEsError() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, true, true, true);

        mvc.perform(get(ruta("")).header("Authorization", token(empleado))).andExpect(status().isForbidden());
        mvc.perform(get(ruta("&desde=2026-05-10&hasta=2026-05-01")).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ruta("&desde=2024-01-01&hasta=2026-01-01")).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ apoyo

    private long pagado(long idPedido) {
        jdbc.update("INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) VALUES (?, 3, ?, 1, now())",
                idPedido, totalDe(idPedido));
        return idPedido;
    }

    private String ruta(String extra) {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/informes?zona=America/Lima" + extra;
    }

    private JsonNode informe(String extra) throws Exception {
        return objectMapper.readTree(mvc.perform(get(ruta(extra)).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
