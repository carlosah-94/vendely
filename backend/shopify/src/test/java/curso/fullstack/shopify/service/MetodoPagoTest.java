package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Elegir el método de pago: PayPal o Mercado Pago.
 *
 * Una tienda ofrece un método solo si (1) el servidor tiene configurada esa pasarela y (2) su
 * plan lo incluye: Gratis, solo el método base (el primero configurado); Básico y Pro, todos.
 * Se prueba por los dos lados que usan esa regla: lo que ve el frontend en la vitrina
 * (metodosPago) y lo que acepta el backend al iniciar el pago.
 *
 * Las pasarelas son @MockBean: "configurada" es lo que devuelve estaConfigurado().
 */
class MetodoPagoTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;
    private long idPedido;

    @BeforeEach
    void pedidoSinPagar() throws Exception {
        tienda = datos.nuevaTienda();   // plan Gratis
        idPedido = crearPedido(tienda, datos.nuevoProducto(tienda.idTienda(), "80.00", 10), 1);

        // Respuestas mínimas de las pasarelas al crear el pago.
        when(mercadoPago.crearPreferencia(anyMap())).thenReturn(Map.of(
                "sandbox_init_point", "https://sandbox.mercadopago.com.pe/checkout/prueba"));
        when(payPal.crearOrden(anyMap())).thenReturn(Map.of(
                "id", "ORDEN-" + System.nanoTime(),
                "links", List.of(Map.of("rel", "payer-action", "href", "https://www.sandbox.paypal.com/checkoutnow?prueba"))));
    }

    // ------------------------------------------------------------------ qué ve el frontend

    @Test
    void conLasDosPasarelasUnaTiendaGratisOfreceSoloElMetodoBase() throws Exception {
        configuradas(true, true);

        vitrina().andExpect(jsonPath("$.metodosPago", contains("PAYPAL")));
    }

    @Test
    void conLasDosPasarelasUnaTiendaBasicoOProOfreceAmbas() throws Exception {
        configuradas(true, true);

        datos.cambiarPlan(tienda.idTienda(), 2);
        vitrina().andExpect(jsonPath("$.metodosPago", contains("PAYPAL", "MERCADO_PAGO")));
        datos.cambiarPlan(tienda.idTienda(), 3);
        vitrina().andExpect(jsonPath("$.metodosPago", contains("PAYPAL", "MERCADO_PAGO")));
    }

    @Test
    void siSoloEstaMercadoPagoEsElMetodoBaseInclusoEnElPlanGratis() throws Exception {
        configuradas(false, true);

        vitrina().andExpect(jsonPath("$.metodosPago", contains("MERCADO_PAGO")));
    }

    @Test
    void unaPasarelaSinCredencialesNoSeOfreceANadie() throws Exception {
        configuradas(true, false);
        datos.cambiarPlan(tienda.idTienda(), 3);   // ni con el plan Pro

        vitrina().andExpect(jsonPath("$.metodosPago", contains("PAYPAL")));
    }

    @Test
    void sinNingunaPasarelaLaListaVieneVacia() throws Exception {
        configuradas(false, false);

        vitrina().andExpect(jsonPath("$.metodosPago", empty()));
    }

    // ------------------------------------------------------------------ iniciar el pago

    @Test
    void elClienteEligeMercadoPago() throws Exception {
        configuradas(true, true);
        datos.cambiarPlan(tienda.idTienda(), 2);

        pagar("{\"metodo\":\"MERCADO_PAGO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago", containsString("mercadopago")));

        verify(mercadoPago).crearPreferencia(anyMap());
        verify(payPal, never()).crearOrden(any());
        assertThat(proveedorDelIntento()).isEqualTo((short) 3);   // 3 = Mercado Pago
    }

    @Test
    void elClienteEligePayPal() throws Exception {
        configuradas(true, true);
        datos.cambiarPlan(tienda.idTienda(), 2);

        pagar("{\"metodo\":\"PAYPAL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago", containsString("paypal")));

        verify(payPal).crearOrden(anyMap());
        verify(mercadoPago, never()).crearPreferencia(any());
        assertThat(proveedorDelIntento()).isEqualTo((short) 2);   // 2 = PayPal
    }

    @Test
    void sinElegirSeUsaElPrimerMetodoComoAntes() throws Exception {
        configuradas(true, true);

        // El frontend anterior a la elección no manda cuerpo: tiene que seguir funcionando.
        mvc.perform(post("/api/cliente/pedidos/" + idPedido + "/pago")
                        .header("Authorization", token(tienda.idCliente())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago", containsString("paypal")));
    }

    @Test
    void elPlanGratisNoPuedeUsarElSegundoMetodo() throws Exception {
        configuradas(true, true);   // la tienda sigue en Gratis

        pagar("{\"metodo\":\"MERCADO_PAGO\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", containsString("Mejora tu plan")));

        verify(mercadoPago, never()).crearPreferencia(any());
        assertThat(intentosDePago()).isZero();
    }

    @Test
    void unaPasarelaSinCredencialesResponde409() throws Exception {
        configuradas(false, true);
        datos.cambiarPlan(tienda.idTienda(), 3);

        pagar("{\"metodo\":\"PAYPAL\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El pago con PayPal no está disponible por ahora"));

        assertThat(intentosDePago()).isZero();
    }

    @Test
    void sinNingunaPasarelaResponde503() throws Exception {
        configuradas(false, false);

        pagar("{\"metodo\":\"PAYPAL\"}").andExpect(status().isServiceUnavailable());
    }

    @Test
    void unMetodoQueNoExisteResponde400() throws Exception {
        configuradas(true, true);

        pagar("{\"metodo\":\"BITCOIN\"}").andExpect(status().isBadRequest());

        assertThat(intentosDePago()).isZero();
    }

    // ------------------------------------------------------------------ apoyo

    private void configuradas(boolean conPayPal, boolean conMercadoPago) {
        when(payPal.estaConfigurado()).thenReturn(conPayPal);
        when(mercadoPago.estaConfigurado()).thenReturn(conMercadoPago);
    }

    private ResultActions vitrina() throws Exception {
        return mvc.perform(get("/api/public/tiendas/" + tienda.subdominio())).andExpect(status().isOk());
    }

    private ResultActions pagar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/cliente/pedidos/" + idPedido + "/pago")
                .header("Authorization", token(tienda.idCliente()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private int intentosDePago() {
        return jdbc.queryForObject("SELECT count(*) FROM pagos WHERE id_pedido = ?", Integer.class, idPedido);
    }

    private short proveedorDelIntento() {
        return jdbc.queryForObject("SELECT proveedor FROM pagos WHERE id_pedido = ?", Short.class, idPedido);
    }
}
