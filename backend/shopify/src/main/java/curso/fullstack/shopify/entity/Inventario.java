package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "inventario": stock de cada variante (una fila por variante).
 * Con el CRUD de productos (issue #10) se crea y se edita; las ventas lo descuentan
 * con un UPDATE condicional en InventarioRepository (seguro ante compras simultáneas).
 */
@Entity
@Table(name = "inventario")
@Getter
public class Inventario {
    /** La llave es la misma variante: no es autogenerada, se asigna al crear. */
    @Setter
    @Id
    @Column(name = "id_variante")
    private Long idVariante;

    /** CHECK cantidad_disponible >= 0 */
    @Setter
    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;

    /** Alerta de stock bajo. Se envía 0 porque la columna es NOT NULL. */
    @Setter
    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 0;
}
