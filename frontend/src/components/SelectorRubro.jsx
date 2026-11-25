import { RUBROS } from '../utils/rubros';
import { temaDeRubro } from '../utils/temas';

/**
 * "¿Qué vas a vender?": una opción por rubro, con los colores de su plantilla (V22). Debajo se ve
 * qué plantilla y qué categorías trae, para que el dueño sepa qué va a encontrar al entrar.
 */
export default function SelectorRubro({ valor, onCambiar }) {
  const elegido = RUBROS.find((r) => r.id === valor);
  return (
    <fieldset>
      <legend className="mb-1.5 text-[13px] font-medium text-[#303030]">¿Qué vas a vender?</legend>
      <div role="radiogroup" className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {RUBROS.map((r) => {
          const activo = r.id === valor;
          const tema = temaDeRubro(r.id);
          return (
            <button
              key={r.id}
              type="button"
              role="radio"
              aria-checked={activo}
              onClick={() => onCambiar(r.id)}
              className={`rounded-lg border px-2 py-2 text-[12.5px] font-medium leading-tight transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#303030] ${
                activo ? 'border-[#303030] bg-[#303030] text-white' : 'border-[#d4d4d4] bg-white text-[#303030] hover:border-[#8a8a8a]'
              }`}
            >
              <span className="flex items-center justify-center gap-1.5">
                {tema && (
                  <span aria-hidden="true" className="flex shrink-0">
                    <span className="h-3 w-3 rounded-full ring-1 ring-white" style={{ background: tema.colorPrincipal }} />
                    <span className="-ml-1 h-3 w-3 rounded-full ring-1 ring-white" style={{ background: tema.estilo.colorAcento }} />
                  </span>
                )}
                {r.nombre}
              </span>
            </button>
          );
        })}
      </div>
      <p className="mt-1.5 text-[12px] text-[#616161]">
        {elegido
          ? elegido.id === 'otro'
            ? elegido.kit
            : <>Tu tienda nace con la plantilla <b>{temaDeRubro(elegido.id)?.nombre}</b> y las categorías {elegido.kit}. Puedes cambiarlo cuando quieras.</>
          : 'Te armamos la tienda con categorías y textos pensados para tu negocio.'}
      </p>
    </fieldset>
  );
}
