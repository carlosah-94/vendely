package curso.fullstack.shopify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.PlanRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Qué pasa cuando una tienda baja de plan (por ejemplo, de Pro a Básico): no se borra nada,
 * pero lo que el plan nuevo no incluye deja de funcionar, y el dueño lo sabe antes de confirmar.
 *
 * Aquí: los envíos internacionales (las tarifas quedan guardadas, pero no se vende al extranjero)
 * y la lista de avisos que ve el dueño en la pantalla de planes.
 */
class BajarDePlanTest extends PruebaIntegracion {

    @Autowired private SuscripcionService suscripcionService;
    @Autowired private TiendaRepository tiendaRepository;
    @Autowired private PlanRepository planRepository;

    private DatosPrueba.Tienda tienda;
    private long direccionArgentina;

    @BeforeEach
    void tiendaProQueVendeAArgentina() {
        tienda = datos.nuevaTienda();
        datos.cambiarPlan(tienda.idTienda(), 3);
        jdbc.update("""
                INSERT INTO tarifas_internacionales (id_tienda, id_pais, costo, dias_entrega_min, dias_entrega_max)
                VALUES (?, 'AR', 45.00, 5, 10)""", tienda.idTienda());
        direccionArgentina = jdbc.queryForObject("""
                INSERT INTO direcciones (id_login, id_pais, region, ciudad, destinatario, telefono, direccion)
                VALUES (?, 'AR', 'Buenos Aires', 'CABA', 'Cliente de prueba', '+5491100000000', 'Av. Corrientes 1234')
                RETURNING id_direccion""", Long.class, tienda.idCliente());
    }

    // ------------------------------------------------------------- envíos internacionales

    @Test
    void conProSeCotizaYSeVendeAlExtranjero() throws Exception {
        cotizar().andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.internacional").value(true));
        paises().andExpect(jsonPath("$[*].idPais", hasItem("AR")));

        long variante = datos.nuevoProducto(tienda.idTienda(), "50.00", 5);
        venta(variante).andExpect(status().isCreated());
    }

    @Test
    void alBajarABasicoNoSeVendeAlExtranjeroPeroLaTarifaQuedaGuardada() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 2);

        cotizar().andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.mensaje").value(containsString("no envía a Argentina")));
        paises().andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].idPais", not(hasItem("AR"))));

        long variante = datos.nuevoProducto(tienda.idTienda(), "50.00", 5);
        venta(variante).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("no envía a Argentina")));

        // La tarifa sigue en la base, y el panel avisa que el plan ya no la incluye.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarifas_internacionales WHERE id_tienda = ?",
                Integer.class, tienda.idTienda())).isEqualTo(1);
        mvc.perform(get("/api/panel/tiendas/" + tienda.idTienda() + "/envios")
                        .header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internacionalIncluido").value(false))
                .andExpect(jsonPath("$.internacionales", hasSize(1)));

        // Si vuelve a Pro, vuelve a vender a Argentina sin configurar nada.
        datos.cambiarPlan(tienda.idTienda(), 3);
        cotizar().andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    void unaVentaNacionalSigueFuncionandoConBasico() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 2);
        crearPedido(tienda, datos.nuevoProducto(tienda.idTienda(), "50.00", 5), 1);
    }

    // ------------------------------------------------------------- avisos antes de bajar

    @Test
    void elAvisoDiceLoQueSePierdeSegunLoQueLaTiendaUsa() {
        jdbc.update("UPDATE tiendas SET plantilla = 'moderna' WHERE id_tienda = ?", tienda.idTienda());
        datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);

        List<String> aBasico = suscripcionService.dejaraDeTener(tiendaActual(), plan(2));
        assertThat(aBasico).anyMatch(m -> m.contains("plantilla Clásica"));
        assertThat(aBasico).anyMatch(m -> m.contains("Solo los 2 empleados más antiguos"));
        assertThat(aBasico).anyMatch(m -> m.contains("Argentina"));
        assertThat(aBasico).noneMatch(m -> m.contains("logo"));   // Básico sí personaliza

        List<String> aGratis = suscripcionService.dejaraDeTener(tiendaActual(), plan(1));
        assertThat(aGratis).anyMatch(m -> m.contains("logo"));
        assertThat(aGratis).anyMatch(m -> m.contains("Tus 3 empleados perderán"));
    }

    @Test
    void sinNadaQuePerderNoHayAvisos() {
        jdbc.update("DELETE FROM tarifas_internacionales WHERE id_tienda = ?", tienda.idTienda());
        datos.cambiarPlan(tienda.idTienda(), 2);

        assertThat(suscripcionService.dejaraDeTener(tiendaActual(), plan(2))).isEmpty();
    }

    // ------------------------------------------------------------------ apoyo

    private Tienda tiendaActual() {
        return tiendaRepository.findById(tienda.idTienda()).orElseThrow();
    }

    private Plan plan(int id) {
        return planRepository.findById((short) id).orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions cotizar() throws Exception {
        return mvc.perform(get("/api/cliente/direcciones/" + direccionArgentina + "/envio")
                .header("Authorization", token(tienda.idCliente())));
    }

    private org.springframework.test.web.servlet.ResultActions paises() throws Exception {
        return mvc.perform(get("/api/public/tiendas/" + tienda.subdominio() + "/envios")).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions venta(long variante) throws Exception {
        return mvc.perform(post("/api/panel/tiendas/" + tienda.idTienda() + "/pedidos")
                .header("Authorization", token(tienda.idDueno()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "idLogin", tienda.idCliente(), "idDireccionEnvio", direccionArgentina,
                        "lineas", List.of(Map.of("idVariante", variante, "cantidad", 1))))));
    }
}
