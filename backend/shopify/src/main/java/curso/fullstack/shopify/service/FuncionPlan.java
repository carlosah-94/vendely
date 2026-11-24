package curso.fullstack.shopify.service;

/** Funciones que dependen del plan de la tienda. */
public enum FuncionPlan {
    CREAR_PRODUCTO,
    PERSONALIZAR,
    PLANTILLAS,
    ENVIO_INTERNACIONAL,
    METODOS_PAGO,
    /** Agregar un empleado mas (cuenta los activos contra planes.max_empleados). */
    AGREGAR_EMPLEADO
}
