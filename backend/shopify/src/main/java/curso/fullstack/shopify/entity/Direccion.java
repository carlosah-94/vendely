package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "direcciones".
 * Antes solo se leía para validar el envío de una venta (issue #7). Con el CRUD de clientes
 * (issue #9) también se CREA, por eso ahora se mapean las columnas necesarias para insertar.
 * Las columnas que no se usan (region, ciudad, codigo_postal, latitud, longitud) no se mapean:
 * son opcionales y, en un país con lista oficial como Perú, deben quedar vacías (CHECK ubicación).
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

    /** País de la dirección (ISO 3166-1). Es el de la tienda: la tienda vende dentro de su país. */
    @Setter
    @Column(name = "id_pais", nullable = false, length = 2, columnDefinition = "bpchar(2)")
    private String idPais;

    /** Distrito de la lista oficial (ubigeo en Perú). El trigger T12 exige el último nivel. */
    @Setter
    @Column(name = "id_division")
    private Long idDivision;

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
