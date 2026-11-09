package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Login;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginRepository extends JpaRepository<Login, Long> {

    // Cuentas del panel (dueños y superadmin): no tienen tienda
    Optional<Login> findByCorreoAndIdTiendaIsNull(String correo);

    boolean existsByCorreoAndIdTiendaIsNull(String correo);

    boolean existsByUsuarioAndIdTiendaIsNull(String usuario);

    // Clientes: el mismo correo puede existir en varias tiendas
    Optional<Login> findByCorreoAndIdTienda(String correo, Long idTienda);

    boolean existsByCorreoAndIdTienda(String correo, Long idTienda);

    boolean existsByUsuarioAndIdTienda(String usuario, Long idTienda);

    // ---- Issue #9: CRUD de clientes desde el panel del dueño

    // Clientes de UNA tienda (rol 3), los más nuevos primero. Filtrar por tienda es el candado multi-tienda.
    List<Login> findByIdTiendaAndIdRolOrderByFechaCreacionDesc(Long idTienda, Short idRol);

    // Un cliente solo si es de esa tienda: si es de otra (o no es cliente), vuelve vacío → 404.
    Optional<Login> findByIdLoginAndIdTiendaAndIdRol(Long idLogin, Long idTienda, Short idRol);

    // Al editar, el correo nuevo no puede ser de OTRO cliente de la misma tienda (sí puede ser el suyo).
    boolean existsByCorreoAndIdTiendaAndIdLoginNot(String correo, Long idTienda, Long idLogin);

    /**
     * Al eliminar un cliente SIN ventas, su carrito se borra antes que la cuenta: la FK
     * carritos → login no tiene cascade. Los ítems del carrito sí se borran en cascada.
     */
    @Modifying
    @Query(value = "DELETE FROM carritos WHERE id_login = :idLogin", nativeQuery = true)
    int borrarCarritoDeCliente(@Param("idLogin") Long idLogin);
}
