import { useEffect, useState } from 'react';
import ConfirmarAccion from '../../components/ConfirmarAccion';
import { IconoBuscar, IconoEnlaceExterno, IconoTienda } from '../../components/Iconos';
import Mensaje from '../../components/Mensaje';
import { useToast } from '../../context/ToastContext';
import { cambiarEstadoTienda, listarTiendasAdmin, obtenerResumen } from '../../services/admin';
import { mensajeDeError } from '../../services/api';
import { formatoFecha, formatoMoneda } from '../../utils/formato';

const PESTANAS = [
  { valor: 'TODAS', texto: 'Todas' },
  { valor: 'ACTIVA', texto: 'Activas' },
  { valor: 'SUSPENDIDA', texto: 'Suspendidas' },
];

const SOMBRA = 'shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]';

/**
 * Panel del superadmin: números de toda la plataforma y la lista de todas las tiendas,
 * con su dueño. Desde aquí se suspende o se reactiva una tienda.
 */
export default function AdminTiendas() {
  const { avisar } = useToast();
  const [datos, setDatos] = useState({ cargando: true, resumen: null, tiendas: [], error: '' });
  const [version, setVersion] = useState(0);   // al sumar 1 se vuelven a pedir los datos
  const [pestana, setPestana] = useState('TODAS');
  const [busqueda, setBusqueda] = useState('');
  const [porCambiar, setPorCambiar] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [errorAccion, setErrorAccion] = useState('');

  useEffect(() => {
    let vigente = true;
    Promise.all([obtenerResumen(), listarTiendasAdmin()])
      .then(([resumen, tiendas]) => vigente && setDatos({ cargando: false, resumen, tiendas, error: '' }))
      .catch((e) => vigente && setDatos({ cargando: false, resumen: null, tiendas: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [version]);

  async function confirmarCambio() {
    const t = porCambiar;
    const activar = t.estado !== 'ACTIVA';
    setEnviando(true);
    setErrorAccion('');
    try {
      await cambiarEstadoTienda(t.idTienda, activar);
      avisar(activar ? `Tienda reactivada: ${t.nombre}` : `Tienda suspendida: ${t.nombre}`);
      setVersion((v) => v + 1);
    } catch (e) {
      setErrorAccion(mensajeDeError(e));
    } finally {
      setEnviando(false);
      setPorCambiar(null);
    }
  }

  const texto = busqueda.trim().toLowerCase();
  const visibles = datos.tiendas.filter(
    (t) =>
      (pestana === 'TODAS' || t.estado === pestana) &&
      (!texto || [t.nombre, t.subdominio, t.dueno, t.correoDueno].some((v) => (v ?? '').toLowerCase().includes(texto))),
  );

  const r = datos.resumen;
  const tarjetas = [
    ['Tiendas activas', r?.tiendasActivas],
    ['Tiendas suspendidas', r?.tiendasSuspendidas],
    ['Dueños', r?.duenos],
    ['Clientes', r?.clientes],
    ['Pedidos', r?.pedidos],
    ['Ventas pagadas', r ? formatoMoneda(r.ventasPagadas) : undefined],
  ];

  return (
    <section className="space-y-4">
      <div>
        <h1 className="text-xl font-bold text-[#101010]">Tiendas de Vendely</h1>
        <p className="text-[13px] text-[#616161]">Todas las tiendas creadas en la plataforma.</p>
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}

      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        {tarjetas.map(([titulo, valor]) => (
          <div key={titulo} className={`rounded-xl bg-white p-4 ${SOMBRA}`}>
            <p className="text-xs text-[#616161]">{titulo}</p>
            <p className="mt-1 text-xl font-semibold text-[#101010]">{datos.cargando ? '…' : (valor ?? '-')}</p>
          </div>
        ))}
      </div>

      <div className={`overflow-hidden rounded-xl bg-white ${SOMBRA}`}>
        <div className="flex flex-wrap items-center gap-2 border-b border-[#ebebeb] px-2 py-2">
          <div className="flex gap-1">
            {PESTANAS.map((p) => (
              <button
                key={p.valor}
                onClick={() => setPestana(p.valor)}
                className={`rounded-lg px-3 py-1 text-[13px] ${
                  pestana === p.valor ? 'bg-[#ebebeb] font-semibold text-[#101010]' : 'text-[#616161] hover:bg-[#f1f1f1]'
                }`}
              >
                {p.texto}
              </button>
            ))}
          </div>
          <label className="ml-auto flex w-full items-center gap-2 rounded-lg border border-[#ddd] px-2.5 py-1 text-[#8a8a8a] focus-within:border-[#1650d0] sm:w-72">
            <IconoBuscar tamano={15} />
            <input
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Buscar tienda, subdominio o dueño"
              aria-label="Buscar tiendas"
              className="w-full bg-transparent text-[13px] text-[#303030] outline-none"
            />
          </label>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-[13px]">
            <thead className="bg-[#f7f7f7] text-left text-[#616161]">
              <tr>
                <th className="px-4 py-2.5 font-medium">Tienda</th>
                <th className="px-4 py-2.5 font-medium">Dueño</th>
                <th className="px-4 py-2.5 text-right font-medium">Productos</th>
                <th className="px-4 py-2.5 text-right font-medium">Pedidos</th>
                <th className="px-4 py-2.5 text-right font-medium">Ventas pagadas</th>
                <th className="px-4 py-2.5 font-medium">Creada</th>
                <th className="px-4 py-2.5 font-medium">Estado</th>
                <th className="px-4 py-2.5 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#ebebeb]">
              {datos.cargando && (
                <tr><td colSpan={8} className="px-4 py-8 text-center text-[#616161]">Cargando tiendas...</td></tr>
              )}
              {!datos.cargando && visibles.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-[#616161]">
                    <span className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1]">
                      <IconoTienda tamano={24} />
                    </span>
                    {datos.tiendas.length === 0 ? 'Todavía no hay tiendas en Vendely.' : 'Ninguna tienda coincide con el filtro.'}
                  </td>
                </tr>
              )}
              {!datos.cargando && visibles.map((t) => (
                <tr key={t.idTienda} className="hover:bg-[#f7f7f7]">
                  <td className="px-4 py-3">
                    <p className="font-medium text-[#101010]">{t.nombre}</p>
                    <p className="text-xs text-[#616161]">{t.subdominio}.vendely.pe · {t.pais}</p>
                  </td>
                  <td className="px-4 py-3">
                    <p className="text-[#303030]">{t.dueno}</p>
                    <p className="text-xs text-[#616161]">{t.correoDueno}</p>
                  </td>
                  <td className="px-4 py-3 text-right">{t.productos}</td>
                  <td className="px-4 py-3 text-right">{t.pedidos}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">{formatoMoneda(t.ventasPagadas)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-[#616161]">{formatoFecha(t.fechaCreacion)}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                        t.estado === 'ACTIVA' ? 'bg-[#e8fcd9] text-[#0c5132]' : 'bg-[#ffe7e6] text-[#8e1f0b]'
                      }`}
                    >
                      {t.estado === 'ACTIVA' ? 'Activa' : 'Suspendida'}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    {t.estado === 'ACTIVA' && (
                      <a
                        href={`${window.location.origin}/tienda/${t.subdominio}`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]"
                      >
                        Ver <IconoEnlaceExterno tamano={13} />
                      </a>
                    )}
                    <button
                      onClick={() => setPorCambiar(t)}
                      className={`ml-1 rounded-lg px-2 py-1 font-medium ${
                        t.estado === 'ACTIVA' ? 'text-[#c70a24] hover:bg-[#ffe7e6]' : 'hover:bg-[#f1f1f1]'
                      }`}
                    >
                      {t.estado === 'ACTIVA' ? 'Suspender' : 'Reactivar'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!datos.cargando && (
          <p className="border-t border-[#ebebeb] px-4 py-2.5 text-[12px] text-[#616161]">
            Mostrando {visibles.length} de {datos.tiendas.length} tiendas
          </p>
        )}
      </div>

      {porCambiar && (
        <ConfirmarAccion
          titulo={porCambiar.estado === 'ACTIVA' ? `¿Suspender ${porCambiar.nombre}?` : `¿Reactivar ${porCambiar.nombre}?`}
          texto={
            porCambiar.estado === 'ACTIVA'
              ? 'La tienda dejará de verse en internet y no recibirá pedidos. No se borra nada: puedes reactivarla cuando quieras.'
              : 'La tienda volverá a verse en internet y podrá recibir pedidos.'
          }
          boton={porCambiar.estado === 'ACTIVA' ? 'Suspender tienda' : 'Reactivar tienda'}
          peligro={porCambiar.estado === 'ACTIVA'}
          enviando={enviando}
          onConfirmar={confirmarCambio}
          onCancelar={() => setPorCambiar(null)}
        />
      )}
    </section>
  );
}
