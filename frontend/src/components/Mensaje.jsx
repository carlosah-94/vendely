const ESTILO_MENSAJE = {
  exito: 'border-green-200 bg-green-50 text-green-800',
  error: 'border-red-200 bg-red-50 text-red-800',
  info: 'border-slate-200 bg-white text-slate-700',
};

/** Aviso de color: verde si salió bien, rojo si hubo un error. */
export default function Mensaje({ tipo, texto, onCerrar }) {
  return (
    <div className={`flex items-start justify-between gap-3 rounded-md border px-4 py-3 text-sm ${ESTILO_MENSAJE[tipo]}`}>
      <p>{texto}</p>
      {onCerrar && (
        <button onClick={onCerrar} className="font-medium opacity-70 hover:opacity-100" aria-label="Cerrar aviso">
          x
        </button>
      )}
    </div>
  );
}
