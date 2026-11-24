package curso.fullstack.shopify.tiemporeal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/**
 * Las reglas de quién puede escuchar cada canal de tiempo real.
 *
 * Son la parte de seguridad de los WebSockets: si una falla, un dueño vería los pedidos de otra
 * tienda. Por eso se prueban una por una, sin levantar Spring: la regla de "esta tienda es suya"
 * se simula con un lambda (la tienda 1 es del dueño 10; la 2, de otro).
 */
class CanalesTest {

    private static final UsuarioAutenticado DUENO = new UsuarioAutenticado(10L, RolUsuario.ADMIN_TIENDA, null, "d@x.pe");
    private static final UsuarioAutenticado CLIENTE = new UsuarioAutenticado(20L, RolUsuario.CLIENTE, 1L, "c@x.pe");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(30L, RolUsuario.SUPERADMIN, null, "a@x.pe");

    /** La tienda 1 es del dueño 10; cualquier otra combinación, no. */
    private static final BiPredicate<UsuarioAutenticado, Long> ES_DUENO =
            (u, idTienda) -> u.idLogin() == 10L && idTienda == 1L;

    @Test
    void elDuenoEscuchaLosPedidosYElInventarioDeSuTienda() {
        assertTrue(Canales.puedeEscuchar(DUENO, "/topic/tiendas/1/pedidos", ES_DUENO));
        assertTrue(Canales.puedeEscuchar(DUENO, "/topic/tiendas/1/inventario", ES_DUENO));
    }

    @Test
    void unDuenoNoEscuchaLosCanalesDeOtraTienda() {
        assertFalse(Canales.puedeEscuchar(DUENO, "/topic/tiendas/2/pedidos", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(DUENO, "/topic/tiendas/2/inventario", ES_DUENO));
    }

    @Test
    void sinSesionNoSeEscuchanLosCanalesPrivados() {
        assertFalse(Canales.puedeEscuchar(null, "/topic/tiendas/1/pedidos", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(null, "/topic/tiendas/1/inventario", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(null, Canales.MIS_PEDIDOS_SUSCRIPCION, ES_DUENO));
    }

    @Test
    void unClienteNoEscuchaLosPedidosDeLaTiendaAunqueSeaLaSuya() {
        // El cliente 20 es de la tienda 1, pero los pedidos de la tienda son del dueño.
        assertFalse(Canales.puedeEscuchar(CLIENTE, "/topic/tiendas/1/pedidos", (u, t) -> true));
    }

    @Test
    void unEmpleadoEscuchaSoloLosCanalesQueSuPermisoLeDa() {
        UsuarioAutenticado empleado = new UsuarioAutenticado(40L, RolUsuario.EMPLEADO, null, "e@x.pe");
        // Trabaja en la tienda 1 solo con el permiso de pedidos (lo decide VerificadorTienda).
        Canales.AccesoTienda soloPedidos = (u, idTienda, canal) -> idTienda == 1L && "pedidos".equals(canal);

        assertTrue(Canales.puedeEscuchar(empleado, "/topic/tiendas/1/pedidos", soloPedidos));
        assertFalse(Canales.puedeEscuchar(empleado, "/topic/tiendas/1/inventario", soloPedidos));
        assertFalse(Canales.puedeEscuchar(empleado, "/topic/tiendas/2/pedidos", soloPedidos));
    }

    @Test
    void elSuperadminNoEntraALosCanalesDeUnaTienda() {
        assertFalse(Canales.puedeEscuchar(ADMIN, "/topic/tiendas/1/pedidos", (u, t) -> true));
    }

    @Test
    void elStockPublicoLoEscuchaCualquiera() {
        assertTrue(Canales.puedeEscuchar(null, "/topic/tiendas/1/stock", ES_DUENO));
        assertTrue(Canales.puedeEscuchar(CLIENTE, "/topic/tiendas/2/stock", ES_DUENO));
    }

    @Test
    void misPedidosLoEscuchaCualquieraConSesion() {
        assertTrue(Canales.puedeEscuchar(CLIENTE, Canales.MIS_PEDIDOS_SUSCRIPCION, ES_DUENO));
    }

    @Test
    void unCanalQueNoEstaEnLaListaSeRechaza() {
        assertFalse(Canales.puedeEscuchar(DUENO, "/topic/tiendas/1/otra-cosa", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(DUENO, "/topic/todo", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(DUENO, "/user/queue/de-otro", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(DUENO, "/topic/tiendas/1/pedidos/extra", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(DUENO, null, ES_DUENO));
    }

    @Test
    void unNumeroDeTiendaAbsurdoNoRompeNada() {
        assertFalse(Canales.puedeEscuchar(DUENO, "/topic/tiendas/99999999999999999999999/pedidos", ES_DUENO));
        assertFalse(Canales.puedeEscuchar(DUENO, "/topic/tiendas/abc/pedidos", ES_DUENO));
    }
}
