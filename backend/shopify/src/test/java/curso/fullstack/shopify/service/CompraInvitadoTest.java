package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import curso.fullstack.shopify.security.ClaveInvitado;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Compra como invitado: sin cuenta, con una clave secreta por dirección y por pedido. Sin la clave
 * (o con la de otro pedido u otra tienda) todo responde 404, como si no existiera.
 */
class CompraInvitadoTest extends PruebaIntegracion {

    private static final String CLAVE = "X-Clave-Invitado";

    @Autowired private ClaveInvitado claves;

    private DatosPrueba.Tienda tienda;
    private long polo;   // S/ 50
    private String base;

    @BeforeEach
    void preparar() {
        tienda = datos.nuevaTienda();
        polo = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        base = "/api/public/tiendas/" + tienda.subdominio() + "/invitado";
    }

    @Test
    void unInvitadoCompraPagaYVeSuPedidoConSuClave() throws Exception {
        JsonNode dir = crearDireccion(tienda);
        long idDireccion = dir.get("datos").get("idDireccion").asLong();
        String claveDir = dir.get("clave").asText();

        // Cotizar el envío: con la clave sí, sin ella 404.
        mvc.perform(get(base + "/direcciones/" + idDireccion + "/envio").param("subtotal", "50").header(CLAVE, claveDir))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true));
        mvc.perform(get(base + "/direcciones/" + idDireccion + "/envio").param("subtotal", "50"))
                .andExpect(status().isNotFound());

        JsonNode creado = leer(pedir(idDireccion, claveDir, "ana@correo.pe", null).andExpect(status().isCreated()));
        long idPedido = creado.get("datos").get("idPedido").asLong();
        String clave = creado.get("clave").asText();
        assertThat(creado.get("datos").get("idLogin").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT correo_contacto FROM pedidos WHERE id_pedido = ?", String.class, idPedido))
                .isEqualTo("ana@correo.pe");
        assertThat(datos.stock(polo)).isEqualTo(9);

        // Ver el pedido: solo con su clave.
        mvc.perform(get(base + "/pedidos/" + idPedido).header(CLAVE, clave))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numeroComprobante").value(nullValue()));
        mvc.perform(get(base + "/pedidos/" + idPedido)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/pedidos/" + idPedido).header(CLAVE, claveDir)).andExpect(status().isNotFound());

        // Pagado: recibe su boleta numerada y puede descargarla.
        jdbc.update("INSERT INTO pagos (id_pedido, proveedor, monto, estado, fecha_pago) "
                + "SELECT id_pedido, 2, total, 1, now() FROM pedidos WHERE id_pedido = ?", idPedido);
        mvc.perform(get(base + "/pedidos/" + idPedido).header(CLAVE, clave))
                .andExpect(jsonPath("$.numeroComprobante").value("B001-00000001"));
        mvc.perform(get(base + "/pedidos/" + idPedido + "/comprobante").header(CLAVE, clave))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("B001-00000001.pdf")));
    }

    @Test
    void laClaveDeUnaTiendaNoSirveEnOtra() throws Exception {
        JsonNode dir = crearDireccion(tienda);
        long idDireccion = dir.get("datos").get("idDireccion").asLong();
        String claveDir = dir.get("clave").asText();
        pedir(idDireccion, "clave-inventada", "ana@correo.pe", null).andExpect(status().isNotFound());

        DatosPrueba.Tienda otra = datos.nuevaTienda();
        long otroProducto = datos.nuevoProducto(otra.idTienda(), "30.00", 5);
        Map<String, Object> cuerpo = cuerpo(idDireccion, claveDir, "ana@correo.pe", null);
        cuerpo.put("lineas", List.of(Map.of("idVariante", otroProducto, "cantidad", 1)));
        mvc.perform(post("/api/public/tiendas/" + otra.subdominio() + "/invitado/pedidos")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isNotFound());
        assertThat(datos.stock(polo)).isEqualTo(10);
        assertThat(datos.stock(otroProducto)).isEqualTo(5);
    }

    @Test
    void elPedidoDeUnClienteNoSeAbrePorLaPuertaDeInvitados() throws Exception {
        long delCliente = crearPedido(tienda, polo, 1);
        String claveFabricada = claves.firmar("pedido", tienda.idTienda(), delCliente);
        // Aunque la clave sea "correcta", el pedido tiene dueño: no es de un invitado.
        mvc.perform(get(base + "/pedidos/" + delCliente).header(CLAVE, claveFabricada)).andExpect(status().isNotFound());
        // Y la dirección de un cliente no la puede usar un invitado.
        String claveDirCliente = claves.firmar("direccion", tienda.idTienda(), tienda.idDireccion());
        pedir(tienda.idDireccion(), claveDirCliente, "ana@correo.pe", null).andExpect(status().isBadRequest());
    }

    @Test
    void unaTiendaQuePideCuentaNoAceptaInvitados() throws Exception {
        jdbc.update("UPDATE tiendas SET permite_invitados = FALSE WHERE id_tienda = ?", tienda.idTienda());
        enviarDireccion(tienda, "+51987654321").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString("iniciar sesión")));
    }

    @Test
    void losDatosDelInvitadoSeValidan() throws Exception {
        enviarDireccion(tienda, "987654321").andExpect(status().isBadRequest());   // sin +51
        long idDireccion = crearDireccion(tienda).get("datos").get("idDireccion").asLong();
        String claveDir = claves.firmar("direccion", tienda.idTienda(), idDireccion);
        pedir(idDireccion, claveDir, "no-es-correo", null).andExpect(status().isBadRequest());
        pedir(idDireccion, claveDir, "", null).andExpect(status().isBadRequest());
        assertThat(datos.stock(polo)).isEqualTo(10);
    }

    @Test
    void unCodigoDeUnaVezPorClienteEsSoloParaCuentas() throws Exception {
        jdbc.update("INSERT INTO cupones (id_tienda, codigo, tipo_descuento, valor, una_vez_por_cliente) VALUES (?, 'SOLO1', 1, 10, TRUE)",
                tienda.idTienda());
        jdbc.update("INSERT INTO cupones (id_tienda, codigo, tipo_descuento, valor) VALUES (?, 'TODOS10', 1, 10)",
                tienda.idTienda());
        mvc.perform(post(base + "/cupones/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"SOLO1\",\"subtotal\":50}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value(containsString("cuenta")));
        mvc.perform(post(base + "/cupones/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"TODOS10\",\"subtotal\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descuento").value(5.0));

        long idDireccion = crearDireccion(tienda).get("datos").get("idDireccion").asLong();
        String claveDir = claves.firmar("direccion", tienda.idTienda(), idDireccion);
        pedir(idDireccion, claveDir, "ana@correo.pe", "SOLO1").andExpect(status().isBadRequest());
        assertThat(datos.stock(polo)).isEqualTo(10);   // el stock se devolvió
        pedir(idDireccion, claveDir, "ana@correo.pe", "TODOS10").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ apoyo

    private ResultActions enviarDireccion(DatosPrueba.Tienda t, String telefono) throws Exception {
        Long idDistrito = jdbc.queryForObject(
                "SELECT id_division FROM divisiones WHERE id_pais = 'PE' AND codigo = '150122'", Long.class);
        Map<String, Object> direccion = new HashMap<>();
        direccion.put("idDistrito", idDistrito);
        direccion.put("direccion", "Av. Larco 456");
        direccion.put("referencia", "Frente al parque");
        Map<String, Object> cuerpo = Map.of("destinatario", "Ana Torres", "telefono", telefono, "direccion", direccion);
        return mvc.perform(post("/api/public/tiendas/" + t.subdominio() + "/invitado/direcciones")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    private JsonNode crearDireccion(DatosPrueba.Tienda t) throws Exception {
        return leer(enviarDireccion(t, "+51987654321").andExpect(status().isCreated()));
    }

    private Map<String, Object> cuerpo(long idDireccion, String claveDir, String correo, String cupon) {
        Map<String, Object> c = new HashMap<>();
        c.put("correo", correo);
        c.put("idDireccionEnvio", idDireccion);
        c.put("claveDireccion", claveDir);
        c.put("lineas", List.of(Map.of("idVariante", polo, "cantidad", 1)));
        if (cupon != null) c.put("codigoCupon", cupon);
        return c;
    }

    private ResultActions pedir(long idDireccion, String claveDir, String correo, String cupon) throws Exception {
        return mvc.perform(post(base + "/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo(idDireccion, claveDir, correo, cupon))));
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
