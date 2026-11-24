import { Link } from 'react-router-dom';

/**
 * Logo de Vendely: una etiqueta de precio color mango con la V y el agujero de la etiqueta.
 * claro = sobre fondo oscuro (texto blanco); si no, texto tinta.
 */
export function SimboloVendely({ tamano = 28, className = '' }) {
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 32 32" aria-hidden="true" className={className}>
      <rect width="32" height="32" rx="9" fill="#FFB31F" />
      <path d="M9 10.5 16 23l7-12.5" fill="none" stroke="#15123B" strokeWidth="3.6" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="24" cy="8" r="2" fill="#15123B" />
    </svg>
  );
}

export default function MarcaVendely({ claro = false, a = '/', tamano = 28, className = '' }) {
  return (
    <Link to={a} className={`inline-flex items-center gap-2 ${className}`} aria-label="Vendely, ir al inicio">
      <SimboloVendely tamano={tamano} />
      <span className={`letra-marca text-[1.35em] leading-none ${claro ? 'text-white' : 'text-[var(--v-tinta)]'}`}>vendely</span>
    </Link>
  );
}
