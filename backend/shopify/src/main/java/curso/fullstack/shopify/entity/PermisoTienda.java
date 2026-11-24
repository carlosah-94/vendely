package curso.fullstack.shopify.entity;

/**
 * Las partes del panel que el dueño puede dar a un empleado (columnas puede_* de empleados_tienda).
 * El dueño las tiene todas. Lo que no está aquí (plan, personalizar, envíos, empleados,
 * eliminar ventas) lo hace solo el dueño.
 */
public enum PermisoTienda {
    PEDIDOS("pedidos"),
    PRODUCTOS("productos"),
    INVENTARIO("inventario"),
    CLIENTES("clientes");

    /** Para el mensaje de error: "Tu cuenta de empleado no tiene permiso de pedidos en esta tienda". */
    private final String texto;

    PermisoTienda(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
