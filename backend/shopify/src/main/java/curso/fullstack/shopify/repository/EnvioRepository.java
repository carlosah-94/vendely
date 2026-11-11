package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Tienda;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Tarifas de envío de una tienda (tablas tarifas_envio y tarifas_internacionales, llaves compuestas).
 * - Nacional: una tarifa por región de primer nivel del país de la tienda (departamento en Perú).
 * - Internacional: una tarifa por país de destino. Sin tarifa activa, la tienda no vende a ese país
 *   (lo exige el trigger T2 de pedidos).
 * Se usan consultas nativas con "INSERT ... ON CONFLICT" (crear o actualizar en un solo paso)
 * porque la llave es compuesta y no hace falta una entidad para cada tabla.
 */
public interface EnvioRepository extends Repository<Tienda, Long> {

    /** Una tarifa, nacional o internacional. idZona = id de la región (nacional) o código del país. */
    interface Tarifa {
        String getIdZona();
        String getZona();
        BigDecimal getCosto();
        BigDecimal getEnvioGratisDesde();
        Short getDiasEntregaMin();
        Short getDiasEntregaMax();
        Short getEstado();          // null = esa región todavía no tiene tarifa
    }

    /** Todas las regiones del país de la tienda, con su tarifa si la tiene. */
    @Query(value = """
            SELECT CAST(d.id_division AS text) AS "idZona", d.nombre AS "zona",
                   te.costo AS "costo", te.envio_gratis_desde AS "envioGratisDesde",
                   te.dias_entrega_min AS "diasEntregaMin", te.dias_entrega_max AS "diasEntregaMax",
                   te.estado AS "estado"
              FROM tiendas t
              JOIN divisiones d ON d.id_pais = t.id_pais AND d.nivel = 1
              LEFT JOIN tarifas_envio te ON te.id_tienda = t.id_tienda AND te.id_division = d.id_division
             WHERE t.id_tienda = :idTienda
             ORDER BY d.nombre
            """, nativeQuery = true)
    List<Tarifa> nacionales(@Param("idTienda") Long idTienda);

    /** Los países a los que la tienda envía (activos o pausados). */
    @Query(value = """
            SELECT CAST(ti.id_pais AS text) AS "idZona", p.nombre AS "zona",
                   ti.costo AS "costo", ti.envio_gratis_desde AS "envioGratisDesde",
                   ti.dias_entrega_min AS "diasEntregaMin", ti.dias_entrega_max AS "diasEntregaMax",
                   ti.estado AS "estado"
              FROM tarifas_internacionales ti
              JOIN paises p ON p.id_pais = ti.id_pais
             WHERE ti.id_tienda = :idTienda
             ORDER BY p.nombre
            """, nativeQuery = true)
    List<Tarifa> internacionales(@Param("idTienda") Long idTienda);

