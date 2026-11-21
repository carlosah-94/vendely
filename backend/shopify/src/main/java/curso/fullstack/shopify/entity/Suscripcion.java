package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "suscripciones": cada pago de plan de una tienda (30 dias).
 * Es el historial de pagos y la base del comprobante. El plan Gratis no genera suscripcion.
 * La base impide que una tienda tenga dos suscripciones PAGADAS que se crucen (ex_suscripciones_periodo).
 */
@Entity
@Table(name = "suscripciones")
@Getter
@Setter
public class Suscripcion {

    public static final short PENDIENTE = 0;
    public static final short PAGADA = 1;
    public static final short CANCELADA = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_suscripcion")
    private Long idSuscripcion;

    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    @Column(name = "id_plan", nullable = false)
    private Short idPlan;

    @Column(name = "fecha_inicio", nullable = false)
    private OffsetDateTime fechaInicio;

    /** fecha_inicio + 30 dias */
    @Column(name = "fecha_fin", nullable = false)
    private OffsetDateTime fechaFin;

    /** "Foto" del precio que pago: si despues cambia el precio del plan, este monto no cambia. */
    @Column(name = "monto_usd", nullable = false, precision = 8, scale = 2)
    private BigDecimal montoUsd;

    /** 0 pendiente · 1 pagada · 2 cancelada */
    @Column(name = "estado", nullable = false)
    private Short estado = PENDIENTE;

    /** Id de la orden de PayPal. Obligatorio cuando esta pagada (ck_suscripciones_pagada). */
    @Column(name = "referencia_pago", unique = true, length = 120)
    private String referenciaPago;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;
}
