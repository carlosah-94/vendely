import IconoChat from '../IconoChat';
import { enlaceWhatsapp } from '../../utils/whatsapp';

/**
 * Botón flotante de la vitrina (abajo a la derecha): abre un chat con la tienda. En Perú mucha
 * gente pregunta antes de comprar; así no se va de la tienda a buscar el número.
 * Solo aparece si el dueño puso su WhatsApp en Personalizar.
 */
export default function BotonWhatsapp({ tienda }) {
  const enlace = enlaceWhatsapp(tienda.whatsapp, `Hola ${tienda.nombre}, tengo una consulta.`);
  if (!enlace) return null;
  return (
    <a
      href={enlace}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={`Escribir a ${tienda.nombre} por WhatsApp`}
      className="fixed bottom-5 right-5 z-30 flex items-center gap-2 rounded-full bg-[#1f9d55] px-4 py-3 text-sm font-semibold text-white shadow-[0_10px_30px_-8px_rgba(31,157,85,0.7)] transition hover:bg-[#188a49] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#1f9d55]"
    >
      <IconoChat />
      <span className="hidden sm:inline">Escríbenos</span>
    </a>
  );
}
