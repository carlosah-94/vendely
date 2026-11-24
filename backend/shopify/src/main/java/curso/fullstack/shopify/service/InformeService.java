package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.InformeResponse;
import curso.fullstack.shopify.dto.PedidoResumenResponse;
import curso.fullstack.shopify.entity.Pago;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informes estadísticos del panel: ventas, pedidos, ticket promedio, ventas por día, productos más
 * vendidos, métodos de pago, clientes nuevos y recurrentes, y pedidos por estado de envío.
 *
 * Las sumas se hacen en la base (una consulta por bloque), no trayendo los pedidos a Java.
 * Una venta = un pedido de la tienda con un pago APROBADO, creado dentro del periodo. Los días se
 * cuentan en la zona horaria del navegador del dueño (la que manda el panel), para que "hoy" sea su hoy.
 */
@Service
public class InformeService {

    /** Un año como máximo: el informe por día de un rango mayor sería ilegible. */
    static final int MAX_DIAS = 366;

    private final VerificadorTienda verificadorTienda;
    private final NamedParameterJdbcTemplate jdbc;

    public InformeService(VerificadorTienda verificadorTienda, NamedParameterJdbcTemplate jdbc) {
        this.verificadorTienda = verificadorTienda;
        this.jdbc = jdbc;
    }

    /** Pedidos pagados de la tienda en [:desde, :hasta). Las demás consultas parten de aquí. */
    private static final String VENTAS = """
            WITH ventas AS (
                SELECT p.id_pedido, p.id_login, p.total, p.costo_envio, p.estado, p.fecha_creacion
                  FROM pedidos p
                 WHERE p.id_tienda = :idTienda
                   AND p.fecha_creacion >= :desde AND p.fecha_creacion < :hasta
                   AND EXISTS (SELECT 1 FROM pagos pg WHERE pg.id_pedido = p.id_pedido AND pg.estado = 1)
            )
            """;

    @Transactional(readOnly = true)
    public InformeResponse informe(UsuarioAutenticado yo, Long idTienda, LocalDate desde, LocalDate hasta, String zona) {
        // Solo el dueño: son las cifras de su negocio.
        verificadorTienda.delDueno(yo, idTienda);

        ZoneId zonaId = zona(zona);
        LocalDate fin = hasta != null ? hasta : LocalDate.now(zonaId);
        LocalDate inicio = desde != null ? desde : fin.minusDays(29);
        if (inicio.isAfter(fin)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha de inicio debe ser anterior a la de fin");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
        if (dias > MAX_DIAS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El periodo puede ser de hasta " + MAX_DIAS + " días");
        }

        MapSqlParameterSource actual = parametros(idTienda, inicio, fin, zonaId);
        MapSqlParameterSource anterior = parametros(idTienda, inicio.minusDays(dias), inicio.minusDays(1), zonaId);

        return new InformeResponse(inicio, fin, resumen(actual), resumen(anterior), porDia(actual, inicio, fin),
                productos(actual), metodos(actual), clientes(actual), estados(actual));
    }

    // ------------------------------------------------------------------ consultas

    private InformeResponse.Resumen resumen(MapSqlParameterSource p) {
        Map<String, Object> f = jdbc.queryForMap(VENTAS + """
                SELECT count(*) AS pedidos,
                       COALESCE(sum(total), 0) AS ventas,
                       COALESCE(sum(costo_envio), 0) AS envios,
                       count(DISTINCT id_login) AS clientes,
                       (SELECT COALESCE(sum(d.cantidad), 0) FROM detalle_pedido d
                         WHERE d.id_pedido IN (SELECT id_pedido FROM ventas)) AS unidades,
                       (SELECT COALESCE(sum(d.descuento_unitario * d.cantidad), 0) FROM detalle_pedido d
                         WHERE d.id_pedido IN (SELECT id_pedido FROM ventas)) AS descuentos
                  FROM ventas
                """, p);
        long pedidos = numero(f.get("pedidos"));
        BigDecimal ventas = monto(f.get("ventas"));
        BigDecimal ticket = pedidos == 0 ? BigDecimal.ZERO
                : ventas.divide(BigDecimal.valueOf(pedidos), 2, RoundingMode.HALF_UP);
        return new InformeResponse.Resumen(ventas, pedidos, ticket, numero(f.get("unidades")),
                monto(f.get("descuentos")), monto(f.get("envios")), numero(f.get("clientes")));
    }

    /** Ventas de cada día del periodo; los días sin ventas también salen, con 0 (para dibujar la curva). */
    private List<InformeResponse.Dia> porDia(MapSqlParameterSource p, LocalDate inicio, LocalDate fin) {
        Map<LocalDate, InformeResponse.Dia> conVentas = new HashMap<>();
        jdbc.query(VENTAS + """
                SELECT (fecha_creacion AT TIME ZONE :zona)::date AS dia, count(*) AS pedidos,
                       COALESCE(sum(total), 0) AS ventas
                  FROM ventas
                 GROUP BY 1
                """, p, rs -> {
            LocalDate dia = rs.getObject("dia", LocalDate.class);
            conVentas.put(dia, new InformeResponse.Dia(dia, rs.getBigDecimal("ventas"), rs.getLong("pedidos")));
        });
        List<InformeResponse.Dia> lista = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
            lista.add(conVentas.getOrDefault(d, new InformeResponse.Dia(d, BigDecimal.ZERO, 0)));
        }
        return lista;
    }

