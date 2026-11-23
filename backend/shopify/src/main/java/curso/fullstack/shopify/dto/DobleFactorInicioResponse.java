package curso.fullstack.shopify.dto;

/**
 * Lo que necesita la pantalla para que la persona agregue la cuenta en su app:
 *  - enlace: va dentro del código QR (otpauth://totp/...); la app lo lee y se configura sola.
 *  - secreto: el mismo dato en texto, por si la cámara no lee el QR y hay que escribirlo.
 *
 * Es la única vez que el secreto sale del servidor.
 */
public record DobleFactorInicioResponse(String secreto, String enlace) {
}
