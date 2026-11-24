package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotNull;

/** El dueño cambia los permisos de un empleado o lo suspende (activo = false) sin quitarlo. */
public record EmpleadoEditarRequest(
        @NotNull Boolean puedePedidos,
        @NotNull Boolean puedeProductos,
        @NotNull Boolean puedeInventario,
        @NotNull Boolean puedeClientes,
        @NotNull Boolean activo
) {}
