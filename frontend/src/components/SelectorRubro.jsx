import { RUBROS } from '../utils/rubros';

/**
 * "¿Qué vas a vender?": una opción por rubro. Debajo se ve qué categorías trae el kit, para que el
 * dueño sepa qué va a encontrar al entrar a su tienda.
 */
export default function SelectorRubro({ valor, onCambiar }) {
  const elegido = RUBROS.find((r) => r.id === valor);
  return (
    <fieldset>
      <legend className="mb-1.5 text-[13px] font-medium text-[#303030]">¿Qué vas a vender?</legend>
      <div role="radiogroup" className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {RUBROS.map((r) => {
          const activo = r.id === valor;
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
              {r.nombre}
            </button>
          );
        })}
      </div>
      <p className="mt-1.5 text-[12px] text-[#616161]">
        {elegido
          ? elegido.id === 'otro' ? elegido.kit : `Tu tienda empieza con: ${elegido.kit}. Puedes cambiarlas cuando quieras.`
          : 'Te armamos la tienda con categorías y textos pensados para tu negocio.'}
      </p>
    </fieldset>
  );
}