    /** Los 10 productos que más dinero dejaron (lo cobrado: precio de la venta menos la oferta). */
    private List<InformeResponse.ProductoVendido> productos(MapSqlParameterSource p) {
        return jdbc.query(VENTAS + """
                SELECT v.id_producto AS id_producto, max(d.nombre_producto) AS nombre,
                       sum(d.cantidad) AS unidades,
                       sum((pr.monto - d.descuento_unitario) * d.cantidad) AS ingresos
                  FROM detalle_pedido d
                  JOIN ventas ve ON ve.id_pedido = d.id_pedido
                  JOIN variantes v ON v.id_variante = d.id_variante
                  JOIN precios pr ON pr.id_precio = d.id_precio
                 GROUP BY v.id_producto
                 ORDER BY ingresos DESC, unidades DESC
                 LIMIT 10
                """, p, (rs, i) -> new InformeResponse.ProductoVendido(rs.getLong("id_producto"),
                rs.getString("nombre"), rs.getLong("unidades"), rs.getBigDecimal("ingresos")));
    }

    private List<InformeResponse.MetodoPago> metodos(MapSqlParameterSource p) {
        return jdbc.query(VENTAS + """
                SELECT pg.proveedor, count(*) AS pedidos, COALESCE(sum(pg.monto), 0) AS monto
                  FROM pagos pg
                  JOIN ventas ve ON ve.id_pedido = pg.id_pedido
                 WHERE pg.estado = 1
                 GROUP BY pg.proveedor
                 ORDER BY monto DESC
                """, p, (rs, i) -> new InformeResponse.MetodoPago(nombreMetodo(rs.getShort("proveedor")),
                rs.getLong("pedidos"), rs.getBigDecimal("monto")));
    }

    /** Un cliente es "nuevo" si su primera compra pagada en esta tienda fue dentro del periodo. */
    private InformeResponse.Clientes clientes(MapSqlParameterSource p) {
        Map<String, Object> f = jdbc.queryForMap(VENTAS + """
                SELECT count(*) FILTER (WHERE primera >= :desde) AS nuevos,
                       count(*) FILTER (WHERE primera < :desde) AS recurrentes
                  FROM (SELECT c.id_login,
                               (SELECT min(p2.fecha_creacion) FROM pedidos p2
                                 WHERE p2.id_tienda = :idTienda AND p2.id_login = c.id_login
                                   AND EXISTS (SELECT 1 FROM pagos pg
                                                WHERE pg.id_pedido = p2.id_pedido AND pg.estado = 1)) AS primera
                          FROM (SELECT DISTINCT id_login FROM ventas WHERE id_login IS NOT NULL) c) x
                """, p);
        return new InformeResponse.Clientes(numero(f.get("nuevos")), numero(f.get("recurrentes")));
    }

    private List<InformeResponse.EstadoPedidos> estados(MapSqlParameterSource p) {
        return jdbc.query(VENTAS + """
                SELECT estado, count(*) AS pedidos FROM ventas GROUP BY estado ORDER BY estado
                """, p, (rs, i) -> new InformeResponse.EstadoPedidos(
                PedidoResumenResponse.nombreEstado(rs.getShort("estado")), rs.getLong("pedidos")));
    }

    // ------------------------------------------------------------------ apoyo

    private static MapSqlParameterSource parametros(Long idTienda, LocalDate inicio, LocalDate fin, ZoneId zona) {
        return new MapSqlParameterSource()
                .addValue("idTienda", idTienda)
                .addValue("desde", inicio.atStartOfDay(zona).toOffsetDateTime())
                .addValue("hasta", fin.plusDays(1).atStartOfDay(zona).toOffsetDateTime())
                .addValue("zona", zona.getId());
    }

    private static ZoneId zona(String zona) {
        if (zona == null || zona.isBlank()) {
            return ZoneId.of("America/Lima");
        }
        try {
            return ZoneId.of(zona.trim());
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Zona horaria no válida");
        }
    }

    private static String nombreMetodo(short proveedor) {
        return switch (proveedor) {
            case Pago.PAYPAL -> "PAYPAL";
            case Pago.MERCADO_PAGO -> "MERCADO_PAGO";
            default -> "OTRO";
        };
    }

    private static long numero(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static BigDecimal monto(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
