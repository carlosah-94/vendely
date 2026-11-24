package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.EmpleadoTienda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmpleadoTiendaRepository extends JpaRepository<EmpleadoTienda, EmpleadoTienda.Llave> {

    Optional<EmpleadoTienda> findByIdLoginAndIdTienda(Long idLogin, Long idTienda);

    /** Las tiendas donde trabaja un empleado (para su selector de tienda). */
    List<EmpleadoTienda> findByIdLoginAndEstadoOrderByIdTiendaAsc(Long idLogin, Short estado);

    /** Para el límite del plan: solo cuentan los activos. */
    long countByIdTiendaAndEstado(Long idTienda, Short estado);

    /** Un empleado de la tienda con los datos de su cuenta, para la pantalla del dueño. */
    interface Fila {
        Long getIdLogin();
        String getNombres();
        String getPrimerApellido();
        String getCorreo();
        Boolean getPuedePedidos();
        Boolean getPuedeProductos();
        Boolean getPuedeInventario();
        Boolean getPuedeClientes();
        Short getEstado();
        Short getEstadoCuenta();
        Boolean getDobleFactor();
        Long getFechaCreacion();     // segundos desde 1970 (el DTO la convierte en fecha)
    }

    @Query(value = """
            SELECT e.id_login AS "idLogin", l.nombres AS "nombres", l.primer_apellido AS "primerApellido",
                   l.correo AS "correo", e.puede_pedidos AS "puedePedidos", e.puede_productos AS "puedeProductos",
                   e.puede_inventario AS "puedeInventario", e.puede_clientes AS "puedeClientes",
                   e.estado AS "estado", l.estado AS "estadoCuenta", l.totp_activo AS "dobleFactor",
                   CAST(extract(epoch FROM e.fecha_creacion) AS bigint) AS "fechaCreacion"
              FROM empleados_tienda e
              JOIN login l ON l.id_login = e.id_login
             WHERE e.id_tienda = :idTienda
             ORDER BY e.fecha_creacion
            """, nativeQuery = true)
    List<Fila> filasDeTienda(Long idTienda);
}
