import { useState } from 'react';

const ORDENES = [
  { valor: 'nombre', nombre: 'Nombre (A-Z)' },
  { valor: 'precio_asc', nombre: 'Precio: de menor a mayor' },
  { valor: 'precio_desc', nombre: 'Precio: de mayor a menor' },
  { valor: 'novedades', nombre: 'Novedades' },
];

/**
 * Barra de filtros del catálogo (issue #4): buscar, rango de precio, solo disponibles y orden.
 *
 * No guarda estado propio salvo lo que se está escribiendo: la verdad vive en la URL, y la
 * portada se la pasa en `filtros`. Así un enlace con filtros abierto en otra pestaña muestra
 * exactamente lo mismo, que es justo lo que se quiere al compartir una búsqueda.
 *
 * El texto y los precios se aplican al enviar el formulario (no en cada tecla): escribir "polo"
 * serían cuatro peticiones al backend, y cada una cambiaría la URL.
 */
export default function FiltrosCatalogo({ filtros, total, onCambiar }) {
  const [texto, setTexto] = useState(filtros.q);
  const [min, setMin] = useState(filtros.min);
  const [max, setMax] = useState(filtros.max);

  // Si los filtros cambian desde afuera (el botón "Quitar filtros", o el botón atrás del
  // navegador), los campos tienen que reflejarlo. Se compara con lo que se recibió la última
  // vez y se corrige durante el render, que es la forma que recomienda React para esto: con un
  // useEffect se pintaría primero el valor viejo y luego el nuevo.
  const [recibidos, setRecibidos] = useState({ q: filtros.q, min: filtros.min, max: filtros.max });
  if (recibidos.q !== filtros.q || recibidos.min !== filtros.min || recibidos.max !== filtros.max) {
    setRecibidos({ q: filtros.q, min: filtros.min, max: filtros.max });
    setTexto(filtros.q);
    setMin(filtros.min);
    setMax(filtros.max);
  }

  const hayFiltros = filtros.q || filtros.min || filtros.max || filtros.stock || filtros.categoria;

  return (
    <div className="mb-6 space-y-3">
      <form
        className="flex flex-wrap items-end gap-3 text-sm"
        onSubmit={(e) => {
          e.preventDefault();
          onCambiar({ q: texto, min, max });
        }}
      >
        <label className="block grow sm:grow-0">
          <span className="mb-1 block text-[#616161]">Buscar</span>
          <input
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Nombre, marca..."
            className="w-full rounded-md border border-[#d9d9d9] px-3 py-2 sm:w-52"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-[#616161]">Precio desde</span>
          <input
            value={min}
            onChange={(e) => setMin(e.target.value)}
            inputMode="decimal"
            placeholder="0"
            className="w-24 rounded-md border border-[#d9d9d9] px-3 py-2"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-[#616161]">hasta</span>
          <input
            value={max}
            onChange={(e) => setMax(e.target.value)}
            inputMode="decimal"
            placeholder="—"
            className="w-24 rounded-md border border-[#d9d9d9] px-3 py-2"
          />
        </label>

        <button
          type="submit"
          className="rounded-md bg-[var(--tienda)] px-4 py-2 font-medium text-[var(--tienda-texto)] hover:brightness-90"
        >
          Aplicar
        </button>
      </form>

      <div className="flex flex-wrap items-center gap-4 text-sm">
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={filtros.stock}
            onChange={(e) => onCambiar({ stock: e.target.checked })}
            className="h-4 w-4"
          />
          Solo disponibles
        </label>

        <label className="flex items-center gap-2">
          <span className="text-[#616161]">Ordenar por</span>
          <select
            value={filtros.orden}
            onChange={(e) => onCambiar({ orden: e.target.value })}
            className="rounded-md border border-[#d9d9d9] px-2 py-1.5"
          >
            {ORDENES.map((o) => (
              <option key={o.valor} value={o.valor}>{o.nombre}</option>
            ))}
          </select>
        </label>

        {total !== null && (
          <span className="text-[#616161]">
            {total} {total === 1 ? 'producto' : 'productos'}
          </span>
        )}

        {hayFiltros && (
          <button
            type="button"
            onClick={() => onCambiar({ q: '', min: '', max: '', stock: false, categoria: '' })}
            className="underline underline-offset-4 hover:text-[#101010]"
          >
            Quitar filtros
          </button>
        )}
      </div>
    </div>
  );
}
