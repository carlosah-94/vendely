/**
 * WhatsApp de la tienda (V17). Se guarda con código de país (+51987654321) y se abre con
 * https://wa.me/<número sin +>?text=<mensaje>, que funciona en el celular y en WhatsApp Web.
 */

const PREFIJOS = { PE: '+51', CL: '+56', MX: '+52', CO: '+57', AR: '+54', EC: '+593' };

export const PATRON_WHATSAPP = /^\+[1-9][0-9]{7,14}$/;

/**
 * Lo que escribe el dueño, listo para guardar: quita espacios y guiones, y si escribió solo
 * el número (987 654 321) le pone el código de su país. '' = sin WhatsApp.
 */
export function normalizarWhatsapp(texto, idPais) {
  const limpio = (texto ?? '').replace(/[\s\-().]/g, '');
  if (!limpio) return '';
  if (limpio.startsWith('+')) return limpio;
  if (limpio.startsWith('00')) return `+${limpio.slice(2)}`;
  return `${PREFIJOS[idPais] ?? '+51'}${limpio}`;
}

/** Enlace que abre el chat con el mensaje ya escrito. null si no hay número. */
export function enlaceWhatsapp(numero, mensaje) {
  if (!numero) return null;
  const digitos = numero.replace(/\D/g, '');
  return `https://wa.me/${digitos}${mensaje ? `?text=${encodeURIComponent(mensaje)}` : ''}`;
}

/** +51987654321 → "+51 987 654 321", para mostrarlo. */
export function whatsappLegible(numero) {
  if (!numero) return '';
  const m = numero.match(/^(\+(?:593|5[0-9]|[1-9]))(\d+)$/);
  if (!m) return numero;
  return `${m[1]} ${m[2].replace(/(\d{3})(?=\d)/g, '$1 ')}`;
}
