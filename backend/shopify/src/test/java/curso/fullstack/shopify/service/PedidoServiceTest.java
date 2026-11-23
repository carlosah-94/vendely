package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Stock: que el inventario nunca quede descuadrado (issue #9, bloque 3).
 *
 * Una venta es "todo o nada": PedidoService descuenta el stock línea por línea dentro de una
 * transacción, y si una línea falla, @Transactional tiene que devolver también lo que ya se
 * descontó de las anteriores. Estas pruebas NO usan @Transactional en la clase a propósito: así
 * la transacción del service se confirma o se deshace de verdad, como en producción.
 */
class PedidoServiceTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tienda() {
        tienda = datos.nuevaTienda();
    }

    @Test
    void siFaltaStockEnLaSegundaLineaElInventarioQuedaExactamenteComoEstaba() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long pantalon = datos.nuevoProducto(tienda.idTienda(), "90.00", 2);

        // La primera línea alcanza (3 de 10) y se descuenta; la segunda pide 5 de 2 y falla.
        vender(camisa, 3, pantalon, 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("Stock insuficiente")));

        assertThat(datos.stock(camisa)).isEqualTo(10);    // se revirtió el descuento de la primera
        assertThat(datos.stock(pantalon)).isEqualTo(2);
        assertThat(pedidosDeLaTienda()).isZero();         // y no quedó ningún pedido a medias
    }

    @Test
    void dosLineasConLaMismaVarianteSeJuntanEnUna() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);

        long idPedido = crearPedido(tienda, camisa, 2, camisa, 3);

        // La llave de detalle_pedido es (id_pedido, id_variante): dos filas iguales no caben.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM detalle_pedido WHERE id_pedido = ?",
                Integer.class, idPedido)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cantidad FROM detalle_pedido WHERE id_pedido = ?",
                Integer.class, idPedido)).isEqualTo(5);
        assertThat(datos.stock(camisa)).isEqualTo(5);
    }

    @Test
    void unaVentaDescuentaElStockDeCadaLinea() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long gorra = datos.nuevoProducto(tienda.idTienda(), "20.00", 4);

        crearPedido(tienda, camisa, 3, gorra, 4);

        assertThat(datos.stock(camisa)).isEqualTo(7);
        assertThat(datos.stock(gorra)).isZero();          // vender la última unidad sí se puede
    }

    @Test
    void eliminarUnaVentaDevuelveElStockExactoDeCadaLinea() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long gorra = datos.nuevoProducto(tienda.idTienda(), "20.00", 6);
        long idPedido = crearPedido(tienda, camisa, 3, gorra, 1);

        mvc.perform(delete("/api/panel/tiendas/" + tienda.idTienda() + "/pedidos/" + idPedido)
                        .header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isNoContent());

        assertThat(datos.stock(camisa)).isEqualTo(10);
        assertThat(datos.stock(gorra)).isEqualTo(6);
        assertThat(pedidosDeLaTienda()).isZero();
    }

    @Test
    void unaVentaConPagoAprobadoNoSePuedeEliminar() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long idPedido = crearPedido(tienda, camisa, 2);
        jdbc.update("INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) VALUES (?, 3, ?, 1, now())",
                idPedido, totalDe(idPedido));

        mvc.perform(delete("/api/panel/tiendas/" + tienda.idTienda() + "/pedidos/" + idPedido)
                        .header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isConflict());

        assertThat(datos.stock(camisa)).isEqualTo(8);     // no se devolvió nada
        assertThat(pedidosDeLaTienda()).isEqualTo(1);
    }

    @ParameterizedTest(name = "un producto en estado {0} ({1}) no se puede vender")
    @CsvSource({"0, borrador", "2, archivado"})
    void unProductoQueNoEstaActivoNoSePuedeVender(int estado, String nombre) throws Exception {
        long oculto = datos.nuevoProducto(tienda.idTienda(), "50.00", 10, estado);

        vender(oculto, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.endsWith("no está a la venta")));

        assertThat(datos.stock(oculto)).isEqualTo(10);
        assertThat(pedidosDeLaTienda()).isZero();
    }

    @Test
    void unProductoDeOtraTiendaNoSePuedeVender() throws Exception {
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        long ajeno = datos.nuevoProducto(otra.idTienda(), "50.00", 10);

        vender(ajeno, 1).andExpect(status().isNotFound());

        assertThat(datos.stock(ajeno)).isEqualTo(10);
    }

    @Test
    void elTotalLoCalculaLaBaseConElEnvio() throws Exception {
        long camisa = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);

        long idPedido = crearPedido(tienda, camisa, 2);

        // 2 × 50.00 + 10.00 de la tarifa de envío de DatosPrueba
        assertThat(totalDe(idPedido)).isEqualByComparingTo("110.00");
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions vender(long... variantesYCantidades) throws Exception {
        return mvc.perform(post("/api/panel/tiendas/" + tienda.idTienda() + "/pedidos")
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ventaJson(tienda, variantesYCantidades)));
    }

    private int pedidosDeLaTienda() {
        return jdbc.queryForObject("SELECT count(*) FROM pedidos WHERE id_tienda = ?", Integer.class, tienda.idTienda());
    }
}
