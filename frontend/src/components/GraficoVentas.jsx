import { useState } from 'react';
import { formatoMoneda } from '../utils/formato';

const SERIE = '#2a78d6';
const ANCHO = 720;
const ALTO = 240;
const M = { arriba: 12, derecha: 12, abajo: 28, izquierda: 64 };

/** Un tope "redondo" para el eje: 1, 2 o 5 × 10ⁿ por encima del máximo. */
function topeRedondo(maximo) {
  if (maximo <= 0) return 1;
  const potencia = 10 ** Math.floor(Math.log10(maximo));
  return [1, 2, 5, 10].map((f) => f * potencia).find((v) => v >= maximo);
}

function fechaCorta(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  return new Date(a, m - 1, d).toLocaleDateString('es-PE', { day: 'numeric', month: 'short' });
}

/**
 * Ventas por día: área con línea de 2px, una sola serie (el título la nombra, sin leyenda).
 * Al pasar el mouse (o con las flechas del teclado), una línea vertical marca el día y un globo
 * muestra las ventas y los pedidos. Debajo, el botón "Ver tabla" da los mismos datos en texto.
 */
export default function GraficoVentas({ dias, moneda }) {
  const [activo, setActivo] = useState(null);
  const [tabla, setTabla] = useState(false);

  const ancho = ANCHO - M.izquierda - M.derecha;
  const alto = ALTO - M.arriba - M.abajo;
  const valores = dias.map((d) => Number(d.ventas));
  const tope = topeRedondo(Math.max(...valores, 0));
  const x = (i) => M.izquierda + (dias.length === 1 ? ancho / 2 : (i * ancho) / (dias.length - 1));
  const y = (v) => M.arriba + alto - (v / tope) * alto;

  const linea = valores.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const area = `${linea} L${x(valores.length - 1).toFixed(1)},${y(0)} L${x(0).toFixed(1)},${y(0)} Z`;
  const marcas = [0, 0.25, 0.5, 0.75, 1].map((f) => tope * f);
  // Unas 6 fechas en el eje X, sin amontonarse.
  const paso = Math.max(1, Math.ceil(dias.length / 6));

  function alMover(e) {
    const caja = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - caja.left) / caja.width) * ANCHO;
    const i = dias.length === 1 ? 0 : Math.round(((px - M.izquierda) / ancho) * (dias.length - 1));
    setActivo(Math.max(0, Math.min(dias.length - 1, i)));
  }

  function alTeclear(e) {
    if (e.key === 'ArrowRight') setActivo((i) => Math.min(dias.length - 1, (i ?? -1) + 1));
    if (e.key === 'ArrowLeft') setActivo((i) => Math.max(0, (i ?? dias.length) - 1));
  }

  const dia = activo != null ? dias[activo] : null;
  const izquierdaGlobo = dia ? (x(activo) / ANCHO) * 100 : 0;

  return (
    <div>
      <div className="relative">
        <svg
          viewBox={`0 0 ${ANCHO} ${ALTO}`}
          className="h-auto w-full touch-none outline-none"
          role="img"
          aria-label="Ventas por día"
          tabIndex={0}
          onPointerMove={alMover}
          onPointerLeave={() => setActivo(null)}
          onKeyDown={alTeclear}
          onBlur={() => setActivo(null)}
        >
          {marcas.map((v) => (
            <g key={v}>
              <line x1={M.izquierda} x2={ANCHO - M.derecha} y1={y(v)} y2={y(v)} stroke="#ebebeb" />
              <text x={M.izquierda - 8} y={y(v) + 4} textAnchor="end" fontSize="11" fill="#616161">
                {formatoMoneda(v, moneda).replace(/\.00$/, '')}
              </text>
            </g>
          ))}
          {dias.map((d, i) => (i % paso === 0 || i === dias.length - 1) && (
            <text key={d.fecha} x={x(i)} y={ALTO - 8} textAnchor="middle" fontSize="11" fill="#616161">
              {fechaCorta(d.fecha)}
            </text>
          ))}
          <path d={area} fill={SERIE} opacity="0.12" />
          <path d={linea} fill="none" stroke={SERIE} strokeWidth="2" strokeLinejoin="round" />
          {dia && (
            <>
              <line x1={x(activo)} x2={x(activo)} y1={M.arriba} y2={y(0)} stroke="#8a8a8a" strokeDasharray="3 3" />
              <circle cx={x(activo)} cy={y(valores[activo])} r="5" fill={SERIE} stroke="#fff" strokeWidth="2" />
            </>
          )}
        </svg>
        {dia && (
          <div
            className="pointer-events-none absolute top-0 -translate-x-1/2 rounded-lg bg-[#1a1a1a] px-3 py-2 text-xs text-white shadow-lg"
            style={{ left: `${Math.min(88, Math.max(12, izquierdaGlobo))}%` }}
          >
            <p className="text-sm font-semibold">{formatoMoneda(dia.ventas, moneda)}</p>
            <p className="text-[#c3c2b7]">{dia.pedidos} pedido{dia.pedidos === 1 ? '' : 's'} · {fechaCorta(dia.fecha)}</p>
          </div>
        )}
      </div>

      <button type="button" onClick={() => setTabla((t) => !t)} className="mt-2 text-xs font-medium text-[#005bd3] hover:underline">
        {tabla ? 'Ocultar tabla' : 'Ver tabla'}
      </button>
      {tabla && (
        <div className="mt-2 max-h-64 overflow-y-auto rounded-lg border border-[#ebebeb]">
          <table className="min-w-full text-[13px]">
            <thead className="sticky top-0 bg-[#f7f7f7] text-left text-[#616161]">
              <tr>
                <th className="px-3 py-2 font-medium">Día</th>
                <th className="px-3 py-2 text-right font-medium">Pedidos</th>
                <th className="px-3 py-2 text-right font-medium">Ventas</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#ebebeb]">
              {dias.map((d) => (
                <tr key={d.fecha}>
                  <td className="px-3 py-1.5">{fechaCorta(d.fecha)}</td>
                  <td className="px-3 py-1.5 text-right">{d.pedidos}</td>
                  <td className="px-3 py-1.5 text-right">{formatoMoneda(d.ventas, moneda)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
