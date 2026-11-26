package curso.fullstack.shopify.security;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import curso.fullstack.shopify.dto.ImagenSubidaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Acceso de soporte (V16): el dueño deja entrar a soporte de Vendely a armar su tienda.
 *
 * Lo que se comprueba: sin permiso, soporte no entra; con permiso entra a esa tienda (y solo a
 * esa), con cualquier plan, y queda anotado lo que cambia; lo que es decisión del dueño (plan,
 * empleados, eliminar ventas, el propio permiso) le sigue dando 403; y al quitarlo o vencer,
 * ya no entra.
 */
class AccesoSoporteTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tienda;
    private long soporte;

    @BeforeEach
    void tiendaGratisYUnaCuentaDeSoporte() {
        tienda = datos.nuevaTienda();          // plan Gratis: sin cupo de empleados
        soporte = datos.nuevoSuperadmin();
    }

    @Test
    void sinPermisoDelDuenoSoporteNoEntra() throws Exception {
        mvc.perform(get(ruta("productos")).header("Authorization", token(soporte)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(VerificadorTienda.SIN_PERMISO_SOPORTE));
        // La base es compartida: otras pruebas dejan tiendas con permiso. Esta no debe estar en la lista.
        mvc.perform(get("/api/panel/tiendas").header("Authorization", token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(enLista(""), hasSize(0)));
    }

    @Test
    void conPermisoEntraArmaLaTiendaYQuedaAnotado() throws Exception {
        permitir(3);

        // La ve en su lista, marcada como soporte (no como dueño).
        mvc.perform(get("/api/panel/tiendas").header("Authorization", token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(enLista(""), hasSize(1)))
                .andExpect(jsonPath(enLista(".soporte"), contains(true)))
                .andExpect(jsonPath(enLista(".esDueno"), contains(false)));

        // Crea una categoría (aunque la tienda sea Gratis) y ve los envíos.
        mvc.perform(post(ruta("categorias")).header("Authorization", token(soporte))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Polos\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get(ruta("envios")).header("Authorization", token(soporte)))
                .andExpect(status().isOk());

        // El dueño ve el permiso y, en el registro, el cambio de soporte (las lecturas no se anotan).
        mvc.perform(get(ruta("soporte")).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activoHasta", notNullValue()))
                .andExpect(jsonPath("$.registro", hasSize(2)))
                .andExpect(jsonPath("$.registro[0].accion").value("CAMBIO"))
                .andExpect(jsonPath("$.registro[0].metodo").value("POST"))
                .andExpect(jsonPath("$.registro[0].ruta").value(ruta("categorias")))
                .andExpect(jsonPath("$.registro[1].accion").value("PERMITIDO"));
    }

    @Test
    void elPermisoEsSoloParaEsaTienda() throws Exception {
        permitir(1);
        DatosPrueba.Tienda otra = datos.nuevaTienda();

        mvc.perform(get("/api/panel/tiendas/" + otra.idTienda() + "/productos").header("Authorization", token(soporte)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "con permiso, soporte no toca {0}")
    @ValueSource(strings = {"empleados", "soporte"})
    void loQueDecideElDuenoNoLoTocaSoporte(String recurso) throws Exception {
        permitir(1);

        mvc.perform(get(ruta(recurso)).header("Authorization", token(soporte)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(VerificadorTienda.SOPORTE_NO_PUEDE));
    }

    @Test
    void soporteNoEliminaVentasNiSeDaPermisoASiMismo() throws Exception {
        long variante = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long idPedido = crearPedido(tienda, variante, 1);
        permitir(1);

        mvc.perform(delete(ruta("pedidos/" + idPedido)).header("Authorization", token(soporte)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(VerificadorTienda.SOPORTE_NO_PUEDE));
        mvc.perform(post(ruta("soporte")).header("Authorization", token(soporte))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unEmpleadoNoDaPermisoDeSoporte() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 2);
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, true, true, true);

        mvc.perform(post(ruta("soporte")).header("Authorization", token(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void alQuitarloYaNoEntra() throws Exception {
        permitir(7);

        mvc.perform(delete(ruta("soporte")).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activoHasta", nullValue()))
                .andExpect(jsonPath("$.registro[0].accion").value("QUITADO"));
        mvc.perform(get(ruta("productos")).header("Authorization", token(soporte)))
                .andExpect(status().isForbidden());
    }

    @Test
    void alVencerYaNoEntra() throws Exception {
        permitir(1);
        jdbc.update("UPDATE tiendas SET soporte_hasta = now() - interval '1 minute' WHERE id_tienda = ?",
                tienda.idTienda());

        mvc.perform(get(ruta("productos")).header("Authorization", token(soporte)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(VerificadorTienda.SIN_PERMISO_SOPORTE));
    }

    @ParameterizedTest(name = "{0} días no es válido")
    @ValueSource(ints = {0, 8})
    void elPermisoDuraDe1A7Dias(int dias) throws Exception {
        mvc.perform(post(ruta("soporte")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":" + dias + "}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM tiendas WHERE id_tienda = ? AND soporte_hasta IS NOT NULL",
                Integer.class, tienda.idTienda()));
    }

    // ------------------------------------------------------------------ mensajes (V17)

    @Test
    void elDuenoExplicaLoQueQuiereYSoporteLeResponde() throws Exception {
        mvc.perform(post(ruta("soporte")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dias\":3,\"mensaje\":\"  Súbanme los 20 polos del Excel  \"}"))
                .andExpect(status().isOk());

        // Soporte lee lo que pidió el dueño y le responde.
        mvc.perform(get(ruta("soporte/mensajes")).header("Authorization", token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].texto").value("Súbanme los 20 polos del Excel"))
                .andExpect(jsonPath("$[0].deSoporte").value(false));
        mvc.perform(post(ruta("soporte/mensajes")).header("Authorization", token(soporte))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"¿Nos pasas las fotos?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].deSoporte").value(true));

        // El dueño ve la respuesta. Responder no es un cambio de la tienda: el registro no lo anota.
        mvc.perform(get(ruta("soporte/mensajes")).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].texto").value("¿Nos pasas las fotos?"));
        mvc.perform(get(ruta("soporte")).header("Authorization", token(tienda.idDueno())))
                .andExpect(jsonPath("$.registro", hasSize(1)))
                .andExpect(jsonPath("$.registro[0].accion").value("PERMITIDO"));
    }

    @Test
    void sinAccesoActivoNoSeEscribe() throws Exception {
        // El dueño: primero tiene que dar acceso, o nadie leería su mensaje.
        mvc.perform(post(ruta("soporte/mensajes")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isConflict());
        // Soporte: ni lee ni escribe.
        mvc.perform(get(ruta("soporte/mensajes")).header("Authorization", token(soporte)))
                .andExpect(status().isForbidden());
        mvc.perform(post(ruta("soporte/mensajes")).header("Authorization", token(soporte))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unEmpleadoNoVeLaConversacion() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 2);
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, true, true, true);
        permitir(1);

        mvc.perform(get(ruta("soporte/mensajes")).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden());
    }

    @Test
    void laBandejaDelEquipoCuentaLoQueFaltaResponder() throws Exception {
        permitir(2);
        escribir(tienda.idDueno(), "Hola");
        escribir(tienda.idDueno(), "¿Me ayudan con las fotos?");

        String bandeja = "$[?(@.idTienda == " + tienda.idTienda() + ")]";
        mvc.perform(get("/api/admin/soporte/bandeja").header("Authorization", token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(bandeja + ".sinResponder", contains(2)))
                .andExpect(jsonPath(bandeja + ".ultimoTexto", contains("¿Me ayudan con las fotos?")));

        // Al responder soporte, queda al día.
        escribir(soporte, "Claro, envíalas por aquí");
        mvc.perform(get("/api/admin/soporte/bandeja").header("Authorization", token(soporte)))
                .andExpect(jsonPath(bandeja + ".sinResponder", contains(0)))
                .andExpect(jsonPath(bandeja + ".ultimoDeSoporte", contains(true)));

        // Solo el equipo de Vendely ve la bandeja.
        mvc.perform(get("/api/admin/soporte/bandeja").header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjuntarRevisaLosPermisosAntesDeSubirNada() throws Exception {
        MockMultipartFile excel = new MockMultipartFile("archivo", "productos.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {'P', 'K', 3, 4, 20, 0});
        String adjunto = ruta("soporte/mensajes/adjunto");

        // Sin acceso activo: el dueño recibe lo mismo que al escribir; soporte, 403; un empleado, 403.
        mvc.perform(multipart(adjunto).file(excel).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isConflict());
        mvc.perform(multipart(adjunto).file(excel).header("Authorization", token(soporte)))
                .andExpect(status().isForbidden());

        // Con acceso, pero sin Cloudinary (en las pruebas no hay CLOUDINARY_URL): 503 con un mensaje claro.
        permitir(1);
        mvc.perform(multipart(adjunto).file(excel).param("mensaje", "Aquí va el Excel")
                        .header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get(ruta("soporte/mensajes")).header("Authorization", token(tienda.idDueno())))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elDuenoMandaElExcelYSoporteLoVeEnLaConversacion() throws Exception {
        String url = "https://res.cloudinary.com/demo/raw/upload/v1/vendely/t" + tienda.idTienda() + "/soporte/productos_ab12.xlsx";
        when(cloudinary.estaConfigurado()).thenReturn(true);
        when(cloudinary.subirArchivo(any(), eq("productos.xlsx"), eq("vendely/t" + tienda.idTienda() + "/soporte"), eq(false)))
                .thenReturn(new ImagenSubidaResponse(url, "vendely/t" + tienda.idTienda() + "/soporte/productos_ab12.xlsx"));
        permitir(2);

        // Solo el archivo, sin texto.
        mvc.perform(multipart(ruta("soporte/mensajes/adjunto"))
                        .file(new MockMultipartFile("archivo", "productos.xlsx", "application/octet-stream",
                                new byte[] {'P', 'K', 3, 4, 20, 0}))
                        .header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].texto").value(""))
                .andExpect(jsonPath("$[0].adjunto.nombre").value("productos.xlsx"))
                .andExpect(jsonPath("$[0].adjunto.url").value(url))
                .andExpect(jsonPath("$[0].adjunto.esImagen").value(false))
                // El tipo sale de la extensión, no del que mandó el navegador.
                .andExpect(jsonPath("$[0].adjunto.tipo").value("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mvc.perform(get(ruta("soporte/mensajes")).header("Authorization", token(soporte)))
                .andExpect(jsonPath("$[0].adjunto.nombre").value("productos.xlsx"));
        String bandeja = "$[?(@.idTienda == " + tienda.idTienda() + ")]";
        mvc.perform(get("/api/admin/soporte/bandeja").header("Authorization", token(soporte)))
                .andExpect(jsonPath(bandeja + ".ultimoTexto", contains("📎 productos.xlsx")));
    }

    @Test
    void unMensajeVacioNoSeGuarda() throws Exception {
        permitir(1);

        mvc.perform(post(ruta("soporte/mensajes")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ apoyo

    private void permitir(int dias) throws Exception {
        mvc.perform(post(ruta("soporte")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":" + dias + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activoHasta", notNullValue()));
    }

    private void escribir(long idLogin, String texto) throws Exception {
        mvc.perform(post(ruta("soporte/mensajes")).header("Authorization", token(idLogin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"" + texto + "\"}"))
                .andExpect(status().isOk());
    }

    /** JSONPath a la tienda de esta prueba dentro de la lista de /api/panel/tiendas (y a un campo suyo). */
    private String enLista(String campo) {
        return "$[?(@.idTienda == " + tienda.idTienda() + ")]" + campo;
    }

    private String ruta(String recurso) {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/" + recurso;
    }
}
