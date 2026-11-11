import { useState } from 'react';
import Mensaje from '../components/Mensaje';
import { IconoCopiar, IconoEnlaceExterno, IconoTienda } from '../components/Iconos';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';

/** Mis tiendas: por cada una, el enlace público para compartir con los clientes. */
export default function MisTiendas() {
  const { tiendas, cargando, error } = useTiendaActual();
  const { avisar } = useToast();
  const [copiada, setCopiada] = useState(null);

  async function copiar(idTienda, enlace) {
    try {
      await navigator.clipboard.writeText(enlace);
      setCopiada(idTienda);
      avisar('Enlace copiado');
      setTimeout(() => setCopiada(null), 2000);
    } catch {
      avisar('No se pudo copiar: selecciona el enlace y cópialo a mano');
    }
  }

  if (error) {
    return <Mensaje tipo="error" texto={error} />;
  }

  return (
    <section className="max-w-3xl space-y-4">
      <h1 className="text-2xl font-bold">Mis tiendas</h1>

      {cargando ? (
        <p className="text-[#616161]">Cargando tiendas...</p>
      ) : tiendas.length === 0 ? (
        <div className="rounded-xl border border-[#e3e3e3] bg-white p-10 text-center">
          <span className="mx-auto mb-3 block w-fit text-[#b5b5b5]"><IconoTienda tamano={40} /></span>
          <p className="text-lg font-semibold">Todavía no tienes tiendas</p>
          <p className="text-[#616161]">Cuando crees una, aquí verás el enlace para compartirla.</p>
        </div>
      ) : (
        tiendas.map((t) => {
          // El frontend tiene su propia dirección: el enlace se arma con ella.
          const enlace = `${window.location.origin}/tienda/${t.subdominio}`;
          return (
            <article key={t.idTienda} className="space-y-3 rounded-xl border border-[#e3e3e3] bg-white p-5">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-lg font-semibold">{t.nombre}</h2>
                <span
                  className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                    t.estado === 'ACTIVA' ? 'bg-green-100 text-green-800' : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {t.estado === 'ACTIVA' ? 'Activa' : 'Suspendida'}
                </span>
              </div>
              <div>
                <p className="mb-1 text-xs text-[#616161]">Enlace de la tienda</p>
                <p className="break-all rounded-md border border-[#e3e3e3] bg-[#f7f7f7] px-3 py-2 text-sm">{enlace}</p>
              </div>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => copiar(t.idTienda, enlace)}
                  className="inline-flex h-8 items-center gap-2 rounded-lg border border-[#c9c9c9] bg-white px-3 hover:bg-[#f3f3f3]"
                >
                  <IconoCopiar tamano={16} /> {copiada === t.idTienda ? 'Copiado' : 'Copiar enlace'}
                </button>
                <a
                  href={enlace}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex h-8 items-center gap-2 rounded-lg bg-[#303030] px-3 text-white hover:bg-black"
                >
                  <IconoEnlaceExterno tamano={16} /> Ver tienda
                </a>
              </div>
            </article>
          );
        })
      )}
    </section>
  );
}
