/** Campo de los formularios del panel: etiqueta, el input y debajo el error (o una ayuda). */
export default function CampoPanel({ etiqueta, error, ayuda, children }) {
  return (
    <label className="block">
      <span className="text-[13px] text-[#303030]">{etiqueta}</span>
      <div className="mt-1">{children}</div>
      {error ? (
        <span className="mt-1 block text-[12px] text-[#8e1f0b]">{error}</span>
      ) : (
        ayuda && <span className="mt-1 block text-[12px] text-[#616161]">{ayuda}</span>
      )}
    </label>
  );
}
