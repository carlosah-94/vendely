import { useState } from 'react';
import { claveVariante, combinaciones, MAX_OPCIONES, MAX_VARIANTES, nombreVariante } from '../utils/variantes';
import { IconoCerrar, IconoMas } from './Iconos';

/**
 * Opciones y variantes de un producto, como en Shopify:
 * 1. Se agregan opciones (Talla, Color) con sus valores (S, M, L / Negro, Blanco).
 * 2. Se generan solas todas las combinaciones ("M / Negro") y cada una tiene su precio, stock y SKU.
 *
 * opciones: [{ nombre, valores: [] }]
 * filas: { [clave]: { idVariante, precio, precioComparacion, stock, sku } } (datos escritos por combinación)
 */
export default function EditorVariantes({ opciones, onOpciones, filas, onFilas, precioBase, errores }) {
  const combos = combinaciones(opciones);

  function cambiarOpcion(indice, cambios) {
    onOpciones(opciones.map((o, i) => (i === indice ? { ...o, ...cambios } : o)));
  }

  function cambiarFila(clave, campo, valor) {
    onFilas({ ...filas, [clave]: { ...filas[clave], [campo]: valor } });
  }

  return (
    <div className="space-y-3">
      {opciones.map((o, i) => (
        <EditorOpcion
          key={i}
          opcion={o}
          error={errores[`opcion${i}`]}
          onCambiar={(cambios) => cambiarOpcion(i, cambios)}
          onQuitar={() => onOpciones(opciones.filter((_, j) => j !== i))}
        />
      ))}

      {opciones.length < MAX_OPCIONES && (
        <button
          type="button"
          onClick={() => onOpciones([...opciones, { nombre: opciones.length === 0 ? 'Talla' : opciones.length === 1 ? 'Color' : '', valores: [] }])}
          className="inline-flex items-center gap-1 text-[13px] font-medium text-[#1650d0] hover:underline"
        >
          <IconoMas tamano={15} /> {opciones.length === 0 ? 'Agregar opciones como talla o color' : 'Agregar otra opción'}
        </button>
      )}

      {errores.variantes && <p className="text-[12px] text-[#8e1f0b]">{errores.variantes}</p>}

      {combos.length > 0 && combos.length <= MAX_VARIANTES && (
        <div className="overflow-x-auto rounded-lg border border-[#e3e3e3]">
          <table className="min-w-full text-[13px]">
            <thead className="bg-[#f7f7f7] text-left text-[#616161]">
              <tr>
                <th className="px-3 py-2 font-medium">Variante</th>
                <th className="px-3 py-2 font-medium">Precio (S/)</th>
                <th className="px-3 py-2 font-medium">Antes (S/)</th>
                <th className="px-3 py-2 font-medium">Stock</th>
                <th className="px-3 py-2 font-medium">SKU</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#ebebeb]">
              {combos.map((valores) => {
                const clave = claveVariante(valores);
                const f = filas[clave] ?? {};
                const err = errores[`fila:${clave}`];
                return (
                  <tr key={clave} className={err ? 'bg-[#fff4f4]' : ''}>
                    <td className="whitespace-nowrap px-3 py-1.5 font-medium text-[#101010]">
                      {nombreVariante(valores)}
                      {err && <span className="block text-[11px] font-normal text-[#8e1f0b]">{err}</span>}
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        inputMode="decimal"
                        value={f.precio ?? ''}
                        placeholder={precioBase || '0.00'}
                        onChange={(e) => cambiarFila(clave, 'precio', e.target.value)}
                        aria-label={`Precio de ${nombreVariante(valores)}`}
                        className="w-24 rounded-md border border-[#c9c9c9] px-2 py-1"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        inputMode="decimal"
                        value={f.precioComparacion ?? ''}
                        onChange={(e) => cambiarFila(clave, 'precioComparacion', e.target.value)}
                        aria-label={`Precio de comparación de ${nombreVariante(valores)}`}
                        className="w-24 rounded-md border border-[#c9c9c9] px-2 py-1"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        inputMode="numeric"
                        value={f.stock ?? ''}
                        placeholder="0"
                        onChange={(e) => cambiarFila(clave, 'stock', e.target.value)}
                        aria-label={`Stock de ${nombreVariante(valores)}`}
                        className="w-16 rounded-md border border-[#c9c9c9] px-2 py-1"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        value={f.sku ?? ''}
                        onChange={(e) => cambiarFila(clave, 'sku', e.target.value)}
                        aria-label={`SKU de ${nombreVariante(valores)}`}
                        className="w-28 rounded-md border border-[#c9c9c9] px-2 py-1"
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <p className="border-t border-[#ebebeb] px-3 py-2 text-[12px] text-[#616161]">
            {combos.length} variantes. Si dejas el precio vacío se usa el precio del producto.
          </p>
        </div>
      )}
      {combos.length > MAX_VARIANTES && (
        <p className="text-[12px] text-[#8e1f0b]">Son {combos.length} combinaciones: el máximo es {MAX_VARIANTES}. Quita algunos valores.</p>
      )}
    </div>
  );
}

/** Una opción: su nombre y sus valores como fichas; un valor se agrega con Enter o coma. */
function EditorOpcion({ opcion, error, onCambiar, onQuitar }) {
  const [nuevo, setNuevo] = useState('');

  /** Agrega los valores que no estén repetidos (sin distinguir mayúsculas). */
  function agregar(textos) {
    const valores = [...opcion.valores];
    for (const texto of textos) {
      const valor = texto.trim().slice(0, 50);
      if (valor && !valores.some((v) => v.toLowerCase() === valor.toLowerCase())) valores.push(valor);
    }
    if (valores.length !== opcion.valores.length) onCambiar({ valores });
  }

  function agregarValor() {
    agregar([nuevo]);
    setNuevo('');
  }

  // Escribir "S, M, L" agrega los tres: cada coma cierra un valor.
  function escribir(texto) {
    if (!texto.includes(',')) {
      setNuevo(texto);
      return;
    }
    const partes = texto.split(',');
    const ultimo = partes.pop();
    agregar(partes);
    setNuevo(ultimo);
  }

  return (
    <div className="rounded-lg border border-[#e3e3e3] p-3">
      <div className="flex items-center gap-2">
        <input
          value={opcion.nombre}
          onChange={(e) => onCambiar({ nombre: e.target.value })}
          placeholder="Nombre de la opción (Talla, Color...)"
          aria-label="Nombre de la opción"
          maxLength={50}
          className="flex-1 rounded-md border border-[#c9c9c9] px-2 py-1.5 text-[13px] font-medium"
        />
        <button type="button" onClick={onQuitar} aria-label="Quitar opción" className="rounded-md p-1 text-[#616161] hover:bg-[#f1f1f1]">
          <IconoCerrar tamano={16} />
        </button>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        {opcion.valores.map((v) => (
          <span key={v} className="inline-flex items-center gap-1 rounded-full bg-[#ebebeb] px-2.5 py-0.5 text-[12px]">
            {v}
            <button
              type="button"
              onClick={() => onCambiar({ valores: opcion.valores.filter((x) => x !== v) })}
              aria-label={`Quitar ${v}`}
              className="text-[#616161] hover:text-[#101010]"
            >
              <IconoCerrar tamano={11} />
            </button>
          </span>
        ))}
        <input
          value={nuevo}
          onChange={(e) => escribir(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              agregarValor();
            }
          }}
          onBlur={agregarValor}
          placeholder={opcion.valores.length ? 'Otro valor' : 'Escribe un valor y presiona Enter'}
          aria-label="Nuevo valor"
          className="min-w-40 flex-1 rounded-md px-1 py-1 text-[13px] outline-none"
        />
      </div>
      {error && <p className="mt-1 text-[12px] text-[#8e1f0b]">{error}</p>}
    </div>
  );
}
