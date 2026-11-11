import { useCallback, useEffect, useState } from 'react';
import FormularioCliente from '../components/FormularioCliente';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { eliminarCliente, listarClientes } from '../services/clientes';

/**
 * Pantalla de clientes del panel del dueño (tarea #17).
 * Tabla con Editar y Eliminar, botón para crear, y mensajes de éxito y de error.
 * Los datos vienen del CRUD /api/panel/tiendas/{idTienda}/clientes (tarea #9).
 *
 * Usa las piezas comunes del panel (las mismas que Ventas): useTiendaActual para saber
 * con qué tienda se trabaja, SelectorTienda para cambiarla y Mensaje para los avisos.
 */
export default function Clientes() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();

  // Se guarda de qué tienda son los clientes: si no coincide con la elegida, todavía se están cargando.
  const [datos, setDatos] = useState({ idTienda: null, clientes: [], error: '' });
  // Al sumar 1 se vuelve a pedir la lista (después de crear, editar o eliminar).
  const [version, setVersion] = useState(0);
  const [aviso, setAviso] = useState('');
  const [errorAccion, setErrorAccion] = useState('');
  // null = formulario cerrado · 'nuevo' = crear · objeto = editar ese cliente
  const [formulario, setFormulario] = useState(null);
  // Cliente que se pidió eliminar y espera confirmación (null = ninguno)
  const [porEliminar, setPorEliminar] = useState(null);

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true; // evita mostrar la respuesta de una tienda que ya no está elegida
    listarClientes(idTienda)
      .then((clientes) => {
        if (vigente) setDatos({ idTienda, clientes, error: '' });
      })
      .catch((e) => {
        if (vigente) setDatos({ idTienda, clientes: [], error: mensajeDeError(e) });
      });
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  const cargando = cargandoTiendas || (idTienda !== null && datos.idTienda !== idTienda);

  // El aviso de éxito se oculta solo a los 4 segundos; los errores se quedan hasta cerrarlos.
  useEffect(() => {
    if (!aviso) {
      return undefined;
    }
    const t = setTimeout(() => setAviso(''), 4000);
    return () => clearTimeout(t);
  }, [aviso]);

  const cerrarFormulario = useCallback(() => setFormulario(null), []);

  function alGuardar(guardado, esNuevo) {
    setFormulario(null);
    setErrorAccion('');
    setAviso(`${esNuevo ? 'Cliente guardado' : 'Cambios guardados'}: ${guardado.nombres} ${guardado.primerApellido}`);
    setVersion((v) => v + 1);
  }

  // Eliminar no se puede deshacer: primero se pide confirmación en una ventana propia
  // (no window.confirm, que no sigue el diseño de la app).
  async function eliminar(cliente) {
    setPorEliminar(null);
    setErrorAccion('');
    const nombre = `${cliente.nombres} ${cliente.primerApellido}`;
    try {
      await eliminarCliente(idTienda, cliente.idLogin);
      setAviso(`Cliente eliminado: ${nombre}`);
      setVersion((v) => v + 1);
    } catch (e) {
      // Por ejemplo, 409 "El cliente tiene ventas asociadas"
      setErrorAccion(mensajeDeError(e));
    }
  }

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (!cargandoTiendas && tiendas.length === 0) {
    return <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para gestionar sus clientes." />;
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-xl font-bold text-[#101010]">Clientes</h1>
          {tiendas.length > 0 && (
            <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />
          )}
        </div>
        {tienda && (
          <button
            onClick={() => setFormulario('nuevo')}
            className="rounded-md bg-violet-600 px-4 py-2 font-semibold text-white hover:bg-violet-700"
          >
            + Nuevo cliente
          </button>
        )}
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}
      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      {tienda && (
        <div className="overflow-x-auto rounded-xl bg-white shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-600">
              <tr>
                <th className="px-4 py-3 font-semibold">Nombre</th>
                <th className="px-4 py-3 font-semibold">Correo</th>
                <th className="px-4 py-3 font-semibold">Teléfono</th>
                <th className="px-4 py-3 font-semibold">Usuario</th>
                <th className="px-4 py-3 font-semibold">Estado</th>
                <th className="px-4 py-3 text-right font-semibold">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {cargando && (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-slate-500">Cargando clientes...</td></tr>
              )}
              {!cargando && datos.clientes.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-slate-500">Esta tienda todavía no tiene clientes.</td></tr>
              )}
              {!cargando && datos.clientes.map((c) => (
                <tr key={c.idLogin} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-3 font-medium text-slate-800">
                    {c.nombres} {c.primerApellido} {c.segundoApellido ?? ''}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{c.correo}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">{c.telefono ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-600">{c.usuario}</td>
                  <td className="px-4 py-3">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                      c.estado === 'ACTIVO' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600'
                    }`}>
                      {c.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    <button onClick={() => setFormulario(c)} className="rounded-md px-3 py-1 font-medium text-violet-700 hover:bg-violet-50">
                      Editar
                    </button>
                    <button onClick={() => setPorEliminar(c)} className="ml-1 rounded-md px-3 py-1 font-medium text-red-600 hover:bg-red-50">
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {porEliminar && (
        <div className="fixed inset-0 z-20 flex items-center justify-center bg-slate-900/50 p-4">
          <div role="alertdialog" aria-labelledby="titulo-eliminar" className="w-full max-w-md space-y-4 rounded-xl bg-white p-6 shadow-xl">
            <h2 id="titulo-eliminar" className="text-lg font-bold text-slate-800">¿Eliminar cliente?</h2>
            <p className="text-sm text-slate-600">
              Se eliminará a <span className="font-semibold">{porEliminar.nombres} {porEliminar.primerApellido}</span> y
              su dirección. Esta acción no se puede deshacer.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setPorEliminar(null)} className="rounded-md border border-slate-300 px-4 py-2 text-slate-700 hover:bg-slate-50">
                Cancelar
              </button>
              <button onClick={() => eliminar(porEliminar)} className="rounded-md bg-red-600 px-4 py-2 font-semibold text-white hover:bg-red-700">
                Sí, eliminar
              </button>
            </div>
          </div>
        </div>
      )}

      {formulario && tienda && (
        <FormularioCliente
          tienda={tienda}
          cliente={formulario === 'nuevo' ? null : formulario}
          onGuardado={alGuardar}
          onCerrar={cerrarFormulario}
        />
      )}
    </section>
  );
}
