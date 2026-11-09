package curso.fullstack.shopify.entity;

import jakarta.persistence.*;

/** Tabla "variantes" (solo lo necesario por ahora; el CRUD de productos la completará). */
@Entity
@Table(name = "variantes")
public class Variante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_variante")
    private Long idVariante;

    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    public Long getIdVariante() { return idVariante; }
    public Long getIdProducto() { return idProducto; }
}