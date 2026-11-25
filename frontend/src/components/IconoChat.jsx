/** Burbuja de chat con un teléfono: el ícono de "Escríbenos por WhatsApp". */
export default function IconoChat({ size = 22 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3.5 20.5l1.3-4.2A8.5 8.5 0 1 1 8 19.4z" />
      <path d="M9.2 8.6c.2-.4.6-.5.9-.3l1 1c.2.3.2.6 0 .9l-.5.6c.5 1 1.3 1.8 2.3 2.3l.6-.5c.3-.2.6-.2.9 0l1 1c.2.3.1.7-.3.9-.8.6-1.8.7-2.7.2a8 8 0 0 1-3.4-3.4c-.5-.9-.4-1.9.2-2.7z" />
    </svg>
  );
}
