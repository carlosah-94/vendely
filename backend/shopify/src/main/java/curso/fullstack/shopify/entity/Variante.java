package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "variantes". Antes solo se leía (ventas, issue #7); con el CRUD de productos (issue #10)
 * también se crea: un producto simple tiene UNA variante, sin opciones (talla, color…).
 * posicion y estado no se mapean: los pone la base por DEFAULT.
 */
@Entity
@Table(name = "variantes")
@Getter
public class Variante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_variante")
    private Long idVariante;

    @Setter
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Código interno del vendedor. Opcional. */
    @Setter
    @Column(name = "sku", length = 60)
    private String sku;
}
