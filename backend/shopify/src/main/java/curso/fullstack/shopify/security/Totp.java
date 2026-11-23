package curso.fullstack.shopify.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de 6 dígitos que cambian cada 30 segundos (TOTP, RFC 6238), los mismos que muestran
 * Google Authenticator, Microsoft Authenticator o Authy.
 *
 * Cómo funciona, en corto: el servidor y el teléfono comparten un secreto. Cada 30 segundos los
 * dos calculan HMAC-SHA1(secreto, número de intervalo) y lo reducen a 6 dígitos. Como usan el
 * mismo secreto y el mismo reloj, llegan al mismo número sin hablarse.
 *
 * Está escrito a mano (sin librería) porque es corto y así se entiende qué se compara. Las
 * pruebas lo contrastan con los valores de ejemplo del propio RFC.
 */
public final class Totp {

    private static final int PERIODO_SEG = 30;
    private static final int DIGITOS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom AZAR = new SecureRandom();

    private Totp() {
    }

    /** Un secreto nuevo: 20 bytes al azar (160 bits, lo que recomienda el RFC), en base32. */
    public static String generarSecreto() {
        byte[] bytes = new byte[20];
        AZAR.nextBytes(bytes);
        return aBase32(bytes);
    }

    /** El intervalo de 30 segundos en el que cae un instante (segundos desde 1970). */
    public static long pasoDe(long segundos) {
        return Math.floorDiv(segundos, PERIODO_SEG);
    }

    /** El código de 6 dígitos de un intervalo. */
    public static String codigo(String secretoBase32, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(deBase32(secretoBase32), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            // "Truncado dinámico" del RFC 4226: el último medio byte dice desde dónde leer 4 bytes.
            int desde = h[h.length - 1] & 0x0F;
            int numero = ((h[desde] & 0x7F) << 24)
                    | ((h[desde + 1] & 0xFF) << 16)
                    | ((h[desde + 2] & 0xFF) << 8)
                    | (h[desde + 3] & 0xFF);
            return String.format("%0" + DIGITOS + "d", numero % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 siempre existe en Java", e);
        }
    }

    /**
     * Comprueba un código escrito por la persona. Devuelve el intervalo en que coincidió, o -1.
     *
     * Se acepta el intervalo actual, el anterior y el siguiente (±30 s): el reloj del teléfono
     * y el del servidor nunca están exactamente iguales, y la persona tarda en escribir.
     * Un intervalo igual o anterior a ultimoPasoUsado se rechaza: así un código no sirve dos veces.
     */
    public static long verificar(String secretoBase32, String escrito, long ahoraSeg, Long ultimoPasoUsado) {
        if (secretoBase32 == null || escrito == null || !escrito.trim().matches("\\d{" + DIGITOS + "}")) {
            return -1;
        }
        byte[] recibido = escrito.trim().getBytes(StandardCharsets.US_ASCII);
        long actual = pasoDe(ahoraSeg);
        for (long paso = actual - 1; paso <= actual + 1; paso++) {
            if (ultimoPasoUsado != null && paso <= ultimoPasoUsado) {
                continue;
            }
            byte[] esperado = codigo(secretoBase32, paso).getBytes(StandardCharsets.US_ASCII);
            // Comparación en tiempo constante: equals se cortaría en el primer dígito distinto y
            // el tiempo de respuesta diría cuántos dígitos se acertaron.
            if (MessageDigest.isEqual(esperado, recibido)) {
                return paso;
            }
        }
        return -1;
    }

    /**
     * El enlace que va dentro del código QR. Las apps lo leen y agregan la cuenta solas:
     * otpauth://totp/Vendely:correo?secret=...&issuer=Vendely
     */
    public static String enlaceParaApp(String secretoBase32, String cuenta, String emisor) {
        String etiqueta = codificar(emisor) + ":" + codificar(cuenta);
        return "otpauth://totp/" + etiqueta + "?secret=" + secretoBase32 + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO_SEG;
    }

    // ------------------------------------------------------------------ base32 (RFC 4648)

    static String aBase32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] deBase32(String texto) {
        String limpio = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("El secreto no es base32");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        byte[] resultado = new byte[salida.position()];
        salida.flip();
        salida.get(resultado);
        return resultado;
    }

    private static String codificar(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
