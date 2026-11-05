package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Login;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRepository extends JpaRepository<Login, Long> {

    // Cuentas del panel (dueños y superadmin): no tienen tienda
    Optional<Login> findByCorreoAndIdTiendaIsNull(String correo);

    boolean existsByCorreoAndIdTiendaIsNull(String correo);

    boolean existsByUsuarioAndIdTiendaIsNull(String usuario);

    // Clientes: el mismo correo puede existir en varias tiendas
    Optional<Login> findByCorreoAndIdTienda(String correo, Long idTienda);

    boolean existsByCorreoAndIdTienda(String correo, Long idTienda);

    boolean existsByUsuarioAndIdTienda(String usuario, Long idTienda);
}
