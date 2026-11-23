package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import curso.fullstack.shopify.evento.PagoAprobadoEvent;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pagos: donde un error cuesta dinero (issue #9, bloque 2).
 *
 * La regla de PagoService es no creerle nunca al navegador: el paymentId que trae la URL de
 * regreso solo sirve para CONSULTAR el pago en Mercado Pago, y se aprueba únicamente si la
 * pasarela dice approved, el external_reference es ESTE pedido y el monto es exactamente el
 * total. Mercado Pago se simula con un @MockBean: ninguna prueba sale a internet.
 *
 * Se entra por el endpoint real del cliente (POST /api/cliente/pedidos/{id}/pago/confirmar),
 * así también se prueba que el pedido tiene que ser del cliente del token.
 */
@RecordApplicationEvents
class PagoServiceTest extends PruebaIntegracion {

    @Autowired
    private ApplicationEvents eventos;

    private DatosPrueba.Tienda tienda;
    private long idPedido;
    private BigDecimal total;

    @BeforeEach
    void pedidoSinPagar() throws Exception {
        tienda = datos.nuevaTienda();
        long variante = datos.nuevoProducto(tienda.idTienda(), "80.00", 10);
        idPedido = crearPedido(tienda, variante, 1);
        total = totalDe(idPedido);   // 80.00 + 10.00 de envío, lo calcula el trigger de la base
        when(mercadoPago.estaConfigurado()).thenReturn(true);
    }

    @Test
    void unPagoAprobadoPorElMontoExactoApruebaElPedido() throws Exception {
        pasarelaDice("1001", "approved", idPedido, total);

        confirmar(tienda.idCliente(), idPedido, "1001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"));

        assertThat(pagosAprobados(idPedido)).isEqualTo(1);
        assertThat(eventosDePagoAprobado()).isEqualTo(1);
    }

    @Test
    void unMontoDistintoAlTotalNoAprueba() throws Exception {
        pasarelaDice("1002", "approved", idPedido, total.subtract(new BigDecimal("0.01")));

        confirmar(tienda.idCliente(), idPedido, "1002")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El pago no corresponde a este pedido"));

        assertThat(pagosAprobados(idPedido)).isZero();
        assertThat(eventosDePagoAprobado()).isZero();
    }

    @Test
    void unPagoConElExternalReferenceDeOtroPedidoNoAprueba() throws Exception {
        // Mercado Pago dice que el pago es del pedido 999999: no se puede usar para este.
        pasarelaDice("1003", "approved", 999_999L, total);

        confirmar(tienda.idCliente(), idPedido, "1003")
                .andExpect(status().isBadRequest());

        assertThat(pagosAprobados(idPedido)).isZero();
    }

    @Test
    void unPagoYaUsadoEnOtroPedidoResponde409() throws Exception {
        // El pago 1004 aprueba el pedido A...
        pasarelaDice("1004", "approved", idPedido, total);
        confirmar(tienda.idCliente(), idPedido, "1004").andExpect(status().isOk());

        // ...y después el mismo cliente intenta usarlo para pagar otro pedido B.
        long otroPedido = crearPedido(tienda, datos.nuevoProducto(tienda.idTienda(), "80.00", 10), 1);
        confirmar(tienda.idCliente(), otroPedido, "1004")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ese pago ya se usó en otro pedido"));

        assertThat(pagosAprobados(otroPedido)).isZero();
    }

    @Test
    void confirmarDosVecesDejaUnSoloPagoAprobadoYUnSoloEvento() throws Exception {
        pasarelaDice("1005", "approved", idPedido, total);

        confirmar(tienda.idCliente(), idPedido, "1005").andExpect(status().isOk());
        // El cliente recarga la pantalla de resultado: no se cobra ni se avisa de nuevo.
        confirmar(tienda.idCliente(), idPedido, "1005")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"));

        assertThat(pagosAprobados(idPedido)).isEqualTo(1);
        assertThat(eventosDePagoAprobado()).isEqualTo(1);
        verify(mercadoPago, times(1)).consultarPago("1005");   // la segunda vez ni pregunta
    }

    @Test
    void unPaymentIdConFormatoInvalidoResponde404SinPreguntarALaPasarela() throws Exception {
        // Pasa la validación del DTO (letras y guiones) pero no es un id de Mercado Pago (solo dígitos)
        // ni una orden de PayPal creada para este pedido.
        confirmar(tienda.idCliente(), idPedido, "no-es-un-id")
                .andExpect(status().isNotFound());

        verify(mercadoPago, never()).consultarPago(anyString());
        assertThat(pagosAprobados(idPedido)).isZero();
    }

    @Test
    void unPagoRechazadoQuedaRechazadoYNoApruebaElPedido() throws Exception {
        pasarelaDice("1006", "rejected", idPedido, total);

        confirmar(tienda.idCliente(), idPedido, "1006")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("RECHAZADO"));

        assertThat(pagosAprobados(idPedido)).isZero();
        assertThat(eventosDePagoAprobado()).isZero();
    }

    @Test
    void elClienteDeOtraTiendaNoPuedeConfirmarElPagoDeUnPedidoAjeno() throws Exception {
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        pasarelaDice("1007", "approved", idPedido, total);

        confirmar(otra.idCliente(), idPedido, "1007")
                .andExpect(status().isNotFound());

        verify(mercadoPago, never()).consultarPago(anyString());
        assertThat(pagosAprobados(idPedido)).isZero();
    }

    @Test
    void otroClienteDeLaMismaTiendaTampocoPuedeConfirmarlo() throws Exception {
        long vecino = datos.nuevoCliente(tienda.idTienda());
        pasarelaDice("1008", "approved", idPedido, total);

        confirmar(vecino, idPedido, "1008")
                .andExpect(status().isNotFound());

        assertThat(pagosAprobados(idPedido)).isZero();
    }

    @Test
    void alAprobarseSeGuardaLaComisionDelPlan() throws Exception {
        pasarelaDice("1009", "approved", idPedido, total);   // plan Gratis: 5 % de comisión

        confirmar(tienda.idCliente(), idPedido, "1009").andExpect(status().isOk());

        BigDecimal comision = jdbc.queryForObject(
                "SELECT comision_plataforma FROM pedidos WHERE id_pedido = ?", BigDecimal.class, idPedido);
        assertThat(comision).isEqualByComparingTo(total.multiply(new BigDecimal("0.05")));
    }

    // ------------------------------------------------------------------ apoyo

    /** Lo que responde Mercado Pago al consultar ese pago. */
    private void pasarelaDice(String paymentId, String estado, long externalReference, BigDecimal monto) {
        when(mercadoPago.consultarPago(paymentId)).thenReturn(Map.of(
                "id", paymentId,
                "status", estado,
                "external_reference", String.valueOf(externalReference),
                "transaction_amount", monto));
    }

    private ResultActions confirmar(long idCliente, long pedido, String paymentId) throws Exception {
        return mvc.perform(post("/api/cliente/pedidos/" + pedido + "/pago/confirmar")
                .header("Authorization", token(idCliente))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":\"" + paymentId + "\"}"));
    }

    private int pagosAprobados(long pedido) {
        return jdbc.queryForObject("SELECT count(*) FROM pagos WHERE id_pedido = ? AND estado = 1", Integer.class, pedido);
    }

    private long eventosDePagoAprobado() {
        return eventos.stream(PagoAprobadoEvent.class).filter(e -> e.idPedido().equals(idPedido)).count();
    }
}
