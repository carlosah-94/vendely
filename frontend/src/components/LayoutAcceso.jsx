import { Link } from 'react-router-dom';

/**
 * Marco de las pantallas de acceso (iniciar sesión y crear cuenta):
 * el nombre de Vendely arriba y el formulario en una tarjeta centrada.
 */
export default function LayoutAcceso({ children, ancho = 'max-w-md' }) {
  return (
    <div className="min-h-screen bg-slate-100 px-4 py-12">
      <div className={`mx-auto ${ancho}`}>
        <Link to="/" className="mb-6 block text-center text-2xl font-bold text-slate-900">
          Vendely
        </Link>
        <div className="rounded-xl border border-slate-200 bg-white p-8 shadow-sm">{children}</div>
      </div>
    </div>
  );
}
