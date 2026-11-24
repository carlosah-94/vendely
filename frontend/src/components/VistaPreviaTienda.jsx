import { useState } from 'react';
import TiendaEnVivo from './TiendaEnVivo';

/** Tamaño con el que se dibuja la tienda, antes de achicarla para que entre. */
// eslint-disable-next-line react-refresh/only-export-components
export const DISPOSITIVOS = {
  escritorio: { nombre: 'Escritorio', ancho: 1280, caja: 'w-full aspect-[1280/860]' },
  movil: { nombre: 'Celular', ancho: 390, caja: 'mx-auto w-full max-w-[390px] aspect-[390/780]' },
};

/** Botones "Escritorio / Celular". */
export function SelectorDispositivo({ valor, onCambiar }) {
  return (
    <div className="flex rounded-lg bg-[#f1f1f1] p-0.5 text-[12px]" role="group" aria-label="Tamaño de la vista previa">
      {Object.entries(DISPOSITIVOS).map(([clave, x]) => (
        <button
          key={clave}
          type="button"
          onClick={() => onCambiar(clave)}
          aria-pressed={valor === clave}
          className={`rounded-md px-2.5 py-1 font-medium ${valor === clave ? 'bg-white text-[#101010] shadow-sm' : 'text-[#616161]'}`}
        >
          {x.nombre}
        </button>
      ))}
    </div>
  );
}

/**
 * Vista previa de Personalizar tienda: la tienda real con lo que el dueño todavía no guardó
 * (ver TiendaEnVivo). En "Celular" la tienda mide 390 px de ancho, así que usa su diseño de
 * celular de verdad.
 */
export default function VistaPreviaTienda({ subdominio, borrador }) {
  const [dispositivo, setDispositivo] = useState('escritorio');
  const d = DISPOSITIVOS[dispositivo];

  return (
    <div>
      <div className="flex items-center justify-between gap-3 border-b border-[#ebebeb] px-4 py-2">
        <p className="text-[12px] font-medium text-[#616161]">Vista previa · aún sin guardar</p>
        <SelectorDispositivo valor={dispositivo} onCambiar={setDispositivo} />
      </div>
      <div className="bg-[#f6f6f7] p-3">
        <TiendaEnVivo
          key={dispositivo}
          subdominio={subdominio}
          borrador={borrador}
          ancho={d.ancho}
          className={`${d.caja} rounded-lg shadow-[0_0_0_1px_rgba(0,0,0,0.06),0_12px_30px_-12px_rgba(0,0,0,0.25)]`}
        />
      </div>
    </div>
  );
}
