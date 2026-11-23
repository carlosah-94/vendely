package curso.fullstack.shopify.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * El generador de códigos de la doble autenticación, contra los valores oficiales.
 *
 * Totp está escrito a mano, así que la única forma de confiar en él es compararlo con los
 * ejemplos del propio estándar: el Apéndice B del RFC 6238 publica, para un secreto fijo, el
 * código exacto que tiene que salir en cada instante. Si estos números coinciden, coincide
 * con Google Authenticator y con cualquier otra app que siga el estándar.
 *
 * No levanta Spring ni toca la base: corre en milisegundos y el CI lo ejecuta en cada PR.
 */
class TotpTest {

    /** El secreto del RFC: el texto "12345678901234567890" (20 bytes), pasado a base32. */
    private static final String SECRETO_RFC = Totp.aBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    /**
     * RFC 6238, Apéndice B, columna SHA1. El RFC da 8 dígitos; las apps usan 6, que son los
     * 6 últimos (94287082 → 287082).
     */
    @ParameterizedTest(name = "en el segundo {0} el código es {1}")
    @CsvSource({
            "59,          287082",
            "1111111109,  081804",
            "1111111111,  050471",
            "1234567890,  005924",
            "2000000000,  279037",
            "20000000000, 353130",
    })
    void coincideConLosEjemplosDelRfc(long segundo, String esperado) {
        assertEquals(esperado, Totp.codigo(SECRETO_RFC, Totp.pasoDe(segundo)));
    }

    @Test
    void elBase32EsElDelRfc4648() {
        // "12345678901234567890" en base32, el mismo valor que usan las demás implementaciones.
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", SECRETO_RFC);
        assertEquals("12345678901234567890", new String(Totp.deBase32(SECRETO_RFC), StandardCharsets.US_ASCII));
    }

    @Test
    void aceptaElCodigoDelIntervaloAnteriorYDelSiguiente() {
        long ahora = 1_234_567_890L;
        long paso = Totp.pasoDe(ahora);
        // El reloj del teléfono y el del servidor nunca están exactamente iguales.
        assertEquals(paso - 1, Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 1), ahora, null));
        assertEquals(paso, Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso), ahora, null));
        assertEquals(paso + 1, Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso + 1), ahora, null));
    }

    @Test
    void rechazaUnCodigoDeHaceDosIntervalos() {
        long ahora = 1_234_567_890L;
        String viejo = Totp.codigo(SECRETO_RFC, Totp.pasoDe(ahora) - 2);
        assertEquals(-1, Totp.verificar(SECRETO_RFC, viejo, ahora, null));
    }

    @Test
    void unCodigoYaUsadoNoSirveDeNuevo() {
        long ahora = 1_234_567_890L;
        long paso = Totp.pasoDe(ahora);
        String codigo = Totp.codigo(SECRETO_RFC, paso);
        // Primera vez: entra. Con ese intervalo marcado como usado, el mismo código ya no.
        assertEquals(paso, Totp.verificar(SECRETO_RFC, codigo, ahora, null));
        assertEquals(-1, Totp.verificar(SECRETO_RFC, codigo, ahora, paso));
    }

    @Test
    void rechazaLoQueNoSonSeisDigitos() {
        long ahora = 1_234_567_890L;
        assertEquals(-1, Totp.verificar(SECRETO_RFC, "12345", ahora, null));
        assertEquals(-1, Totp.verificar(SECRETO_RFC, "1234567", ahora, null));
        assertEquals(-1, Totp.verificar(SECRETO_RFC, "abcdef", ahora, null));
        assertEquals(-1, Totp.verificar(SECRETO_RFC, null, ahora, null));
    }

    @Test
    void cadaSecretoNuevoEsDistintoYTiene160Bits() {
        String a = Totp.generarSecreto();
        String b = Totp.generarSecreto();
        assertTrue(!a.equals(b), "dos secretos seguidos no pueden salir iguales");
        assertEquals(20, Totp.deBase32(a).length, "20 bytes = 160 bits, lo que recomienda el RFC");
    }

    @Test
    void elEnlaceDelQrTieneElFormatoQueLeenLasApps() {
        String enlace = Totp.enlaceParaApp("JBSWY3DPEHPK3PXP", "dueno@vendely.pe", "Vendely");
        assertEquals("otpauth://totp/Vendely:dueno%40vendely.pe?secret=JBSWY3DPEHPK3PXP&issuer=Vendely"
                + "&algorithm=SHA1&digits=6&period=30", enlace);
    }
}
