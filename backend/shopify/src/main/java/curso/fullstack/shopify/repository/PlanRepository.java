package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Plan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Short> {

    /** Planes en orden de precio (Gratis, Basico, Pro) para la tabla comparativa. */
    List<Plan> findAllByOrderByPrecioUsdAsc();
}
