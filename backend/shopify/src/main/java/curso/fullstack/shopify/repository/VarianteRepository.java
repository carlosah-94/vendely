package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Variante;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VarianteRepository extends JpaRepository<Variante, Long> {

    interface Info {
        Long getIdTienda();
        String getNombreProducto();
        String getNombreVariante();
        Short getEstadoProducto();
    }

    /** Tienda, nombre del producto y nombre de la variante ("42 / Negro"; "Única" si no tiene opciones). */
    @Query(value = """
            SELECT p.id_tienda AS "idTienda", p.nombre AS "nombreProducto", p.estado AS "estadoProducto",
                   COALESCE(string_agg(vv.valor, ' / ' ORDER BY vv.posicion), 'Única') AS "nombreVariante"
              FROM variantes v
              JOIN productos p ON p.id_producto = v.id_producto
              LEFT JOIN variante_valores vv ON vv.id_variante = v.id_variante
             WHERE v.id_variante = :idVariante
             GROUP BY p.id_tienda, p.nombre, p.estado
            """, nativeQuery = true)
    Optional<Info> buscarInfo(@Param("idVariante") Long idVariante);

    // Issue #10: variantes de un producto. El CRUD del panel gestiona productos SIMPLES (una sola).
    List<Variante> findByIdProducto(Long idProducto);
}