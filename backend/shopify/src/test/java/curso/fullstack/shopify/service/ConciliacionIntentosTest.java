package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.entity.Pago;
import curso.fullstack.shopify.exception.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Intentos de pago que nadie pagó (PagoService.conciliar → VENCIDO).
 *
 * Antes, si el cliente iba a Mercado Pago y no pagaba, su intento quedaba PENDIENTE para siempre
 * y la conciliación lo volvía a consultar cada 15 minutos. Ahora se cierra (VENCIDO) cuando ya
 * no hay nada que esperar, sin perder un pago que llegue tarde.
 */
class ConciliacionIntentosTest extends PruebaIntegracion {

    @Autowired private PagoService pagoService;

    private DatosPrueba.Tienda tienda;
    private long idPedido;
    private BigDecimal total;

    @BeforeEach
    void pedidoSinPagar() throws Exception {
        tienda = datos.nuevaTienda();
        long variante = datos.nuevoProducto(tienda.idTienda(), "80.00", 10);
        idPedido = crearPedido(tienda, variante, 1);
        total = totalDe(idPedido);
        when(mercadoPago.estaConfigurado()).thenReturn(true);
        when(payPal.estaConfigurado()).thenReturn(true);
    }

    // ------------------------------------------------------------------ Mercado Pago

    @Test
    void unIntentoDeMercadoPagoSinPagoDespuesDeTresDiasVence() {
        long intento = intento(Pago.MERCADO_PAGO, null, "4 days");

        assertThat(pagoService.conciliar(intento)).isTrue();

        assertThat(estado(intento)).isEqualTo(Pago.VENCIDO);
    }

    @Test
    void unIntentoRecienteSinPagoSigueEsperando() {
        long intento = intento(Pago.MERCADO_PAGO, null, "1 hour");   // el cliente puede seguir pagando

        assertThat(pagoService.conciliar(intento)).isFalse();

        assertThat(estado(intento)).isEqualTo(Pago.PENDIENTE);
    }

    @Test
    void sinMercadoPagoConfiguradoNoSeTocaAunqueSeaViejo() {
        when(mercadoPago.estaConfigurado()).thenReturn(false);
        long intento = intento(Pago.MERCADO_PAGO, null, "10 days");

        assertThat(pagoService.conciliar(intento)).isFalse();

        assertThat(estado(intento)).isEqualTo(Pago.PENDIENTE);
    }

    @Test
    void siElClientePagaDespuesDeVencidoElPagoIgualSeRegistra() throws Exception {
        long intento = intento(Pago.MERCADO_PAGO, null, "4 days");
        pagoService.conciliar(intento);
        when(mercadoPago.consultarPago("777001")).thenReturn(Map.of(
                "id", "777001", "status", "approved",
                "external_reference", String.valueOf(idPedido), "transaction_amount", total));

        mvc.perform(post("/api/cliente/pedidos/" + idPedido + "/pago/confirmar")
                        .header("Authorization", token(tienda.idCliente()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"777001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"));

        assertThat(estado(intento)).as("el intento vencido no cambia").isEqualTo(Pago.VENCIDO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos WHERE id_pedido = ? AND estado = 1", Integer.class, idPedido))
                .as("se registra en un intento nuevo").isEqualTo(1);
    }

    // ------------------------------------------------------------------ pedido ya pagado

    @Test
    void unIntentoQueSobraPorqueElPedidoYaSePagoVence() {
        jdbc.update("""
                INSERT INTO pagos (id_pedido, proveedor, monto, estado, referencia_transaccion, fecha_pago)
                VALUES (?, 3, ?, 1, ?, now())""", idPedido, total, "MP-" + System.nanoTime());
        long sobrante = intento(Pago.MERCADO_PAGO, null, "1 hour");

        assertThat(pagoService.conciliar(sobrante)).isTrue();

        assertThat(estado(sobrante)).isEqualTo(Pago.VENCIDO);
    }

    // ------------------------------------------------------------------ PayPal

    @Test
    void unaOrdenDePayPalQueYaNoExisteVence() {
        String orden = "ORD-" + System.nanoTime();
        long intento = intento(Pago.PAYPAL, orden, "5 hours");
        when(payPal.consultarOrden(orden)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        assertThat(pagoService.conciliar(intento)).isTrue();

        assertThat(estado(intento)).isEqualTo(Pago.VENCIDO);
    }

    @Test
    void siPayPalNoRespondeElIntentoSiguePendiente() {
        String orden = "ORD-" + System.nanoTime();
        long intento = intento(Pago.PAYPAL, orden, "1 hour");
        when(payPal.consultarOrden(orden)).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> pagoService.conciliar(intento)).isInstanceOf(ApiException.class);

        assertThat(estado(intento)).as("se reintenta en 15 minutos").isEqualTo(Pago.PENDIENTE);
    }

    // ------------------------------------------------------------------ cómo se muestra

    @Test
    void unPedidoConSuIntentoVencidoSeMuestraSinPagoNoRechazado() {
        assertThat(PedidoResumenResponse.nombreEstadoPago(Pago.VENCIDO)).isEqualTo("SIN_PAGO");
    }

    // ------------------------------------------------------------------ apoyo

    /** Un intento PENDIENTE del pedido, creado hace el intervalo indicado ("4 days", "1 hour"). */
    private long intento(short proveedor, String referencia, String hace) {
        return jdbc.queryForObject("""
                INSERT INTO pagos (id_pedido, proveedor, monto, estado, referencia_transaccion, fecha_creacion)
                VALUES (?, ?, ?, 0, ?, now() - CAST(? AS interval)) RETURNING id_pago""",
                Long.class, idPedido, proveedor, total, referencia, hace);
    }

    private short estado(long idPago) {
        return jdbc.queryForObject("SELECT estado FROM pagos WHERE id_pago = ?", Short.class, idPago);
    }
}
