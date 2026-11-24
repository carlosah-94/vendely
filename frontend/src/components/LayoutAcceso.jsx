import { Link } from 'react-router-dom';
import MarcaVendely from './marca/MarcaVendely';
import MuroTiendas from './marca/MuroTiendas';

/**
 * Marco de las pantallas de acceso (iniciar sesión y crear cuenta).
 * En la computadora: el formulario a la izquierda y, a la derecha, el mismo muro de tiendas
 * en movimiento de la portada. En el celular solo va el formulario.
 */
export default function LayoutAcceso({ children, ancho = 'max-w-md' }) {
  return (
    <div className="grid min-h-screen bg-white lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <div className="flex flex-col px-5 py-8 sm:px-10">
        <MarcaVendely className="text-lg" />
        <div className={`mx-auto flex w-full ${ancho} flex-1 flex-col justify-center py-10`}>{children}</div>
        <p className="text-xs text-[#6b6890]">
          <Link to="/" className="hover:text-[var(--v-tinta)] hover:underline">Volver a la página principal</Link>
        </p>
      </div>

      <aside className="relative isolate hidden overflow-hidden bg-[var(--v-tinta)] lg:block">
        <MuroTiendas columnas={4} className="-z-20 opacity-80" />
        <div className="absolute inset-0 -z-10 bg-gradient-to-t from-[var(--v-tinta)] via-[var(--v-tinta)]/60 to-[var(--v-tinta)]/10" />
        <div className="absolute inset-x-0 bottom-0 p-12 text-white">
          <p className="letra-titular max-w-md text-[2.6rem] leading-[1]">Así se ven las tiendas hechas con Vendely.</p>
          <p className="mt-4 max-w-sm text-white/75">Tu tienda queda en línea apenas activas tu primer producto.</p>
        </div>
      </aside>
    </div>
  );
}
