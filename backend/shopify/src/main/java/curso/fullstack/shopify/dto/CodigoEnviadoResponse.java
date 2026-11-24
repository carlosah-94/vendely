package curso.fullstack.shopify.dto;

/**
 * Respuesta al pedir el código de verificación.
 *
 * @param requerido          false si la verificación está apagada (app.registro.verificar-correo):
 *                           entonces no se envió nada y el registro no pide código
 * @param correo             a dónde se envió, ya normalizado
 * @param venceEnSegundos    cuánto dura el código
 * @param reenviarEnSegundos cuánto esperar para pedir otro
 */
public record CodigoEnviadoResponse(boolean requerido, String correo, long venceEnSegundos, long reenviarEnSegundos) {}
