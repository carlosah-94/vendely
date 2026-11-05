package curso.fullstack.shopify.entity;

/**
 * Los tres roles de la tabla "roles". El número es el id_rol guardado en la base.
 * Es como el color de la pulsera en un evento: dice a qué zonas puede entrar cada uno.
 */
public enum RolUsuario {
    SUPERADMIN((short) 1),
    ADMIN_TIENDA((short) 2),
    CLIENTE((short) 3);

    private final short id;

    RolUsuario(short id) {
        this.id = id;
    }

    public short getId() {
        return id;
    }

    public static RolUsuario desdeId(short id) {
        for (RolUsuario r : values()) {
            if (r.id == id) {
                return r;
            }
        }
        throw new IllegalArgumentException("Rol desconocido: " + id);
    }
}
