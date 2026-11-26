package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.MensajeSoporte;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeSoporteRepository extends JpaRepository<MensajeSoporte, Long> {

    /** La conversación de una tienda, de la más antigua a la más nueva (como un chat). */
    List<MensajeSoporte> findByIdTiendaOrderByFechaAscIdMensajeAsc(Long idTienda);

    /** Los mensajes de esas tiendas, del más nuevo al más antiguo (para la bandeja del equipo). */
    List<MensajeSoporte> findByIdTiendaInOrderByIdMensajeDesc(List<Long> idsTienda);
}
