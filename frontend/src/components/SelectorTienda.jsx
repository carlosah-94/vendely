/** Elige la tienda con la que se trabaja. Si el dueño tiene una sola, solo muestra su nombre. */
export default function SelectorTienda({ tiendas, idTienda, onCambiar }) {
  if (tiendas.length <= 1) {
    return (
      <p className="text-sm text-slate-600">
        Tienda: <span className="font-medium text-slate-800">{tiendas[0]?.nombre}</span>
      </p>
    );
  }
  return (
    <label className="flex items-center gap-2 text-sm text-slate-600">
      Tienda:
      <select
        value={idTienda ?? ''}
        onChange={(e) => onCambiar(Number(e.target.value))}
        className="rounded-md border border-slate-300 bg-white px-2 py-1 text-slate-800"
      >
        {tiendas.map((t) => (
          <option key={t.idTienda} value={t.idTienda}>{t.nombre}</option>
        ))}
      </select>
    </label>
  );
}
