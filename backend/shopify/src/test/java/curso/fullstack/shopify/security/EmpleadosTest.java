package curso.fullstack.shopify.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Empleados de la tienda: cuentas de personal con permisos (como en Shopify).
 *
 * Lo que se comprueba es la barrera: un empleado entra SOLO a las tiendas donde trabaja, y
 * dentro de ellas SOLO a lo que el dueño le permitió. Lo que es del dueño (plan, envíos,
 * empleados, eliminar ventas) le responde 403 aunque trabaje en la tienda. Y el plan pone el
 * límite: Gratis 0 empleados, Básico 2, Pro 15.
 */
class EmpleadosTest extends PruebaIntegracion {

    private static final String SOLO_DUENO = "Solo el dueño de la tienda puede hacer esto";

    private DatosPrueba.Tienda tienda;

    @BeforeEach
    void tiendaConPlanBasico() {
        tienda = datos.nuevaTienda();
        datos.cambiarPlan(tienda.idTienda(), 2);   // Básico: hasta 2 empleados
    }

    // ------------------------------------------------------------- permisos dentro de la tienda

    @Test
    void conPermisoDePedidosVePedidosYLeeLoQueNecesitaParaVender() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);

        mvc.perform(get(ruta("pedidos")).header("Authorization", token(empleado))).andExpect(status().isOk());
        // Para registrar una venta hay que elegir cliente y productos: leerlos sí puede.
        mvc.perform(get(ruta("clientes")).header("Authorization", token(empleado))).andExpect(status().isOk());
        mvc.perform(get(ruta("productos")).header("Authorization", token(empleado))).andExpect(status().isOk());
    }

    @Test
    void sinPermisoDeProductosNoCambiaElCatalogo() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);

        mvc.perform(post(ruta("categorias")).header("Authorization", token(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Polos\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Tu cuenta de empleado no tiene permiso de productos en esta tienda"));
    }

    @Test
    void conPermisoDeProductosSiCreaCategorias() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), false, true, false, false);

        mvc.perform(post(ruta("categorias")).header("Authorization", token(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Polos\"}"))
                .andExpect(status().isCreated());
        // Pero no ve los pedidos: no se los dieron.
        mvc.perform(get(ruta("pedidos")).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinPermisoDeInventarioNoCambiaElStock() throws Exception {
        long variante = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);

        mvc.perform(patch(ruta("inventario/" + variante)).header("Authorization", token(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":99}"))
                .andExpect(status().isForbidden());
        assertEquals(10, datos.stock(variante));

        long bodeguero = datos.nuevoEmpleado(tienda.idTienda(), false, false, true, false);
        mvc.perform(patch(ruta("inventario/" + variante)).header("Authorization", token(bodeguero))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":99}"))
                .andExpect(status().isOk());
        assertEquals(99, datos.stock(variante));
    }

    @ParameterizedTest(name = "lo del dueño ({0}) le da 403 a un empleado con todos los permisos")
    @ValueSource(strings = {"envios", "plan", "empleados"})
    void loDelDuenoNoLoTocaUnEmpleado(String recurso) throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, true, true, true);

        mvc.perform(get(ruta(recurso)).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(SOLO_DUENO));
    }

    @Test
    void unEmpleadoNoEliminaVentas() throws Exception {
        long variante = datos.nuevoProducto(tienda.idTienda(), "50.00", 10);
        long idPedido = crearPedido(tienda, variante, 1);
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, true, true, true);

        mvc.perform(delete(ruta("pedidos/" + idPedido)).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(SOLO_DUENO));
    }

    // ------------------------------------------------------------- aislamiento y estado

    @Test
    void noEntraAUnaTiendaDondeNoTrabaja() throws Exception {
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        datos.cambiarPlan(otra.idTienda(), 2);
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, true, true, true);

        mvc.perform(get("/api/panel/tiendas/" + otra.idTienda() + "/pedidos").header("Authorization", token(empleado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Esta tienda no es tuya"));
    }

    @Test
    void suspendidoYaNoEntra() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        datos.suspenderEmpleado(empleado, tienda.idTienda());

        mvc.perform(get(ruta("pedidos")).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("El dueño suspendió tu acceso a esta tienda"));
    }

    @Test
    void siLaTiendaBajaAGratisSusEmpleadosYaNoEntran() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        datos.cambiarPlan(tienda.idTienda(), 1);

        mvc.perform(get(ruta("pedidos")).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("El plan de esta tienda ya no incluye empleados"));
    }

    @Test
    void siLaTiendaBajaDeProABasicoEntranSoloLosDosMasAntiguos() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 3);   // Pro: hasta 15
        long primero = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        long segundo = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        long tercero = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);
        datos.cambiarPlan(tienda.idTienda(), 2);   // Básico: hasta 2

        mvc.perform(get(ruta("pedidos")).header("Authorization", token(primero))).andExpect(status().isOk());
        mvc.perform(get(ruta("pedidos")).header("Authorization", token(segundo))).andExpect(status().isOk());
        mvc.perform(get(ruta("pedidos")).header("Authorization", token(tercero)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString("quedó fuera del límite")));
        mvc.perform(get("/api/panel/tiendas").header("Authorization", token(tercero)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // El dueño ve a los tres (nadie se borra) y sabe quién quedó fuera.
        mvc.perform(get(ruta("empleados")).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxEmpleados").value(2))
                .andExpect(jsonPath("$.activos").value(3))
                .andExpect(jsonPath("$.empleados", hasSize(3)))
                .andExpect(jsonPath("$.empleados[0].dentroDelPlan").value(true))
                .andExpect(jsonPath("$.empleados[1].dentroDelPlan").value(true))
                .andExpect(jsonPath("$.empleados[2].dentroDelPlan").value(false));

        // Si el dueño suspende a uno, el tercero vuelve a entrar sin hacer nada más.
        datos.suspenderEmpleado(segundo, tienda.idTienda());
        mvc.perform(get(ruta("pedidos")).header("Authorization", token(tercero))).andExpect(status().isOk());
    }

    @Test
    void misTiendasMuestraSoloDondeTrabajaYConSusPermisos() throws Exception {
        DatosPrueba.Tienda ajena = datos.nuevaTienda();
        datos.cambiarPlan(ajena.idTienda(), 2);
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, true, false);

        mvc.perform(get("/api/panel/tiendas").header("Authorization", token(empleado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idTienda").value(tienda.idTienda()))
                .andExpect(jsonPath("$[0].esDueno").value(false))
                .andExpect(jsonPath("$[0].permisos", hasItem("PEDIDOS")))
                .andExpect(jsonPath("$[0].permisos", hasItem("INVENTARIO")))
                .andExpect(jsonPath("$[0].permisos", not(hasItem("PRODUCTOS"))));
    }

    @Test
    void elDuenoSigueTeniendoTodo() throws Exception {
        mvc.perform(get("/api/panel/tiendas/" + tienda.idTienda()).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDueno").value(true))
                .andExpect(jsonPath("$.permisos", hasSize(4)));
    }

    @Test
    void unEmpleadoNoCreaTiendas() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, true, true, true);

        mvc.perform(post("/api/panel/tiendas").header("Authorization", token(empleado))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mi tienda\",\"subdominio\":\"tienda-de-empleado\",\"correoContacto\":\"e@prueba.pe\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- el dueño gestiona empleados

    @Test
    void elDuenoAgregaUnEmpleadoYEsePuedeIniciarSesion() throws Exception {
        String correo = "nuevo" + System.nanoTime() + "@prueba.pe";

        mvc.perform(post(ruta("empleados")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(correo, "Clave1234", true, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.puedePedidos").value(true))
                .andExpect(jsonPath("$.activo").value(true));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("EMPLEADO"));
    }

    @Test
    void elPlanBasicoPermiteDosEmpleados() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(ruta("empleados")).header("Authorization", token(tienda.idDueno()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("e" + i + "-" + System.nanoTime() + "@prueba.pe", "Clave1234", true, false)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post(ruta("empleados")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("tercero" + System.nanoTime() + "@prueba.pe", "Clave1234", true, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", containsString("permite hasta 2 empleados")))
                .andExpect(jsonPath("$.mensaje", containsString("Mejora tu plan")));
    }

    @Test
    void elPlanGratisNoIncluyeEmpleados() throws Exception {
        datos.cambiarPlan(tienda.idTienda(), 1);

        mvc.perform(post(ruta("empleados")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("gratis" + System.nanoTime() + "@prueba.pe", "Clave1234", true, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", containsString("no incluye empleados")));
    }

    @Test
    void noSeAgregaUnEmpleadoSinPermisos() throws Exception {
        mvc.perform(post(ruta("empleados")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("sinpermisos" + System.nanoTime() + "@prueba.pe", "Clave1234", false, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Marca al menos un permiso para el empleado"));
    }

    @Test
    void elCorreoDeUnDuenoNoPuedeSerEmpleado() throws Exception {
        DatosPrueba.Tienda otra = datos.nuevaTienda();

        mvc.perform(post(ruta("empleados")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(otra.correoDueno(), "Clave1234", true, false)))
                .andExpect(status().isConflict());
    }

    @Test
    void unEmpleadoDeOtraTiendaSeAgregaSinContrasenaNueva() throws Exception {
        DatosPrueba.Tienda otra = datos.nuevaTienda();
        datos.cambiarPlan(otra.idTienda(), 2);
        long empleado = datos.nuevoEmpleado(otra.idTienda(), true, false, false, false);
        String correo = jdbc.queryForObject("SELECT correo FROM login WHERE id_login = ?", String.class, empleado);

        mvc.perform(post(ruta("empleados")).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(correo, null, false, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLogin").value(empleado));

        // Ahora trabaja en las dos tiendas.
        mvc.perform(get("/api/panel/tiendas").header("Authorization", token(empleado)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void elDuenoCambiaPermisosYLoQuita() throws Exception {
        long empleado = datos.nuevoEmpleado(tienda.idTienda(), true, false, false, false);

        mvc.perform(put(ruta("empleados/" + empleado)).header("Authorization", token(tienda.idDueno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"puedePedidos\":false,\"puedeProductos\":true,\"puedeInventario\":false,"
                                + "\"puedeClientes\":false,\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedePedidos").value(false))
                .andExpect(jsonPath("$.puedeProductos").value(true));
        mvc.perform(get(ruta("pedidos")).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden());

        mvc.perform(delete(ruta("empleados/" + empleado)).header("Authorization", token(tienda.idDueno())))
                .andExpect(status().isNoContent());
        mvc.perform(get(ruta("productos")).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Esta tienda no es tuya"));
    }

    @Test
    void otroDuenoNoVeLosEmpleadosDeEstaTienda() throws Exception {
        DatosPrueba.Tienda otra = datos.nuevaTienda();

        mvc.perform(get(ruta("empleados")).header("Authorization", token(otra.idDueno())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Esta tienda no es tuya"));
    }

    // ------------------------------------------------------------------ apoyo

    private String ruta(String recurso) {
        return "/api/panel/tiendas/" + tienda.idTienda() + "/" + recurso;
    }

    private String json(String correo, String contrasena, boolean pedidos, boolean productos) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("nombres", "Ana");
        m.put("primerApellido", "Torres");
        m.put("correo", correo);
        m.put("contrasena", contrasena);
        m.put("puedePedidos", pedidos);
        m.put("puedeProductos", productos);
        m.put("puedeInventario", false);
        m.put("puedeClientes", false);
        return objectMapper.writeValueAsString(m);
    }
}
