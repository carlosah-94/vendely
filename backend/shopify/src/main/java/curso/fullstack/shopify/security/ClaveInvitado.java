package curso.fullstack.shopify.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Clave de un invitado: quien compra sin cuenta no tiene token de sesión, así que cada dirección y
 * cada pedido que crea lleva una clave firmada (HMAC-SHA256 con el secreto del servidor). Es como
 * el enlace de seguimiento que mandan las tiendas por correo: solo quien tiene la clave puede ver
 * ese pedido o pagarlo, y no se puede adivinar ni fabricar sin el secreto. No se guarda en la base.
 *
 * La clave lleva el tipo ("direccion", "pedido"): la de una dirección no sirve para un pedido.
 */
@Component
public class ClaveInvitado {

    private final byte[] secreto;

    public ClaveInvitado(@Value("${app.jwt.secret}") String secreto) {
        this.secreto = ("invitado:" + secreto).getBytes(StandardCharsets.UTF_8);
    }

    public String firmar(String tipo, long idTienda, long id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            byte[] firma = mac.doFinal((tipo + ":" + idTienda + ":" + id).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(firma).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la clave", e);
        }
    }

    /** Compara en tiempo constante (no se puede adivinar la clave midiendo cuánto tarda). */
    public boolean valida(String clave, String tipo, long idTienda, long id) {
        if (clave == null || clave.isBlank()) return false;
        return MessageDigest.isEqual(firmar(tipo, idTienda, id).getBytes(StandardCharsets.UTF_8),
                clave.trim().getBytes(StandardCharsets.UTF_8));
    }
}
