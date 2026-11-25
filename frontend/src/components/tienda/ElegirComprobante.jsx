/* eslint-disable react-refresh/only-export-components */
import { problemaRuc } from '../../utils/documentos';

/**
 * "Comprobante" en el checkout de una tienda de Perú (V25): boleta o factura, como en cualquier
 * tienda peruana. Mismas reglas que el servidor y la base (ck_pedidos_*):
 *   Boleta:  DNI (8) o carné de extranjería (9), opcional; obligatorio desde S/ 700 (SUNAT).
 *   Factura: RUC (con su dígito verificador) y razón social de la empresa.
 */

export const COMPROBANTE_INICIAL = { tipo: 'BOLETA', tipoDocumento: 'DNI', numeroDocumento: '', razonSocial: '' };
export const MONTO_BOLETA_CON_DNI = 700;

/** El problema del comprobante (o null), para no dejar pagar hasta corregirlo. */
export function problemaComprobante(c, total) {
  const numero = c.numeroDocumento.trim();
  if (c.tipo === 'FACTURA') {
    const p = problemaRuc(numero);
    if (p) return { campo: 'numeroDocumento', texto: p };
    if (c.razonSocial.trim().length < 3) return { campo: 'razonSocial', texto: 'Escribe la razón social de la empresa' };
    return null;
  }
  if (!numero) {
    return total >= MONTO_BOLETA_CON_DNI
      ? { campo: 'numeroDocumento', texto: 'Para una boleta desde S/ 700, la SUNAT pide tu DNI o carné de extranjería' }
      : null;
  }
  if (c.tipoDocumento === 'DNI' && !/^[0-9]{8}$/.test(numero)) return { campo: 'numeroDocumento', texto: 'El DNI tiene 8 dígitos' };
  if (c.tipoDocumento === 'CE' && !/^[0-9]{9}$/.test(numero)) return { campo: 'numeroDocumento', texto: 'El carné de extranjería tiene 9 dígitos' };
  return null;
}

/** Lo que se manda al crear el pedido. */
export function comprobanteParaEnviar(c) {
  const numero = c.numeroDocumento.trim();
  return c.tipo === 'FACTURA'
    ? { tipo: 'FACTURA', tipoDocumento: 'RUC', numeroDocumento: numero, razonSocial: c.razonSocial.trim() }
    : { tipo: 'BOLETA', tipoDocumento: numero ? c.tipoDocumento : null, numeroDocumento: numero || null, razonSocial: null };
}

const CAMPO = 'w-full rounded-md border px-3 py-2.5 text-sm';

export default function ElegirComprobante({ valor: c, total, bloqueado, mostrarErrores, onCambiar }) {
  const problema = mostrarErrores ? problemaComprobante(c, total) : null;
  const borde = (campo) => (problema?.campo === campo ? 'border-[#c0392b]' : 'border-[#c9c9c9]');
  const set = (campo) => (e) => onCambiar({ ...c, [campo]: campo === 'numeroDocumento' ? e.target.value.replace(/\D/g, '').slice(0, 11) : e.target.value });

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">Comprobante</h2>
      <div className="grid grid-cols-2 gap-2">
        {[['BOLETA', 'Boleta', 'Para ti, con tu DNI'], ['FACTURA', 'Factura', 'Para tu empresa, con RUC']].map(([tipo, nombre, ayuda]) => (
          <label key={tipo} className={`flex cursor-pointer items-start gap-2 rounded-md border p-3 text-sm ${
            c.tipo === tipo ? 'border-[#303030] bg-[#f5f5f5]' : 'border-[#d9d9d9]'} ${bloqueado ? 'cursor-not-allowed opacity-60' : ''}`}>
            <input type="radio" name="comprobante" checked={c.tipo === tipo} disabled={bloqueado} className="mt-1"
              onChange={() => onCambiar({ ...COMPROBANTE_INICIAL, tipo })} />
            <span><b className="block">{nombre}</b><span className="text-xs text-[#616161]">{ayuda}</span></span>
          </label>
        ))}
      </div>

      {c.tipo === 'BOLETA' ? (
        <div className="grid grid-cols-[140px_1fr] gap-2">
          <select value={c.tipoDocumento} disabled={bloqueado} onChange={set('tipoDocumento')} className={`${CAMPO} border-[#c9c9c9]`} aria-label="Tipo de documento">
            <option value="DNI">DNI</option>
            <option value="CE">Carné de extranjería</option>
          </select>
          <input value={c.numeroDocumento} disabled={bloqueado} onChange={set('numeroDocumento')} inputMode="numeric"
            placeholder={total >= MONTO_BOLETA_CON_DNI ? 'Obligatorio desde S/ 700' : 'Opcional'}
            className={`${CAMPO} ${borde('numeroDocumento')}`} aria-label="Número de documento" />
        </div>
      ) : (
        <div className="space-y-2">
          <input value={c.numeroDocumento} disabled={bloqueado} onChange={set('numeroDocumento')} inputMode="numeric"
            placeholder="RUC de la empresa (11 dígitos)" className={`${CAMPO} ${borde('numeroDocumento')}`} aria-label="RUC" />
          <input value={c.razonSocial} disabled={bloqueado} onChange={set('razonSocial')} maxLength={150}
            placeholder="Razón social (ej. Gloria S.A.)" className={`${CAMPO} ${borde('razonSocial')}`} aria-label="Razón social" />
        </div>
      )}
      {problema && <p className="text-xs text-[#c0392b]">{problema.texto}</p>}
      <p className="text-xs text-[#616161]">
        El número de tu {c.tipo === 'FACTURA' ? 'factura (F001-…)' : 'boleta (B001-…)'} se asigna al confirmarse el pago y te llega por correo.
      </p>
    </section>
  );
}
