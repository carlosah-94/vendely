package curso.fullstack.shopify.tiemporeal;

import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los canales del tiempo real (issue #4) y quién puede escuchar cada uno.
 *
 * Es el equivalente de SecurityConfig para los WebSockets: en una conexión abierta no hay rutas
 * HTTP, hay canales, y cada uno necesita su regla. Está aparte, sin Spring, para poder probar
 * las reglas una por una (CanalesTest).
 *
 *   /topic/tiendas/{id}/pedidos      pedidos nuevos, pagos y cambios de estado   el dueño y empleados con pedidos
 *   /topic/tiendas/{id}/inventario   cambios de stock, para el panel             el dueño y empleados con inventario o productos
 *   /topic/tiendas/{id}/stock        el stock que ve la tienda pública           cualquiera
 *   /user/queue/pedidos              el estado de MIS pedidos                    cada cliente
 */
public final class Canales {

    /** Lo que el servidor usa con convertAndSendToUser: Spring le antepone /user y el usuario. */
    public static final String MIS_PEDIDOS = "/queue/pedidos";
    /** Lo que el cliente pide escuchar para recibir lo de arriba. */
    public static final String MIS_PEDIDOS_SUSCRIPCION = "/user/queue/pedidos";

    private static final Pattern DE_TIENDA = Pattern.compile("^/topic/tiendas/(\\d{1,18})/(pedidos|inventario|stock)$");

    private Canales() {
    }

    public static String pedidos(Long idTienda) {
        return "/topic/tiendas/" + idTienda + "/pedidos";
    }

    public static String inventario(Long idTienda) {
        return "/topic/tiendas/" + idTienda + "/inventario";
    }

    public static String stock(Long idTienda) {
        return "/topic/tiendas/" + idTienda + "/stock";
    }

    /**
     * ¿Puede esta persona escuchar ese canal? usuario es null para un visitante sin sesión.
     * esDueno pregunta si la tienda es suya; se recibe de afuera para no atar esta clase a la base.
     * (Versión de un solo permiso: la usan las pruebas. El servidor usa la de AccesoTienda.)
     *
     * Todo lo que no está en la lista se rechaza: un canal nuevo tiene que agregarse aquí a
     * propósito, no queda abierto por olvido.
     */
    public static boolean puedeEscuchar(UsuarioAutenticado usuario, String destino,
                                        BiPredicate<UsuarioAutenticado, Long> esDueno) {
        return puedeEscuchar(usuario, destino, (u, idTienda, canal) -> esDueno.test(u, idTienda));
    }

    /**
     * Quién puede escuchar el canal de una tienda: se pregunta afuera, porque depende de la base
     * (si es el dueño, o un empleado con el permiso que corresponde a ese canal).
     * canal es "pedidos" o "inventario".
     */
    @FunctionalInterface
    public interface AccesoTienda {
        boolean puede(UsuarioAutenticado usuario, Long idTienda, String canal);
    }

    public static boolean puedeEscuchar(UsuarioAutenticado usuario, String destino, AccesoTienda acceso) {
        if (destino == null) {
            return false;
        }
        if (MIS_PEDIDOS_SUSCRIPCION.equals(destino)) {
            // Cada sesión recibe solo lo suyo: Spring entrega /user/queue/... al usuario de esa
            // conexión. Basta con que haya iniciado sesión.
            return usuario != null;
        }
        Matcher m = DE_TIENDA.matcher(destino);
        if (!m.matches()) {
            return false;
        }
        if ("stock".equals(m.group(2))) {
            return true;   // el stock de cada producto ya es público en el catálogo
        }
        Long idTienda = Long.valueOf(m.group(1));
        return usuario != null
                // SUPERADMIN: soporte de Vendely, solo si el dueño le dio permiso (lo decide acceso).
                && (usuario.rol() == RolUsuario.ADMIN_TIENDA || usuario.rol() == RolUsuario.EMPLEADO
                        || usuario.rol() == RolUsuario.SUPERADMIN)
                && acceso.puede(usuario, idTienda, m.group(2));
    }
}
