package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import curso.fullstack.shopify.entity.Suscripcion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Conciliación de los pagos de plan (ConciliacionPagosService.conciliarPlanes).
 *
 * El caso real: el dueño abre PayPal para pagar su plan y cierra la pestaña sin volver. Antes
 * ese pago quedaba "Pendiente" para siempre en su historial. Ahora, pasados 30 minutos, se le
 * pregunta a PayPal (simulado aquí) cómo terminó la orden y se cierra.
 */
class ConciliacionPlanesTest extends PruebaIntegracion {

    private static final AtomicInteger ORDENES = new AtomicInteger();

    @Autowired private ConciliacionPagosService conciliacion;

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tiendaGratis() {
        tienda = datos.nuevaTienda();             // plan Gratis (1)
        when(payPal.estaConfigurado()).thenReturn(true);
    }

    @Test
    void unaOrdenQueNadiePagoSeCancelaYElPlanNoCambia() throws Exception {
        long id = pendiente("hace 1 hora");
        when(payPal.consultarOrden(referencia(id))).thenReturn(orden(id, "PAYER_ACTION_REQUIRED"));

        conciliar();

        assertThat(estado(id)).isEqualTo(Suscripcion.CANCELADA);
        assertThat(planDeLaTienda()).isEqualTo(1);
        verify(payPal, never()).capturarOrden(anyString());
    }

    @Test
    void unaOrdenQueYaNoExisteEnPayPalSeCancela() throws Exception {
        long id = pendiente("hace 5 horas");
        when(payPal.consultarOrden(referencia(id))).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        conciliar();

        assertThat(estado(id)).isEqualTo(Suscripcion.CANCELADA);
    }

    @Test
    void siPayPalYaLaCobroSeActivaElPlan() throws Exception {
        long id = pendiente("hace 1 hora");
        when(payPal.consultarOrden(referencia(id))).thenReturn(orden(id, "COMPLETED"));

        conciliar();

        assertThat(estado(id)).isEqualTo(Suscripcion.PAGADA);
        assertThat(planDeLaTienda()).as("pasa a Básico").isEqualTo(2);
    }

    @Test
    void siElDuenoAproboPeroNoVolvioSeCobraYSeActivaElPlan() throws Exception {
        long id = pendiente("hace 1 hora");
        when(payPal.consultarOrden(referencia(id))).thenReturn(orden(id, "APPROVED"));
        when(payPal.capturarOrden(referencia(id))).thenReturn(Map.of("purchase_units", List.of(
                Map.of("payments", Map.of("captures", List.of(Map.of("status", "COMPLETED")))))));

        conciliar();

        assertThat(estado(id)).isEqualTo(Suscripcion.PAGADA);
        assertThat(planDeLaTienda()).isEqualTo(2);
    }

    @Test
    void unPagoRecienIniciadoNoSeToca() throws Exception {
        long id = pendiente("hace 5 minutos");      // el dueño puede seguir en PayPal

        conciliar();

        assertThat(estado(id)).isEqualTo(Suscripcion.PENDIENTE);
        verify(payPal, never()).consultarOrden(referencia(id));
    }

    @Test
    void siPayPalNoRespondeSiguePendienteYLosDemasSeRevisanIgual() throws Exception {
        long caido = pendiente("hace 1 hora");
        long otro = pendiente("hace 1 hora");
        when(payPal.consultarOrden(referencia(caido))).thenThrow(new ResourceAccessException("timeout"));
        when(payPal.consultarOrden(referencia(otro))).thenReturn(orden(otro, "PAYER_ACTION_REQUIRED"));

        conciliar();

        assertThat(estado(caido)).as("se reintenta en 15 minutos").isEqualTo(Suscripcion.PENDIENTE);
        assertThat(estado(otro)).isEqualTo(Suscripcion.CANCELADA);
    }

    @Test
    void sinPayPalConfiguradoNoSeCambiaNada() throws Exception {
        when(payPal.estaConfigurado()).thenReturn(false);
        long id = pendiente("hace 1 hora");

        conciliar();

        assertThat(estado(id)).isEqualTo(Suscripcion.PENDIENTE);
        verify(payPal, never()).consultarOrden(anyString());
    }

    // ------------------------------------------------------------------ apoyo

    private void conciliar() {
        conciliacion.conciliarPlanes(OffsetDateTime.now().minusMinutes(30));
    }

    /** Un pago de plan Básico (15 USD) pendiente, creado hace el tiempo indicado ("hace 1 hora"). */
    private long pendiente(String hace) {
        String intervalo = hace.replace("hace ", "");
        Long id = jdbc.queryForObject("""
                INSERT INTO suscripciones (id_tienda, id_plan, fecha_inicio, fecha_fin, monto_usd, estado,
                                           referencia_pago, fecha_creacion)
                VALUES (?, 2, now(), now() + interval '30 days', 15.00, 0, ?, now() - CAST(? AS interval))
                RETURNING id_suscripcion
                """, Long.class, tienda.idTienda(), "ORDEN-" + ORDENES.incrementAndGet() + "-" + System.nanoTime(),
                intervalo.replace("hora", "hour").replace("horas", "hours").replace("minutos", "minutes"));
        return id;
    }

    private String referencia(long idSuscripcion) {
        return jdbc.queryForObject("SELECT referencia_pago FROM suscripciones WHERE id_suscripcion = ?",
                String.class, idSuscripcion);
    }

    /** Una orden de PayPal como la devuelve GET /v2/checkout/orders/{id}. */
    private static Map<String, Object> orden(long idSuscripcion, String estado) {
        return Map.of("status", estado, "purchase_units", List.of(Map.of(
                "custom_id", "SUS-" + idSuscripcion,
                "amount", Map.of("currency_code", "USD", "value", "15.00"))));
    }

    private short estado(long idSuscripcion) {
        return jdbc.queryForObject("SELECT estado FROM suscripciones WHERE id_suscripcion = ?", Short.class, idSuscripcion);
    }

    private int planDeLaTienda() {
        return jdbc.queryForObject("SELECT id_plan FROM tiendas WHERE id_tienda = ?", Integer.class, tienda.idTienda());
    }
}
