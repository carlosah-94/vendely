import { useEffect, useState } from 'react';
import ConfirmarAccion from '../../components/ConfirmarAccion';
import { IconoBuscar, IconoClientes } from '../../components/Iconos';
import Mensaje from '../../components/Mensaje';
import { useToast } from '../../context/ToastContext';
import { cambiarEstadoCuenta, listarCuentasAdmin } from '../../services/admin';
import { mensajeDeError } from '../../services/api';
import { formatoFecha } from '../../utils/formato';

const PESTANAS = [
  { valor: 'TODAS', texto: 'Todas' },
  { valor: 'ADMIN_TIENDA', texto: 'Dueños' },
  { valor: 'CLIENTE', texto: 'Clientes' },
  { valor: 'SUPERADMIN', texto: 'Administradores' },
];

const ROL = {
  SUPERADMIN: { texto: 'Administrador', estilo: 'bg-[#303030] text-white' },
  ADMIN_TIENDA: { texto: 'Dueño', estilo: 'bg-[#eaf4ff] text-[#00527c]' },
  CLIENTE: { texto: 'Cliente', estilo: 'bg-[#e3e3e3] text-[#4a4a4a]' },
};

const SOMBRA = 'shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]';

/**
 * Todas las cuentas de Vendely (dueños, clientes y administradores).
 * El superadmin puede desactivar una cuenta: ya no puede iniciar sesión y su sesión abierta
 * se corta en la siguiente acción. Las cuentas de administrador no se pueden desactivar.
 */
