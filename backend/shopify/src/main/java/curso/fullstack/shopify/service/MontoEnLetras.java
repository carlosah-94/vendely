package curso.fullstack.shopify.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El total en letras, como en las boletas y facturas peruanas:
 * 110.50 -> "CIENTO DIEZ CON 50/100 SOLES". Sirve hasta 999 999 999.
 */
public final class MontoEnLetras {

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO",
            "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO",
            "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO",
            "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private MontoEnLetras() {}

    /** monto en letras + la moneda: "CIENTO DIEZ CON 00/100 SOLES". */
    public static String de(BigDecimal monto, String moneda) {
        BigDecimal m = (monto == null ? BigDecimal.ZERO : monto).setScale(2, RoundingMode.HALF_UP);
        long entero = m.longValue();
        int centimos = m.subtract(BigDecimal.valueOf(entero)).movePointRight(2).intValue();
        String nombre = switch (moneda == null ? "" : moneda.trim()) {
            case "PEN" -> "SOLES";
            case "CLP" -> "PESOS CHILENOS";
            case "USD" -> "DÓLARES AMERICANOS";
            default -> moneda == null ? "" : moneda.trim();
        };
        return (entero == 0 ? "CERO" : letras(entero)) + " CON " + String.format("%02d", centimos) + "/100 " + nombre;
    }

    static String letras(long n) {
        if (n >= 1_000_000) {
            long millones = n / 1_000_000;
            String resto = n % 1_000_000 == 0 ? "" : " " + letras(n % 1_000_000);
            return (millones == 1 ? "UN MILLON" : letras(millones).replaceAll("UNO$", "UN") + " MILLONES") + resto;
        }
        if (n >= 1000) {
            long miles = n / 1000;
            String resto = n % 1000 == 0 ? "" : " " + letras(n % 1000);
            return (miles == 1 ? "MIL" : letras(miles).replaceAll("UNO$", "UN") + " MIL") + resto;
        }
        if (n == 100) return "CIEN";
        if (n > 100) return CENTENAS[(int) n / 100] + (n % 100 == 0 ? "" : " " + letras(n % 100));
        if (n < 30) return UNIDADES[(int) n];
        return DECENAS[(int) n / 10] + (n % 10 == 0 ? "" : " Y " + UNIDADES[(int) n % 10]);
    }
}
