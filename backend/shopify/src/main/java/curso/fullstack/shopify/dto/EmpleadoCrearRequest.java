package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El dueño agrega un empleado a su tienda.
 * Si el correo ya es de un empleado (que trabaja en otra tienda), se usa esa misma cuenta y la
 * contraseña no hace falta. Si es nuevo, se crea su cuenta con la contraseña que pone el dueño;
 * el empleado la cambia después en Seguridad.
 */
public record EmpleadoCrearRequest(
        @NotBlank(message = "Escribe los nombres") @Size(max = 80) String nombres,
        @NotBlank(message = "Escribe el apellido") @Size(max = 80) String primerApellido,

        @NotBlank(message = "Escribe el correo") @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correo,

        // Opcional aquí: solo es obligatoria si la cuenta es nueva (lo revisa EmpleadoService).
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe tener al menos una letra y un número")
        String contrasena,

        Boolean puedePedidos,
        Boolean puedeProductos,
        Boolean puedeInventario,
        Boolean puedeClientes
) {}
