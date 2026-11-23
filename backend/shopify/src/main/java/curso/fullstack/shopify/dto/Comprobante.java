package curso.fullstack.shopify.dto;

/**
 * El comprobante ya generado: su nombre de archivo y el PDF en bytes.
 * Van juntos porque los dos usos lo necesitan completo: la descarga (para el nombre que ve el
 * cliente) y el correo de confirmación (para el nombre del adjunto).
 */
public record Comprobante(String nombreArchivo, byte[] contenido) {
}