export default function AdminCuentas() {
  const { avisar } = useToast();
  const [datos, setDatos] = useState({ cargando: true, cuentas: [], error: '' });
  const [version, setVersion] = useState(0);
  const [pestana, setPestana] = useState('TODAS');
  const [busqueda, setBusqueda] = useState('');
  const [porCambiar, setPorCambiar] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [errorAccion, setErrorAccion] = useState('');

  useEffect(() => {
    let vigente = true;
    listarCuentasAdmin()
      .then((cuentas) => vigente && setDatos({ cargando: false, cuentas, error: '' }))
      .catch((e) => vigente && setDatos({ cargando: false, cuentas: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [version]);

  async function confirmarCambio() {
    const c = porCambiar;
    const activar = c.estado !== 'ACTIVO';
    setEnviando(true);
    setErrorAccion('');
    try {
      await cambiarEstadoCuenta(c.idLogin, activar);
      avisar(activar ? `Cuenta activada: ${c.nombre}` : `Cuenta desactivada: ${c.nombre}`);
      setVersion((v) => v + 1);
    } catch (e) {
      setErrorAccion(mensajeDeError(e));
    } finally {
      setEnviando(false);
      setPorCambiar(null);
    }
  }

  const texto = busqueda.trim().toLowerCase();
  const visibles = datos.cuentas.filter(
    (c) =>
      (pestana === 'TODAS' || c.rol === pestana) &&
      (!texto || [c.nombre, c.correo, c.tienda].some((v) => (v ?? '').toLowerCase().includes(texto))),
  );
  const cuantas = (rol) => datos.cuentas.filter((c) => rol === 'TODAS' || c.rol === rol).length;

  return (
    <section className="space-y-4">
      <div>
        <h1 className="text-xl font-bold text-[#101010]">Cuentas</h1>
        <p className="text-[13px] text-[#616161]">Dueños de tienda, clientes y administradores de Vendely.</p>
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}

      <div className={`overflow-hidden rounded-xl bg-white ${SOMBRA}`}>
        <div className="flex flex-wrap items-center gap-2 border-b border-[#ebebeb] px-2 py-2">
          <div className="flex flex-wrap gap-1">
            {PESTANAS.map((p) => (
              <button
                key={p.valor}
                onClick={() => setPestana(p.valor)}
                className={`rounded-lg px-3 py-1 text-[13px] ${
                  pestana === p.valor ? 'bg-[#ebebeb] font-semibold text-[#101010]' : 'text-[#616161] hover:bg-[#f1f1f1]'
                }`}
              >
                {p.texto} {!datos.cargando && <span className="text-[#8a8a8a]">{cuantas(p.valor)}</span>}
              </button>
            ))}
          </div>
          <label className="ml-auto flex w-full items-center gap-2 rounded-lg border border-[#ddd] px-2.5 py-1 text-[#8a8a8a] focus-within:border-[#1650d0] sm:w-72">
            <IconoBuscar tamano={15} />
            <input
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Buscar por nombre, correo o tienda"
              aria-label="Buscar cuentas"
              className="w-full bg-transparent text-[13px] text-[#303030] outline-none"
            />
          </label>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-[13px]">
            <thead className="bg-[#f7f7f7] text-left text-[#616161]">
              <tr>
                <th className="px-4 py-2.5 font-medium">Nombre</th>
                <th className="px-4 py-2.5 font-medium">Rol</th>
                <th className="px-4 py-2.5 font-medium">Tienda</th>
                <th className="px-4 py-2.5 font-medium">Registrada</th>
                <th className="px-4 py-2.5 font-medium">Estado</th>
                <th className="px-4 py-2.5 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#ebebeb]">
              {datos.cargando && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-[#616161]">Cargando cuentas...</td></tr>
              )}
              {!datos.cargando && visibles.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-[#616161]">
                    <span className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1]">
                      <IconoClientes tamano={24} />
                    </span>
                    Ninguna cuenta coincide con el filtro.
                  </td>
                </tr>
              )}
              {!datos.cargando && visibles.map((c) => (
                <tr key={c.idLogin} className="hover:bg-[#f7f7f7]">
                  <td className="px-4 py-3">
                    <p className="font-medium text-[#101010]">{c.nombre}</p>
                    <p className="text-xs text-[#616161]">{c.correo}</p>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${ROL[c.rol]?.estilo ?? ''}`}>
                      {ROL[c.rol]?.texto ?? c.rol}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-[#303030]">
                    {c.rol === 'CLIENTE' && c.tienda}
                    {c.rol === 'ADMIN_TIENDA' && `${c.tiendasPropias} ${c.tiendasPropias === 1 ? 'tienda' : 'tiendas'}`}
                    {c.rol === 'SUPERADMIN' && <span className="text-[#8a8a8a]">Toda la plataforma</span>}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-[#616161]">{formatoFecha(c.fechaCreacion)}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                        c.estado === 'ACTIVO' ? 'bg-[#e8fcd9] text-[#0c5132]' : 'bg-[#ffe7e6] text-[#8e1f0b]'
                      }`}
                    >
                      {c.estado === 'ACTIVO' ? 'Activa' : 'Desactivada'}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    {c.rol !== 'SUPERADMIN' && (
                      <button
                        onClick={() => setPorCambiar(c)}
                        className={`rounded-lg px-2 py-1 font-medium ${
                          c.estado === 'ACTIVO' ? 'text-[#c70a24] hover:bg-[#ffe7e6]' : 'hover:bg-[#f1f1f1]'
                        }`}
                      >
                        {c.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!datos.cargando && (
          <p className="border-t border-[#ebebeb] px-4 py-2.5 text-[12px] text-[#616161]">
            Mostrando {visibles.length} de {datos.cuentas.length} cuentas
          </p>
        )}
      </div>

      {porCambiar && (
        <ConfirmarAccion
          titulo={porCambiar.estado === 'ACTIVO' ? `¿Desactivar la cuenta de ${porCambiar.nombre}?` : `¿Activar la cuenta de ${porCambiar.nombre}?`}
          texto={
            porCambiar.estado === 'ACTIVO'
              ? 'No podrá iniciar sesión y, si tiene la sesión abierta, se le cerrará en su siguiente acción. Sus datos, tiendas y pedidos no se borran.'
              : 'Podrá volver a iniciar sesión con su correo y contraseña.'
          }
          boton={porCambiar.estado === 'ACTIVO' ? 'Desactivar cuenta' : 'Activar cuenta'}
          peligro={porCambiar.estado === 'ACTIVO'}
          enviando={enviando}
          onConfirmar={confirmarCambio}
          onCancelar={() => setPorCambiar(null)}
        />
      )}
    </section>
  );
}