    /** ¿La región es de primer nivel y del país de la tienda? */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM divisiones d JOIN tiendas t ON t.id_pais = d.id_pais
                            WHERE t.id_tienda = :idTienda AND d.id_division = :idDivision AND d.nivel = 1)
            """, nativeQuery = true)
    boolean esRegionDeLaTienda(@Param("idTienda") Long idTienda, @Param("idDivision") Long idDivision);

    @Modifying
    @Query(value = """
            INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde,
                                       dias_entrega_min, dias_entrega_max, estado)
            VALUES (:idTienda, :idDivision, :costo, :gratis, :diasMin, :diasMax, :estado)
            ON CONFLICT (id_tienda, id_division) DO UPDATE
               SET costo = EXCLUDED.costo, envio_gratis_desde = EXCLUDED.envio_gratis_desde,
                   dias_entrega_min = EXCLUDED.dias_entrega_min, dias_entrega_max = EXCLUDED.dias_entrega_max,
                   estado = EXCLUDED.estado
            """, nativeQuery = true)
    int guardarNacional(@Param("idTienda") Long idTienda, @Param("idDivision") Long idDivision,
                        @Param("costo") BigDecimal costo, @Param("gratis") BigDecimal envioGratisDesde,
                        @Param("diasMin") Short diasMin, @Param("diasMax") Short diasMax,
                        @Param("estado") Short estado);

    @Modifying
    @Query(value = """
            INSERT INTO tarifas_internacionales (id_tienda, id_pais, costo, envio_gratis_desde,
                                                 dias_entrega_min, dias_entrega_max, estado)
            VALUES (:idTienda, :idPais, :costo, :gratis, :diasMin, :diasMax, :estado)
            ON CONFLICT (id_tienda, id_pais) DO UPDATE
               SET costo = EXCLUDED.costo, envio_gratis_desde = EXCLUDED.envio_gratis_desde,
                   dias_entrega_min = EXCLUDED.dias_entrega_min, dias_entrega_max = EXCLUDED.dias_entrega_max,
                   estado = EXCLUDED.estado
            """, nativeQuery = true)
    int guardarInternacional(@Param("idTienda") Long idTienda, @Param("idPais") String idPais,
                             @Param("costo") BigDecimal costo, @Param("gratis") BigDecimal envioGratisDesde,
                             @Param("diasMin") Short diasMin, @Param("diasMax") Short diasMax,
                             @Param("estado") Short estado);

    @Modifying
    @Query(value = "DELETE FROM tarifas_internacionales WHERE id_tienda = :idTienda AND id_pais = :idPais",
           nativeQuery = true)
    int borrarInternacional(@Param("idTienda") Long idTienda, @Param("idPais") String idPais);

    /** Envío para una dirección del cliente: la misma regla que usa el trigger T2 al crear el pedido. */
    interface Cotizacion {
        String getIdPais();
        String getPais();
        Boolean getInternacional();
        BigDecimal getCosto();              // null = la tienda no envía ahí
        BigDecimal getEnvioGratisDesde();
        Short getDiasEntregaMin();
        Short getDiasEntregaMax();
    }

    @Query(value = """
            SELECT CAST(d.id_pais AS text) AS "idPais", p.nombre AS "pais",
                   (d.id_pais <> t.id_pais) AS "internacional",
                   COALESCE(te.costo, ti.costo) AS "costo",
                   COALESCE(te.envio_gratis_desde, ti.envio_gratis_desde) AS "envioGratisDesde",
                   COALESCE(te.dias_entrega_min, ti.dias_entrega_min) AS "diasEntregaMin",
                   COALESCE(te.dias_entrega_max, ti.dias_entrega_max) AS "diasEntregaMax"
              FROM direcciones d
              JOIN tiendas t ON t.id_tienda = :idTienda
              JOIN paises p ON p.id_pais = d.id_pais
              LEFT JOIN tarifas_envio te
                     ON d.id_pais = t.id_pais AND d.id_division IS NOT NULL
                    AND te.id_tienda = t.id_tienda AND te.estado = 1
                    AND te.id_division = fn_division_nivel1(d.id_division)
              LEFT JOIN tarifas_internacionales ti
                     ON d.id_pais <> t.id_pais
                    AND ti.id_tienda = t.id_tienda AND ti.id_pais = d.id_pais AND ti.estado = 1
             WHERE d.id_direccion = :idDireccion AND d.id_login = :idLogin AND d.estado = 1
            """, nativeQuery = true)
    Optional<Cotizacion> cotizar(@Param("idTienda") Long idTienda, @Param("idLogin") Long idLogin,
                                 @Param("idDireccion") Long idDireccion);

    /** Países a los que una tienda vende: el suyo y los que tienen tarifa internacional activa. */
    interface PaisEnvio {
        String getIdPais();
        String getNombre();
        Boolean getInternacional();
    }

    @Query(value = """
            SELECT CAST(p.id_pais AS text) AS "idPais", p.nombre AS "nombre", FALSE AS "internacional"
              FROM tiendas t JOIN paises p ON p.id_pais = t.id_pais
             WHERE t.id_tienda = :idTienda
            UNION ALL
            SELECT CAST(p.id_pais AS text), p.nombre, TRUE
              FROM tarifas_internacionales ti JOIN paises p ON p.id_pais = ti.id_pais
             WHERE ti.id_tienda = :idTienda AND ti.estado = 1
             ORDER BY 3, 2
            """, nativeQuery = true)
    List<PaisEnvio> paisesDeEnvio(@Param("idTienda") Long idTienda);
}
