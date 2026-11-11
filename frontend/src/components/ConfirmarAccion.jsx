/**
 * Ventana para confirmar una acción importante (suspender una tienda, desactivar una cuenta).
 * peligro = true pinta el botón en rojo.
 */
export default function ConfirmarAccion({ titulo, texto, boton, peligro = false, enviando = false, onConfirmar, onCancelar }) {
  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-black/50 p-4">
      <div role="alertdialog" aria-labelledby="titulo-confirmar" className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="space-y-2 p-5">
          <h2 id="titulo-confirmar" className="text-base font-semibold text-[#101010]">{titulo}</h2>
          <p className="text-[13px] text-[#4a4a4a]">{texto}</p>
        </div>
        <div className="flex justify-end gap-2 border-t border-[#ebebeb] bg-[#f7f7f7] px-5 py-3">
          <button onClick={onCancelar} className="rounded-lg border border-[#ddd] bg-white px-3 py-1.5 font-medium hover:bg-[#f1f1f1]">
            Cancelar
          </button>
          <button
            onClick={onConfirmar}
            disabled={enviando}
            className={`rounded-lg px-3 py-1.5 font-semibold text-white disabled:opacity-60 ${
              peligro ? 'bg-[#c70a24] hover:bg-[#a00a1f]' : 'bg-[#303030] hover:bg-[#1a1a1a]'
            }`}
          >
            {enviando ? 'Guardando...' : boton}
          </button>
        </div>
      </div>
    </div>
  );
}
