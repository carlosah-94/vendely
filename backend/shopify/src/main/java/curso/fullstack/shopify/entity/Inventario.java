package curso.fullstack.shopify.entity;

import jakarta.persistence.*;

/** Tabla "inventario": stock de cada variante. */
@Entity
@Table(name = "inventario")
public class Inventario {

    @Id
    @Column(name = "id_variante")
    private Long idVariante;

    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;

    public Long getIdVariante() { return idVariante; }
    public Integer getCantidadDisponible() { return cantidadDisponible; }
    public Integer getStockMinimo() { return stockMinimo; }
}