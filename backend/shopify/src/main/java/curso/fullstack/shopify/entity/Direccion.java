package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import lombok.Getter;

/** Tabla "direcciones" (solo lo necesario para validar el envío). */
@Entity
@Table(name = "direcciones")
@Getter
public class Direccion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_direccion")
    private Long idDireccion;

    @Column(name = "id_login")
    private Long idLogin;

    /** 1 activa · 0 archivada */
    @Column(name = "estado", nullable = false)
    private Short estado;
}
