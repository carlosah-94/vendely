package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Inventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventarioRepository extends JpaRepository<Inventario, Long> {

    /** Descuenta solo si alcanza el stock. Devuelve 1 si lo hizo, 0 si no había suficiente. */
    @Modifying
    @Query(value = """
            UPDATE inventario SET cantidad_disponible = cantidad_disponible - :cantidad
             WHERE id_variante = :idVariante AND cantidad_disponible >= :cantidad
            """, nativeQuery = true)
    int descontar(@Param("idVariante") Long idVariante, @Param("cantidad") int cantidad);
}