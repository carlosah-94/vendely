package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Tienda;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Consultas de solo lectura para el panel del superadmin (toda la plataforma, sin filtrar por tienda).
 * Extiende Repository (no JpaRepository) porque solo tiene consultas propias: no guarda ni borra nada.
 * Los cambios de estado se hacen con TiendaRepository y LoginRepository.
 * Las fechas salen como segundos (epoch) para no depender de cómo el driver convierte timestamptz.
 */
public interface AdminRepository extends Repository<Tienda, Long> {

    /** Números generales de Vendely. */
    interface Resumen {
        Long getTiendasActivas();
        Long getTiendasSuspendidas();
        Long getDuenos();
        Long getClientes();
        Long getPedidos();
    }

    // Una venta "pagada" es un pedido con un pago aprobado (pagos.estado = 1).
    @Query(value = """
            SELECT (SELECT count(*) FROM tiendas WHERE estado = 1) AS "tiendasActivas",
                   (SELECT count(*) FROM tiendas WHERE estado = 0) AS "tiendasSuspendidas",
                   (SELECT count(*) FROM login WHERE id_rol = 2)   AS "duenos",
                   (SELECT count(*) FROM login WHERE id_rol = 3)   AS "clientes",
                   (SELECT count(*) FROM pedidos)                  AS "pedidos"
            """, nativeQuery = true)
    Resumen resumen();

    /** Ventas pagadas sumadas por moneda: no se suman soles con pesos chilenos. */
    interface VentaMoneda {
        String getMoneda();
        BigDecimal getMonto();
    }

    @Query(value = """
            SELECT CAST(p.moneda AS text) AS "moneda", sum(p.total) AS "monto"
              FROM pedidos p
             WHERE EXISTS (SELECT 1 FROM pagos pg WHERE pg.id_pedido = p.id_pedido AND pg.estado = 1)
             GROUP BY p.moneda
             ORDER BY sum(p.total) DESC
            """, nativeQuery = true)
    List<VentaMoneda> ventasPorMoneda();

    /** Una tienda con su dueño y sus números. */
    interface FilaTienda {
        Long getIdTienda();
        String getNombre();
        String getSubdominio();
        String getPais();
        Short getEstado();
        Long getFechaCreacion();     // segundos desde 1970 (el DTO la convierte en fecha)
        String getDueno();
        String getCorreoDueno();
        Long getProductos();
        Long getPedidos();
        BigDecimal getVentasPagadas();
        String getMoneda();          // moneda de la tienda: sus ventas van en ella
    }

    @Query(value = """
            SELECT t.id_tienda AS "idTienda", t.nombre AS "nombre", t.subdominio AS "subdominio",
                   CAST(t.id_pais AS text) AS "pais", t.estado AS "estado", CAST(extract(epoch FROM t.fecha_creacion) AS bigint) AS "fechaCreacion",
                   l.nombres || ' ' || l.primer_apellido AS "dueno", l.correo AS "correoDueno",
                   (SELECT count(*) FROM productos pr WHERE pr.id_tienda = t.id_tienda) AS "productos",
                   (SELECT count(*) FROM pedidos pe WHERE pe.id_tienda = t.id_tienda)   AS "pedidos",
                   (SELECT coalesce(sum(pe.total), 0) FROM pedidos pe
                     WHERE pe.id_tienda = t.id_tienda
                       AND EXISTS (SELECT 1 FROM pagos pg WHERE pg.id_pedido = pe.id_pedido AND pg.estado = 1))
                                                                                         AS "ventasPagadas",
                   CAST(pa.id_moneda AS text) AS "moneda"
              FROM tiendas t
              JOIN paises pa        ON pa.id_pais = t.id_pais
              JOIN propietarios pro ON pro.id_propietario = t.id_propietario
              JOIN login l          ON l.id_login = pro.id_login
             ORDER BY t.fecha_creacion DESC
            """, nativeQuery = true)
    List<FilaTienda> tiendas();

    /** Una cuenta de cualquier rol. tienda = la tienda del cliente; para un dueño, cuántas tiene. */
    interface FilaCuenta {
        Long getIdLogin();
        Short getIdRol();
        String getNombre();
        String getCorreo();
        String getTienda();
        Long getTiendasPropias();
        Short getEstado();
        Long getFechaCreacion();     // segundos desde 1970 (el DTO la convierte en fecha)
    }

    @Query(value = """
            SELECT l.id_login AS "idLogin", l.id_rol AS "idRol",
                   l.nombres || ' ' || l.primer_apellido AS "nombre", l.correo AS "correo",
                   t.nombre AS "tienda",
                   (SELECT count(*) FROM tiendas tp JOIN propietarios pro ON pro.id_propietario = tp.id_propietario
                     WHERE pro.id_login = l.id_login) AS "tiendasPropias",
                   l.estado AS "estado", CAST(extract(epoch FROM l.fecha_creacion) AS bigint) AS "fechaCreacion"
              FROM login l
              LEFT JOIN tiendas t ON t.id_tienda = l.id_tienda
             ORDER BY l.fecha_creacion DESC
            """, nativeQuery = true)
    List<FilaCuenta> cuentas();
}
