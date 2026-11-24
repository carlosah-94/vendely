package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "empleados_tienda": en qué tienda trabaja un empleado y qué puede hacer ahí.
 * La llave es compuesta (id_login, id_tienda): la misma persona puede trabajar en varias tiendas.
 */
@Entity
@Table(name = "empleados_tienda")
@IdClass(EmpleadoTienda.Llave.class)
@Getter
@Setter
public class EmpleadoTienda {

    public static final short ACTIVO = 1;
    public static final short SUSPENDIDO = 0;

    @Id
    @Column(name = "id_login")
    private Long idLogin;

    @Id
    @Column(name = "id_tienda")
    private Long idTienda;

    /** Siempre 4 (EMPLEADO): la base lo exige para la FK compuesta con login. */
    @Column(name = "id_rol", nullable = false)
    private Short idRol = RolUsuario.EMPLEADO.getId();

    @Column(name = "puede_pedidos", nullable = false)
    private Boolean puedePedidos = false;

    @Column(name = "puede_productos", nullable = false)
    private Boolean puedeProductos = false;

    @Column(name = "puede_inventario", nullable = false)
    private Boolean puedeInventario = false;

    @Column(name = "puede_clientes", nullable = false)
    private Boolean puedeClientes = false;

    /** 1 activo · 0 suspendido */
    @Column(name = "estado", nullable = false)
    private Short estado = ACTIVO;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    private OffsetDateTime fechaModificacion;

    public boolean estaActivo() {
        return estado != null && estado == ACTIVO;
    }

    /** ¿Tiene este permiso? */
    public boolean puede(PermisoTienda permiso) {
        return switch (permiso) {
            case PEDIDOS -> Boolean.TRUE.equals(puedePedidos);
            case PRODUCTOS -> Boolean.TRUE.equals(puedeProductos);
            case INVENTARIO -> Boolean.TRUE.equals(puedeInventario);
            case CLIENTES -> Boolean.TRUE.equals(puedeClientes);
        };
    }

    /** La llave compuesta, como la pide JPA para @IdClass. */
    public static class Llave implements Serializable {
        private Long idLogin;
        private Long idTienda;

        public Llave() {
        }

        public Llave(Long idLogin, Long idTienda) {
            this.idLogin = idLogin;
            this.idTienda = idTienda;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Llave l && Objects.equals(idLogin, l.idLogin) && Objects.equals(idTienda, l.idTienda);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idLogin, idTienda);
        }
    }
}
