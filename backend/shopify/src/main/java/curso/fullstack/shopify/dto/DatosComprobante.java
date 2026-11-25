package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Qué comprobante pide el cliente al comprar en una tienda de Perú (V25).
 *   tipo BOLETA:  documento opcional (DNI de 8 dígitos o CE de 9); obligatorio desde S/ 700.
 *   tipo FACTURA: RUC (con su dígito verificador) y razón social obligatorios.
 * null = boleta sin documento. El servicio y la base (ck_pedidos_*) vuelven a revisar todo.
 */
public record DatosComprobante(
        @Pattern(regexp = "^(BOLETA|FACTURA)$", message = "El comprobante debe ser BOLETA o FACTURA") String tipo,
        @Pattern(regexp = "^$|^(DNI|CE|RUC)$", message = "El documento debe ser DNI, CE o RUC") String tipoDocumento,
        @Pattern(regexp = "^$|^[0-9]{8,11}$", message = "El número de documento solo lleva dígitos") String numeroDocumento,
        @Size(max = 150, message = "La razón social puede tener hasta 150 caracteres")
        @Pattern(regexp = "^[^<>]*$", message = "No se permiten los signos < ni >") String razonSocial
) {}
