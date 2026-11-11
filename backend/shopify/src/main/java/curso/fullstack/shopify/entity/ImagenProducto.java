package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "imagenes_producto": las fotos de un producto, guardadas como enlaces (URL).
 * posicion = orden en la ficha; la 1 es la foto principal (UNIQUE id_producto + posicion).
 */
@Entity
@Table(name = "imagenes_producto")
@Getter
public class ImagenProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_imagen")
    private Long idImagen;

    @Setter
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    @Setter
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** Texto para lectores de pantalla. Se usa el nombre del producto. */
    @Setter
    @Column(name = "texto_alternativo", length = 200)
    private String textoAlternativo;

    @Setter
    @Column(name = "posicion", nullable = false)
    private Short posicion;
}
