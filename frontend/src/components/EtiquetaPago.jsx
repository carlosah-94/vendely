const ESTILOS = {
  APROBADO: ['Pagado', 'bg-green-100 text-green-800'],
  PENDIENTE: ['Pendiente', 'bg-amber-100 text-amber-800'],
  RECHAZADO: ['Rechazado', 'bg-red-100 text-red-800'],
  REEMBOLSADO: ['Reembolsado', 'bg-slate-200 text-slate-700'],
  SIN_PAGO: ['Sin pago', 'bg-slate-200 text-slate-700'],
};

/** Pastilla con el estado del pago de un pedido (verde "Pagado", amarillo "Pendiente"...). */
export default function EtiquetaPago({ estado }) {
  const [texto, clases] = ESTILOS[estado] ?? ESTILOS.SIN_PAGO;
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${clases}`}>{texto}</span>
  );
}
