import { useEffect, useRef, useState } from 'react';
import { MENSAJE_VISTA_PREVIA } from './tienda/LayoutTienda';

/**
 * La tienda pública DE VERDAD (con sus productos) dentro de un iframe, achicada para entrar en su
 * caja. La caja la dimensiona quien la usa (className: ancho y alto o aspect-ratio); la tienda se
 * dibuja con `ancho` px de ancho (1280 = computadora, 390 = celular) y se escala para llenarla.
 *
 * borrador: lo que se quiere ver sin guardar (colores, plantilla, diseño...). Viaja por postMessage
 * y LayoutTienda lo aplica solo dentro del iframe (ver useBorradorVistaPrevia). null = la tienda
 * tal como está publicada.
 *
 * interactiva = false: no se puede hacer clic adentro (miniaturas de la galería de temas).
 */
export default function TiendaEnVivo({ subdominio, borrador = null, ancho = 1280, interactiva = true, titulo, className = '' }) {
  const caja = useRef(null);
  const marco = useRef(null);
  const ultimo = useRef(borrador);
  const [medida, setMedida] = useState({ w: 0, h: 0 });

  useEffect(() => {
    ultimo.current = borrador;
  }, [borrador]);

  useEffect(() => {
    const el = caja.current;
    if (!el) return undefined;
    const observador = new ResizeObserver(([e]) => setMedida({ w: e.contentRect.width, h: e.contentRect.height }));
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

  // Cada cambio del borrador se ve al instante.
  useEffect(() => {
    enviar(borrador);
  }, [borrador]);

  const escala = medida.w ? medida.w / ancho : 0;
  return (
    <div ref={caja} className={`relative overflow-hidden bg-white ${className}`}>
      {escala > 0 && (
        <iframe
          ref={marco}
          title={titulo ?? 'Vista previa de la tienda'}
          src={`/tienda/${subdominio}`}
          loading="lazy"
          tabIndex={interactiva ? undefined : -1}
          style={{
            width: ancho,
            height: medida.h / escala,
            transform: `scale(${escala})`,
            transformOrigin: '0 0',
            pointerEvents: interactiva ? 'auto' : 'none',
          }}
          className="absolute left-0 top-0 block border-0"
        />
      )}
    </div>
  );
}
