import { useEffect, useRef, useState } from 'react';
import { MENSAJE_VISTA_PREVIA } from './tienda/LayoutTienda';

/** Tamaño con el que se dibuja la tienda dentro del iframe, antes de achicarla para que entre. */
const DISPOSITIVOS = {
  escritorio: { nombre: 'Escritorio', ancho: 1280, alto: 860 },
  movil: { nombre: 'Celular', ancho: 390, alto: 780 },
};

/**
 * Vista previa de Personalizar tienda: la tienda pública DE VERDAD (con sus productos), dentro de un
 * iframe, con lo que el dueño todavía no guardó. El borrador viaja por postMessage y LayoutTienda lo
 * aplica solo dentro del iframe (ver useBorradorVistaPrevia). Así la vista previa no se desfasa
 * nunca de las plantillas: es el mismo código.
 *
 * En "Celular" el iframe mide 390 px de ancho, así que la tienda usa su diseño de celular de verdad.
 */
export default function VistaPreviaTienda({ subdominio, borrador }) {
  const [dispositivo, setDispositivo] = useState('escritorio');
  const [anchoCaja, setAnchoCaja] = useState(0);
  const caja = useRef(null);
  const marco = useRef(null);
  const ultimo = useRef(borrador);

  useEffect(() => {
    ultimo.current = borrador;
  }, [borrador]);

  // Ancho disponible: la tienda se achica (scale) para entrar entera a lo ancho.
  useEffect(() => {
    const el = caja.current;
    if (!el) return undefined;
    const observador = new ResizeObserver(([e]) => setAnchoCaja(e.contentRect.width));
    observador.observe(el);
    return () => observador.disconnect();
  }, []);

  function enviar(datos) {
    marco.current?.contentWindow?.postMessage({ tipo: MENSAJE_VISTA_PREVIA, tienda: datos }, window.location.origin);
  }

  // Cuando la tienda del iframe termina de cargar, pide el borrador (también tras navegar dentro).
  useEffect(() => {
    function recibir(e) {
      if (e.origin !== window.location.origin || e.source !== marco.current?.contentWindow) return;
      if (e.data?.tipo === `${MENSAJE_VISTA_PREVIA}:lista`) enviar(ultimo.current);
    }
    window.addEventListener('message', recibir);
    return () => window.removeEventListener('message', recibir);
  }, []);

  // Cada cambio del formulario se ve al instante.
  useEffect(() => {
    enviar(borrador);
  }, [borrador]);

  const d = DISPOSITIVOS[dispositivo];
  const escala = anchoCaja ? Math.min(1, anchoCaja / d.ancho) : 0;

  return (
    <div>
      <div className="flex items-center justify-between gap-3 border-b border-[#ebebeb] px-4 py-2">
        <p className="text-[12px] font-medium text-[#616161]">Vista previa · aún sin guardar</p>
        <div className="flex rounded-lg bg-[#f1f1f1] p-0.5 text-[12px]" role="group" aria-label="Tamaño de la vista previa">
          {Object.entries(DISPOSITIVOS).map(([clave, x]) => (
            <button
              key={clave}
              type="button"
              onClick={() => setDispositivo(clave)}
              aria-pressed={dispositivo === clave}
              className={`rounded-md px-2.5 py-1 font-medium ${dispositivo === clave ? 'bg-white text-[#101010] shadow-sm' : 'text-[#616161]'}`}
            >
              {x.nombre}
            </button>
          ))}
        </div>
      </div>
      <div ref={caja} className="bg-[#f6f6f7] p-3">
        <div
          className="mx-auto overflow-hidden rounded-lg bg-white shadow-[0_0_0_1px_rgba(0,0,0,0.06),0_12px_30px_-12px_rgba(0,0,0,0.25)]"
          style={{ width: d.ancho * escala, height: d.alto * escala }}
        >
          <iframe
            ref={marco}
            title="Vista previa de la tienda"
            src={`/tienda/${subdominio}`}
            style={{ width: d.ancho, height: d.alto, transform: `scale(${escala})`, transformOrigin: '0 0' }}
            className="block border-0"
          />
        </div>
      </div>
    </div>
  );
}
