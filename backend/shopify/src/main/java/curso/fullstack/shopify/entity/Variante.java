package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "variantes": cada combinación que se vende ("M / Negro"), con su SKU. Su precio está en
 * precios, su stock en inventario y sus valores en variante_valores.
 * Un producto simple tiene UNA variante sin valores; uno con opciones (talla, color) tiene una
 * por combinación. estado no se mapea: lo pone la base por DEFAULT (1 = activa).
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

    /** Orden en la ficha del producto (1, 2, 3...). CHECK posicion >= 1. */
    @Setter
    @Column(name = "posicion", nullable = false)
    private Short posicion = 1;
}
