package curso.fullstack.shopify.service;

/**
 * Dígitos verificadores del RUC (Perú, SUNAT) y del RUT (Chile, SII). Es la misma fórmula que usa
 * la base (fn_ruc_valido y fn_rut_valido en V24) y la página (utils/documentos.js): así un número
 * mal escrito o inventado se rechaza en las tres capas.
 *
 * El verificador funciona como el último dígito de una tarjeta: se calcula con los demás, y si se
 * cambia uno solo por error, la cuenta ya no cuadra.
 */
public final class DocumentoTributario {

    private static final int[] PESOS_RUC = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private DocumentoTributario() {}

    /** RUC: 11 dígitos, empieza con 10, 15, 17 o 20, y el último es el verificador (módulo 11). */
    public static boolean esRucValido(String ruc) {
        if (ruc == null || !ruc.matches("^(10|15|17|20)[0-9]{9}$")) return false;
        return ruc.charAt(10) - '0' == digitoRuc(ruc.substring(0, 10));
    }

    /** El dígito verificador de los primeros 10 dígitos de un RUC. */
    public static int digitoRuc(String diezDigitos) {
        int suma = 0;
        for (int i = 0; i < 10; i++) suma += (diezDigitos.charAt(i) - '0') * PESOS_RUC[i];
        int resto = 11 - (suma % 11);
        return resto == 10 ? 0 : resto == 11 ? 1 : resto;
    }

    /** RUT chileno con guion: 12345678-5 (verificador módulo 11 con pesos 2..7; 10 = K). */
    public static boolean esRutValido(String rut) {
        if (rut == null || !rut.matches("^[0-9]{7,8}-[0-9K]$")) return false;
        String cuerpo = rut.substring(0, rut.indexOf('-'));
        int suma = 0;
        int peso = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += (cuerpo.charAt(i) - '0') * peso;
            peso = peso == 7 ? 2 : peso + 1;
        }
        int resto = 11 - (suma % 11);
        String dv = resto == 11 ? "0" : resto == 10 ? "K" : String.valueOf(resto);
        return rut.endsWith("-" + dv);
    }
}
