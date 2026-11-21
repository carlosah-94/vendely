package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Tienda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {

    Optional<Tienda> findBySubdominio(String subdominio);

    boolean existsBySubdominio(String subdominio);

    // Las tiendas de un dueño, de la más antigua a la más nueva
    List<Tienda> findByIdPropietarioOrderByIdTiendaAsc(Long idPropietario);

    /**
     * Tarifa de envío por defecto a cada región del país de la tienda, en su moneda:
     * 10.00 en monedas con céntimos (S/ 10) y 3990 en monedas sin decimales (CLP 3.990).
     * El dueño la cambia luego en Envíos.
     */
    @Modifying
    @Query(value = """
            INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
            SELECT t.id_tienda, d.id_division, CASE WHEN m.decimales = 0 THEN 3990 ELSE 10.00 END, NULL, 1, 5
              FROM tiendas t
              JOIN paises p ON p.id_pais = t.id_pais
              JOIN monedas m ON m.id_moneda = p.id_moneda
              JOIN divisiones d ON d.id_pais = t.id_pais AND d.nivel = 1
             WHERE t.id_tienda = :idTienda
            """, nativeQuery = true)
    int crearTarifasPorDefecto(@Param("idTienda") Long idTienda);

    /**
     * Pone a cada tienda el plan que le corresponde HOY segun su historial de pagos:
     * el de la suscripcion pagada que cubre este momento; si no hay ninguna, Gratis (1).
     * Asi funcionan solos el vencimiento (vuelve a Gratis) y el cambio programado a un plan menor
     * (empieza cuando termina el que ya pago). Si el plan nuevo no incluye plantillas, vuelve a la clasica.
     * No se borra nada: productos, colores y envios se quedan.
     * clock_timestamp() es la hora exacta de ahora; now() seria la hora en que empezo la transaccion
     * (unos segundos antes, mientras se cobraba en PayPal) y veria todavia el plan anterior.
     */
    String SINCRONIZAR_PLANES = """
            UPDATE tiendas t
               SET id_plan = x.id_plan,
                   plan_vence_el = x.fecha_fin,
                   plantilla = CASE WHEN x.permite_plantillas THEN t.plantilla ELSE 'clasica' END
              FROM (SELECT t2.id_tienda, COALESCE(v.id_plan, 1) AS id_plan, v.fecha_fin, p.permite_plantillas
                      FROM tiendas t2
                      LEFT JOIN LATERAL (
                           SELECT s.id_plan, s.fecha_fin
                             FROM suscripciones s
                            WHERE s.id_tienda = t2.id_tienda AND s.estado = 1
                              AND s.fecha_inicio <= clock_timestamp() AND s.fecha_fin > clock_timestamp()
                            ORDER BY s.fecha_inicio DESC
                            LIMIT 1) v ON TRUE
                      JOIN planes p ON p.id_plan = COALESCE(v.id_plan, 1)) x
             WHERE t.id_tienda = x.id_tienda
               AND (t.id_plan IS DISTINCT FROM x.id_plan OR t.plan_vence_el IS DISTINCT FROM x.fecha_fin)
            """;

    /** Todas las tiendas (tarea diaria y al arrancar). */
    @Modifying
    @Query(value = SINCRONIZAR_PLANES, nativeQuery = true)
    int sincronizarPlanes();

    /** Solo una tienda (despues de pagar su plan). */
    @Modifying
    @Query(value = SINCRONIZAR_PLANES + " AND t.id_tienda = :idTienda", nativeQuery = true)
    int sincronizarPlan(@Param("idTienda") Long idTienda);
}
