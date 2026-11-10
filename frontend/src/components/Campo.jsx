/** Campo de formulario con su etiqueta, una ayuda opcional y el error debajo. */
export default function Campo({ etiqueta, error, ayuda, children }) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-slate-700">{etiqueta}</span>
      <span className="mt-1 block">{children}</span>
      {error ? (
        <span className="mt-1 block text-sm text-red-600">{error}</span>
      ) : (
        ayuda && <span className="mt-1 block text-xs text-slate-500">{ayuda}</span>
      )}
    </label>
  );
}
