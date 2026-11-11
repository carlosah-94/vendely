package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.repository.AdminRepository;
import java.time.Instant;

/** Una cuenta de Vendely (de cualquier rol) vista por el superadmin. Nunca incluye la contraseña. */
public record AdminCuentaResponse(
        Long idLogin,
        String rol,                 // "SUPERADMIN", "ADMIN_TIENDA" o "CLIENTE"
        String nombre,
        String correo,
        String tienda,              // solo clientes: la tienda donde se registró
        long tiendasPropias,        // solo dueños: cuántas tiendas tiene
        String estado,              // "ACTIVO" o "INACTIVO"
        String fechaCreacion        // ISO 8601 en UTC, por ejemplo 2026-11-11T08:30:00Z
) {
    public static AdminCuentaResponse desde(AdminRepository.FilaCuenta f) {
        return new AdminCuentaResponse(f.getIdLogin(), RolUsuario.desdeId(f.getIdRol()).name(), f.getNombre(),
                f.getCorreo(), f.getTienda(), f.getTiendasPropias() == null ? 0 : f.getTiendasPropias(),
                f.getEstado() != null && f.getEstado() == 1 ? "ACTIVO" : "INACTIVO", Instant.ofEpochSecond(f.getFechaCreacion()).toString());
    }
}
