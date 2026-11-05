package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.RolUsuario;

/**
 * Quién hizo la petición, según su token. Los controllers lo reciben con
 * @AuthenticationPrincipal UsuarioAutenticado yo.
 * idTienda es null en las cuentas del panel; en los clientes es su tienda.
 * Regla multi-tienda: la tienda del cliente SIEMPRE se toma de aquí, nunca del JSON.
 */
public record UsuarioAutenticado(Long idLogin, RolUsuario rol, Long idTienda, String correo) {}
