package curso.fullstack.shopify.entity;

import jakarta.persistence.*;

/** Tabla "direcciones" (solo lo necesario para validar el envío). */
@Entity
@Table(name = "direcciones")
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

    public Long getIdDireccion() { return idDireccion; }
    public Long getIdLogin() { return idLogin; }
    public Short getEstado() { return estado; }
}