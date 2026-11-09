package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import lombok.Getter;

/** Tabla "variantes" (solo lo necesario por ahora; el CRUD de productos la completará). */
@Entity
@Table(name = "variantes")
@Getter
public class Variante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_variante")
    private Long idVariante;

    @Column(name = "id_producto", nullable = false)
    private Long idProducto;
}
