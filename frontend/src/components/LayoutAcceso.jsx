import { Link } from 'react-router-dom';

/**
 * Marco de las pantallas de acceso (iniciar sesión y crear cuenta), como las de Shopify:
 * fondo oscuro, el nombre de Vendely arriba y el formulario en una tarjeta blanca centrada.
 */
export default function LayoutAcceso({ children, ancho = 'max-w-md' }) {
  return (
    <div className="flex min-h-screen flex-col bg-[#0a1314] px-4 py-10">
      <div className={`mx-auto w-full ${ancho} flex-1`}>
        <Link to="/" className="mb-8 block text-center text-2xl font-semibold tracking-tight text-white">
          Vendely
        </Link>
        <div className="rounded-2xl bg-white p-8 shadow-[0_20px_60px_rgba(0,0,0,0.35)]">{children}</div>
      </div>
      <p className="mt-10 text-center text-xs text-[#8a9a9b]">
        <Link to="/" className="hover:text-white">Volver a la página principal</Link>
      </p>
    </div>
  );
}
