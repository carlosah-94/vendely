import { Link } from 'react-router-dom';

/** Se muestra cuando la cuenta no tiene el rol que pide la página. */
export default function AccesoDenegado() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-100 px-4">
      <div className="max-w-md text-center space-y-3">
        <p className="text-5xl font-bold text-violet-600">403</p>
        <h1 className="text-2xl font-bold text-slate-800">Acceso denegado</h1>
        <p className="text-slate-600">Tu cuenta no tiene permiso para ver esta página.</p>
        <Link to="/" className="inline-block rounded-md bg-slate-800 px-4 py-2 text-white hover:bg-slate-700">
          Volver al inicio
        </Link>
      </div>
    </main>
  );
}