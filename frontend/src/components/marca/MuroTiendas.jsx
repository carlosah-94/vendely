import Ilustracion from './Ilustraciones';
import { TIENDAS_EJEMPLO } from './tiendasEjemplo';

/** Una tienda de ejemplo en miniatura, con el estilo de su plantilla. */
export function MiniTienda({ tienda, className = '' }) {
  const { nombre, plantilla, color, fondo, productos } = tienda;
  const boutique = plantilla === 'boutique';
  const moderna = plantilla === 'moderna';
  return (
    <div className={`overflow-hidden rounded-2xl bg-white text-left shadow-[0_18px_40px_-18px_rgba(0,0,0,0.55)] ${className}`}>
      {/* Barra del navegador */}
      <div className="flex items-center gap-1.5 bg-[#f2f2f4] px-3 py-2">
        <span className="h-2 w-2 rounded-full bg-[#ff5f57]" />
        <span className="h-2 w-2 rounded-full bg-[#febc2e]" />
        <span className="h-2 w-2 rounded-full bg-[#28c840]" />
        <span className="ml-2 truncate rounded-md bg-white px-2 py-0.5 text-[9px] text-[#777]">
          {nombre.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z]+/g, '-')}.vendely.pe
        </span>
      </div>
      {/* Portada según la plantilla */}
      {boutique ? (
        <div className="relative flex h-28 items-end p-3" style={{ background: fondo }}>
          <Ilustracion dibujo={productos[0][0]} color={color} className="absolute right-2 top-2 h-24 w-24" />
          <p className="letra-serifa relative text-[26px] leading-none" style={{ color }}>{nombre}</p>
        </div>
      ) : moderna ? (
        <div className="flex h-28 items-center justify-between gap-2 p-3" style={{ background: color }}>
          <p className="letra-ancha text-[19px] leading-[0.95] text-white">{nombre}</p>
          <span className="flex h-20 w-20 shrink-0 rotate-3 items-center justify-center rounded-xl bg-white">
            <Ilustracion dibujo={productos[0][0]} color={color} className="h-16 w-16" />
          </span>
        </div>
      ) : (
        <div className="flex h-28 flex-col items-center justify-center gap-1.5" style={{ background: color }}>
          <p className="text-[17px] font-semibold text-white">{nombre}</p>
          <span className="rounded bg-white px-2.5 py-1 text-[8px] font-semibold" style={{ color }}>Ver productos</span>
        </div>
      )}
      {/* Dos productos */}
      <div className="grid grid-cols-2 gap-2 p-3">
        {productos.map(([dibujo, producto, precio]) => (
          <div key={producto}>
            <div className={`flex aspect-square items-center justify-center ${boutique ? '' : 'rounded-lg'}`} style={{ background: fondo }}>
              <Ilustracion dibujo={dibujo} color={color} className="h-3/4 w-3/4" />
            </div>
            <p className={`mt-1.5 truncate text-[10px] text-[#333] ${boutique ? 'letra-serifa text-[12px]' : ''}`}>{producto}</p>
            <p className="text-[10px] font-semibold text-[#111]">S/ {precio}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * El muro: varias columnas de tiendas que se desplazan en direcciones alternas, inclinado,
 * como un video de fondo. Lo de encima (el título) lo pone quien lo usa.
 */
export default function MuroTiendas({ columnas = 5, className = '' }) {
  const lista = Array.from({ length: columnas }, (_, c) =>
    Array.from({ length: 5 }, (_, i) => TIENDAS_EJEMPLO[(c * 5 + i * (c >= 2 ? 3 : 1)) % TIENDAS_EJEMPLO.length]));

  return (
    <div className={`muro-tiendas pointer-events-none absolute inset-0 overflow-hidden ${className}`} aria-hidden="true">
      <div className="muro-tiendas__plano flex gap-5">
        {lista.map((tiendas, c) => (
          <div
            key={c}
            className={`muro-tiendas__columna flex w-56 shrink-0 flex-col gap-5 ${c % 2 ? 'muro-tiendas__columna--baja' : ''}`}
            style={{ animationDuration: `${46 + (c % 3) * 9}s` }}
          >
            {/* La lista va dos veces para que el bucle no tenga corte */}
            {[...tiendas, ...tiendas].map((t, i) => (
              <MiniTienda key={`${t.nombre}-${i}`} tienda={t} />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
