/** Íconos simples de línea (SVG). Se pintan con el color del texto (currentColor). */
function Icono({ children, tamano = 20, className = '' }) {
  return (
    <svg
      width={tamano}
      height={tamano}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export const IconoCarrito = (p) => (
  <Icono {...p}><path d="M3 4h2l2.4 11h10.2L20 7H6.2" /><circle cx="9" cy="19.5" r="1.2" /><circle cx="17" cy="19.5" r="1.2" /></Icono>
);
export const IconoBuscar = (p) => (
  <Icono {...p}><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.2-4.2" /></Icono>
);
export const IconoCuenta = (p) => (
  <Icono {...p}><circle cx="12" cy="8" r="4" /><path d="M4 20c1.2-3.6 4.2-5.5 8-5.5s6.8 1.9 8 5.5" /></Icono>
);
export const IconoImagen = (p) => (
  <Icono {...p}><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="1.6" /><path d="m21 16-5-5-8 8" /></Icono>
);
export const IconoCerrar = (p) => (
  <Icono {...p}><path d="M6 6l12 12M18 6 6 18" /></Icono>
);
export const IconoCheck = (p) => (
  <Icono {...p}><path d="m5 12.5 4.5 4.5L19 7.5" /></Icono>
);
export const IconoEnlaceExterno = (p) => (
  <Icono {...p}><path d="M14 4h6v6M20 4l-9 9" /><path d="M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" /></Icono>
);
export const IconoInicio = (p) => (
  <Icono {...p}><path d="M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z" /></Icono>
);
export const IconoPedidos = (p) => (
  <Icono {...p}><path d="M4 5h16v14H4z" /><path d="M4 10h16M9 14h6" /></Icono>
);
export const IconoProductos = (p) => (
  <Icono {...p}><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9z" /><path d="m4 7.5 8 4.5 8-4.5M12 12v9" /></Icono>
);
export const IconoClientes = (p) => (
  <Icono {...p}><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20c.8-3.2 3.3-5 6.5-5s5.7 1.8 6.5 5" /><path d="M16 5a3.5 3.5 0 0 1 0 6.5M18 15.2c1.7.7 2.8 2.2 3.5 4.8" /></Icono>
);
export const IconoTienda = (p) => (
  <Icono {...p}><path d="M4 9 5.5 4h13L20 9" /><path d="M4 9v10h16V9" /><path d="M4 9a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0A2.7 2.7 0 0 0 20 9" /></Icono>
);
export const IconoCopiar = (p) => (
  <Icono {...p}><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M5 15V6a2 2 0 0 1 2-2h9" /></Icono>
);
