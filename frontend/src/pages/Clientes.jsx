import { useCallback, useEffect, useState } from 'react';
import FormularioCliente from '../components/FormularioCliente';
import SelectorTienda from '../components/SelectorTienda';
import { mensajeDeError } from '../services/api';
import { eliminarCliente, listarClientes } from '../services/clientes';

/**
 * Pantalla de clientes del panel del dueño (tarea #17).
 * Tabla con Editar y Eliminar, botón para crear, y mensajes de éxito y de error.
 * Los datos vienen del CRUD /api/panel/tiendas/{idTienda}/clientes (tarea #9).
 */
export default function Clientes() {
  const [tienda, setTienda] = useState(null);
  const [clientes, setClientes] = useState([]);
  const [cargando, setCargando] = useState(false);
  const [mensaje, setMensaje] = useState(null); // { tipo: 'exito' | 'error', texto }
  // null = formulario cerrado · 'nuevo' = crear · objeto = editar ese cliente
  const [formulario, setFormulario] = useState(null);
  // Cliente que se pidió eliminar y espera confirmación (null = ninguno)
  const [porEliminar, setPorEliminar] = useState(null);

  const cargar = useCallback(async (t) => {
    if (!t) {
      setClientes([]);
      return;
    }
    setCargando(true);
    try {
      setClientes(await listarClientes(t.idTienda));
    } catch (e) {
      setMensaje({ tipo: 'error', texto: mensajeDeError(e) });
    } finally {
      setCargando(false);
    }
  }, []);

  // Al elegir una tienda (o al cargarse la guardada) se piden sus clientes en ese momento,
  // en vez de con un useEffect que reaccione al cambio (así lo recomienda React).
  function elegirTienda(t) {
    setTienda(t);
    cargar(t);
  }

  // El mensaje de éxito se oculta solo a los 4 segundos; el de error se queda hasta cerrarlo.
  useEffect(() => {
    if (mensaje?.tipo !== 'exito') {
      return undefined;
    }
    const t = setTimeout(() => setMensaje(null), 4000);
    return () => clearTimeout(t);
  }, [mensaje]);

  const cerrarFormulario = useCallback(() => setFormulario(null), []);

  function alGuardar(guardado, esNuevo) {
    setFormulario(null);
    setMensaje({
      tipo: 'exito',
      texto: esNuevo
        ? `Cliente guardado: ${guardado.nombres} ${guardado.primerApellido}`
        : `Cambios guardados: ${guardado.nombres} ${guardado.primerApellido}`,
    });
    cargar(tienda);
  }

  // Eliminar no se puede deshacer: primero se pide confirmación en una ventana propia
  // (no window.confirm, que no sigue el diseño de la app).
  async function eliminar(cliente) {
    setPorEliminar(null);
    const nombre = `${cliente.nombres} ${cliente.primerApellido}`;
    try {
      await eliminarCliente(tienda.idTienda, cliente.idLogin);
      setMensaje({ tipo: 'exito', texto: `Cliente eliminado: ${nombre}` });
      cargar(tienda);
    } catch (e) {
      // Por ejemplo, 409 "El cliente tiene ventas asociadas"
      setMensaje({ tipo: 'error', texto: mensajeDeError(e) });
    }
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold text-slate-800">Clientes</h1>
          <SelectorTienda onCambio={elegirTienda} />
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

      {mensaje && (
        <div
          role={mensaje.tipo === 'error' ? 'alert' : 'status'}
          className={`flex items-start justify-between gap-3 rounded-md border px-4 py-3 text-sm ${
            mensaje.tipo === 'exito'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
              : 'border-red-200 bg-red-50 text-red-700'
          }`}
        >
          <span>{mensaje.texto}</span>
          <button onClick={() => setMensaje(null)} className="text-lg leading-none opacity-60 hover:opacity-100" aria-label="Cerrar mensaje">
            ×
          </button>
        </div>
      )}

      {tienda && (
        <div className="overflow-x-auto rounded-xl bg-white shadow">
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
              {!cargando && clientes.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-slate-500">Esta tienda todavía no tiene clientes.</td></tr>
              )}
              {!cargando && clientes.map((c) => (
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
