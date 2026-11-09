package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Direccion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DireccionRepository extends JpaRepository<Direccion, Long> {
    boolean existsByIdDireccionAndIdLoginAndEstado(Long idDireccion, Long idLogin, Short estado);
}