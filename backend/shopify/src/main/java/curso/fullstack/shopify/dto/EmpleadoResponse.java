package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.EmpleadoTiendaRepository;
import java.time.Instant;

/** Un empleado de la tienda, como lo ve su dueño. Nunca incluye la contraseña. */
public record EmpleadoResponse(
        Long idLogin,
        String nombres,
        String primerApellido,
        String correo,
        Boolean puedePedidos,
        Boolean puedeProductos,
        Boolean puedeInventario,
        Boolean puedeClientes,
        Boolean activo,             // en esta tienda (el dueño lo puede suspender)
        Boolean cuentaActiva,       // la cuenta en Vendely (la puede desactivar el superadmin)
        Boolean dobleFactor,        // si protege su cuenta con el código de 6 dígitos
        String fechaCreacion        // ISO 8601 en UTC: desde cuándo trabaja en la tienda
) {
    public static EmpleadoResponse desde(EmpleadoTiendaRepository.Fila f) {
        return new EmpleadoResponse(f.getIdLogin(), f.getNombres(), f.getPrimerApellido(), f.getCorreo(),
                f.getPuedePedidos(), f.getPuedeProductos(), f.getPuedeInventario(), f.getPuedeClientes(),
                f.getEstado() != null && f.getEstado() == 1, f.getEstadoCuenta() != null && f.getEstadoCuenta() == 1,
                Boolean.TRUE.equals(f.getDobleFactor()),
                f.getFechaCreacion() == null ? null : Instant.ofEpochSecond(f.getFechaCreacion()).toString());
    }
}
