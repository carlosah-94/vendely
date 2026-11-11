package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Categoria;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findByIdCategoriaAndIdTienda(Long idCategoria, Long idTienda);

    boolean existsByIdTiendaAndSlug(Long idTienda, String slug);

    boolean existsByIdTiendaAndSlugAndIdCategoriaNot(Long idTienda, String slug, Long idCategoria);

    /** Una categoría con cuántos productos tiene. */
    interface Fila {
        Long getIdCategoria();
        String getNombre();
        String getSlug();
        String getDescripcion();
        Short getEstado();
        Long getProductos();
    }

    @Query(value = """
            SELECT c.id_categoria AS "idCategoria", c.nombre AS "nombre", c.slug AS "slug",
                   c.descripcion AS "descripcion", c.estado AS "estado",
                   (SELECT count(*) FROM producto_categorias pc WHERE pc.id_categoria = c.id_categoria) AS "productos"
              FROM categorias c
             WHERE c.id_tienda = :idTienda
             ORDER BY c.nombre
            """, nativeQuery = true)
    List<Fila> filasDeTienda(@Param("idTienda") Long idTienda);

    /** Tienda pública: categorías activas que tienen al menos un producto activo. */
    @Query(value = """
            SELECT c.id_categoria AS "idCategoria", c.nombre AS "nombre", c.slug AS "slug",
                   c.descripcion AS "descripcion", c.estado AS "estado",
                   count(*) AS "productos"
              FROM categorias c
              JOIN producto_categorias pc ON pc.id_categoria = c.id_categoria
              JOIN productos p ON p.id_producto = pc.id_producto AND p.estado = 1
             WHERE c.id_tienda = :idTienda AND c.estado = 1
             GROUP BY c.id_categoria
             ORDER BY c.nombre
            """, nativeQuery = true)
    List<Fila> filasPublicas(@Param("idTienda") Long idTienda);

    /** Cuántas de estas categorías son de la tienda (para rechazar ids de otra tienda). */
    @Query(value = "SELECT count(*) FROM categorias WHERE id_tienda = :idTienda AND id_categoria IN (:ids)",
           nativeQuery = true)
    long contarDeTienda(@Param("idTienda") Long idTienda, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query(value = "DELETE FROM producto_categorias WHERE id_producto = :idProducto", nativeQuery = true)
    int quitarDeProducto(@Param("idProducto") Long idProducto);

    @Modifying
    @Query(value = "INSERT INTO producto_categorias (id_producto, id_categoria) VALUES (:idProducto, :idCategoria)",
           nativeQuery = true)
    int agregarAProducto(@Param("idProducto") Long idProducto, @Param("idCategoria") Long idCategoria);
}
