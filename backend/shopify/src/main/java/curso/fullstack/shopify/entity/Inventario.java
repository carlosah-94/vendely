package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import lombok.Getter;

/** Tabla "inventario": stock de cada variante. */
@Entity
@Table(name = "inventario")
@Getter
public class Inventario {

    @Id
    @Column(name = "id_variante")
    private Long idVariante;

    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;
}
