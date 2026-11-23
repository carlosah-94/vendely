package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.ImagenProducto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagenProductoRepository extends JpaRepository<ImagenProducto, Long> {

    /**
     * Borra todas las fotos de un producto. Al editar se borran y se vuelven a crear en el orden
     * nuevo: así no choca la llave UNIQUE (id_producto, posicion) al reordenarlas.
     */
    @Modifying
    @Query(value = "DELETE FROM imagenes_producto WHERE id_producto = :idProducto", nativeQuery = true)
    int borrarDeProducto(@Param("idProducto") Long idProducto);

    /**
     * Los public_id de las fotos de un producto que estan subidas a Cloudinary (las pegadas de
     * otra pagina tienen public_id NULL y no son nuestras). Con esto se sabe cuales hay que
     * pedir que se borren cuando se quitan del producto.
     */
    @Query(value = "SELECT public_id FROM imagenes_producto WHERE id_producto = :idProducto AND public_id IS NOT NULL",
           nativeQuery = true)
    List<String> publicIdsDe(@Param("idProducto") Long idProducto);
}
