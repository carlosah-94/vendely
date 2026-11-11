package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "direcciones".
 * Antes solo se leía para validar el envío de una venta (issue #7). Con el CRUD de clientes
 * (issue #9) también se CREA, por eso ahora se mapean las columnas necesarias para insertar.
 * Dos formas de ubicar una dirección (CHECK ck_direcciones_ubicacion y trigger T12):
 * - En el país de la tienda (Perú): se elige el distrito de la lista oficial (id_division);
 *   region y ciudad quedan vacías.
 * - En otro país (España, por ejemplo): se escriben ciudad, región y código postal, como en Shopify;
 *   id_division queda vacío.
 * latitud y longitud no se usan y no se mapean.
 */
@Entity
@Table(name = "direcciones")
@Getter
public class Direccion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_direccion")
    private Long idDireccion;

    @Setter
    @Column(name = "id_login")
    private Long idLogin;

    /** País de la dirección (ISO 3166-1). Si es distinto al de la tienda, la venta es al extranjero. */
    @Setter
    @Column(name = "id_pais", nullable = false, length = 2, columnDefinition = "bpchar(2)")
    private String idPais;

    /** Distrito de la lista oficial (ubigeo en Perú). El trigger T12 exige el último nivel. */
    @Setter
    @Column(name = "id_division")
    private Long idDivision;

    /** Solo en otro país: estado o provincia, escrito a mano ("Madrid"). */
    @Setter
    @Column(name = "region", length = 100)
    private String region;

    /** Solo en otro país: ciudad escrita a mano ("Alcalá de Henares"). */
    @Setter
    @Column(name = "ciudad", length = 100)
    private String ciudad;

    /** Obligatorio si el país lo exige; su formato lo revisa la base (paises.patron_codigo_postal). */
    @Setter
    @Column(name = "codigo_postal", length = 12)
    private String codigoPostal;

    @Setter
    @Column(name = "destinatario", nullable = false, length = 160)
    private String destinatario;

    /** Formato internacional E.164 (CHECK ck_direcciones_telefono). */
    @Setter
    @Column(name = "telefono", nullable = false, length = 16)
    private String telefono;

    @Setter
    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @Setter
    @Column(name = "referencia", length = 200)
    private String referencia;

    @Setter
    @Column(name = "predeterminada", nullable = false)
    private Boolean predeterminada = false;

    /** 1 activa · 0 archivada */
    @Setter
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;
}
