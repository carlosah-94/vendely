package curso.fullstack.shopify.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import curso.fullstack.shopify.DatosPrueba;
import curso.fullstack.shopify.PruebaIntegracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Aislamiento entre tiendas: la barrera multi-tienda del panel (issue #9, bloque 1).
 *
 * El token de un dueño no dice qué tienda administra (puede tener varias), así que cada ruta
 * /api/panel/tiendas/{idTienda}/... depende de que su service llame a
 * {@code verificadorTienda.delDueno(yo, idTienda)}. Si un endpoint nuevo se olvida de esa
 * línea, un dueño lee los datos de otro.
 *
 * Las pruebas parametrizadas recorren un endpoint de cada controller del panel. Si alguien
 * quita la llamada a delDueno de cualquiera de ellos, el dueño B recibe 200 en lugar de 403 y
 * la prueba falla con el nombre del recurso: ese es el candado que pide la issue.
 */
class AislamientoTiendasTest extends PruebaIntegracion {

    private DatosPrueba.Tienda tiendaA;
    private DatosPrueba.Tienda tiendaB;

    @BeforeEach
    void crearDosTiendas() {
        tiendaA = datos.nuevaTienda();
        tiendaB = datos.nuevaTienda();
    }

    @ParameterizedTest(name = "el dueño ve sus {0}")
    @ValueSource(strings = {"productos", "pedidos", "clientes", "inventario", "envios",
            "categorias"})
    void elDuenoEntraASuPropiaTienda(String recurso) throws Exception {
        mvc.perform(get(ruta(tiendaA.idTienda(), recurso)).header("Authorization", token(tiendaA.idDueno())))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "un dueño no ve los {0} de otra tienda: 403")
    @ValueSource(strings = {"productos", "pedidos", "clientes", "inventario", "envios",
            "categorias"})
    void unDuenoNoEntraALaTiendaDeOtro(String recurso) throws Exception {
        mvc.perform(get(ruta(tiendaA.idTienda(), recurso)).header("Authorization", token(tiendaB.idDueno())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Esta tienda no es tuya"));
    }

    @ParameterizedTest(name = "{0} de una tienda que no existe: 404")
    @ValueSource(strings = {"productos", "pedidos", "clientes", "inventario", "envios"})
    void tiendaInexistenteDa404(String recurso) throws Exception {
        mvc.perform(get(ruta(987_654_321L, recurso)).header("Authorization", token(tiendaA.idDueno())))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaCuentaDeDuenoSinPropietarioRecibe403() throws Exception {
        long sinPropietario = datos.cuentaDuenoSinPropietario();
        mvc.perform(get(ruta(tiendaA.idTienda(), "productos")).header("Authorization", token(sinPropietario)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Tu cuenta no está registrada como dueño de tienda"));
    }

    @Test
    void unClienteNoEntraAlPanelNiDeSuPropiaTienda() throws Exception {
        mvc.perform(get(ruta(tiendaA.idTienda(), "productos")).header("Authorization", token(tiendaA.idCliente())))
                .andExpect(status().isForbidden());
    }

    @Test
    void elSuperadminNoUsaElPanelDeLasTiendas() throws Exception {
        // 9001 = admin@vendely.pe, de db/migration-local/V3__usuarios_prueba.sql
        mvc.perform(get(ruta(tiendaA.idTienda(), "productos")).header("Authorization", token(9001L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenElPanelResponde401() throws Exception {
        mvc.perform(get(ruta(tiendaA.idTienda(), "productos")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elDuenoNoVeUnPedidoDeOtraTiendaAunqueUseSuPropioIdTienda() throws Exception {
        // El pedido es de la tienda B; el dueño A lo pide "dentro" de su tienda A.
        long variante = datos.nuevoProducto(tiendaB.idTienda(), "50.00", 10);
        long idPedido = crearPedido(tiendaB, variante, 1);

        mvc.perform(get(ruta(tiendaA.idTienda(), "pedidos/" + idPedido)).header("Authorization", token(tiendaA.idDueno())))
                .andExpect(status().isNotFound());
    }

    private static String ruta(long idTienda, String recurso) {
        return "/api/panel/tiendas/" + idTienda + "/" + recurso;
    }
}
