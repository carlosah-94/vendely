/**
 * Dígitos verificadores del RUC (Perú, SUNAT) y del RUT (Chile, SII). Es la misma fórmula del
 * backend (DocumentoTributario.java) y de la base (fn_ruc_valido y fn_rut_valido, V24): aquí sirve
 * para avisar al momento, mientras el dueño escribe; el servidor y la base lo vuelven a revisar.
 */

const PESOS_RUC = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];

/** El dígito verificador de los primeros 10 dígitos de un RUC. */
export function digitoRuc(diez) {
  const suma = [...diez].reduce((s, c, i) => s + Number(c) * PESOS_RUC[i], 0);
  const resto = 11 - (suma % 11);
  return resto === 10 ? 0 : resto === 11 ? 1 : resto;
}

/**
 * Revisa un RUC y devuelve el problema en palabras (o null si está bien). dni: el del dueño, para
 * revisar que un RUC 10 lo lleve adentro (10 + DNI + verificador).
 */
export function problemaRuc(ruc, dni) {
  if (!/^[0-9]{11}$/.test(ruc)) return 'El RUC tiene 11 dígitos';
  if (!/^(10|15|17|20)/.test(ruc)) return 'El RUC empieza con 10 (persona), 15, 17 o 20 (empresa)';
  if (Number(ruc[10]) !== digitoRuc(ruc.slice(0, 10))) return 'El RUC no es válido: revisa los números (el último dígito no coincide)';
  if (dni && ruc.startsWith('10') && ruc.slice(2, 10) !== dni) return `Tu RUC 10 debe contener tu DNI: 10 + ${dni} + un dígito`;
  return null;
}

/** RUT chileno con guion (12345678-5): verificador módulo 11 con pesos 2..7 desde la derecha (10 = K). */
export function rutValido(rut) {
  if (!/^[0-9]{7,8}-[0-9K]$/.test(rut)) return false;
  const [cuerpo, dv] = rut.split('-');
  let suma = 0;
  let peso = 2;
  for (let i = cuerpo.length - 1; i >= 0; i -= 1) {
    suma += Number(cuerpo[i]) * peso;
    peso = peso === 7 ? 2 : peso + 1;
  }
  const resto = 11 - (suma % 11);
  return dv === (resto === 11 ? '0' : resto === 10 ? 'K' : String(resto));
}
