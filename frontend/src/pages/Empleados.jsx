import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import CampoPanel from '../components/CampoPanel';
import { IconoEmpleados, IconoMas } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { crearEmpleado, editarEmpleado, listarEmpleados, quitarEmpleado } from '../services/empleados';
import { formatoFecha } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta, inputPanel } from '../utils/panel';
import { PERMISOS } from '../utils/permisos';

const SIN_PERMISOS = { puedePedidos: false, puedeProductos: false, puedeInventario: false, puedeClientes: false };
const VACIO = { nombres: '', primerApellido: '', correo: '', contrasena: '', ...SIN_PERMISOS, puedePedidos: true };

/**
 * Empleados de la tienda (solo el dueño), como las cuentas de personal de Shopify.
 * El dueño agrega a alguien con su correo, marca qué partes del panel puede usar y lo puede
 * suspender o quitar. Cuántos puede tener lo decide el plan: Gratis 0, Básico 2, Pro 15.
 */
export default function Empleados() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const [datos, setDatos] = useState({ idTienda: null, respuesta: null, error: '' });
  const [version, setVersion] = useState(0);
  const [aviso, setAviso] = useState('');
  const [errorAccion, setErrorAccion] = useState('');
  const [agregando, setAgregando] = useState(false);
  const [editando, setEditando] = useState(null);       // idLogin del empleado cuyos permisos se editan
  const [porQuitar, setPorQuitar] = useState(null);     // idLogin que espera confirmación

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true;
    listarEmpleados(idTienda)
      .then((respuesta) => vigente && setDatos({ idTienda, respuesta, error: '' }))
      .catch((e) => vigente && setDatos({ idTienda, respuesta: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  // El aviso de éxito se oculta solo; los errores se quedan hasta cerrarlos.
  useEffect(() => {
    if (!aviso) {
      return undefined;
    }
    const t = setTimeout(() => setAviso(''), 4000);
    return () => clearTimeout(t);
  }, [aviso]);

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (!cargandoTiendas && tiendas.length === 0) {
    return <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para agregar empleados." />;
  }

  const cargando = cargandoTiendas || datos.idTienda !== idTienda;
  const r = datos.respuesta;
  const lleno = r ? r.activos >= r.maxEmpleados : true;
  const recargar = () => setVersion((v) => v + 1);

  async function cambiarEstado(e) {
    setErrorAccion('');
    try {
      await editarEmpleado(idTienda, e.idLogin, { ...permisosDe(e), activo: !e.activo });
      setAviso(e.activo ? `${e.nombres} ya no puede entrar a ${tienda?.nombre}` : `${e.nombres} puede volver a entrar`);
      recargar();
    } catch (err) {
      setErrorAccion(mensajeDeError(err));
    }
  }

  async function quitar(e) {
    setPorQuitar(null);
    setErrorAccion('');
    try {
      await quitarEmpleado(idTienda, e.idLogin);
      setAviso(`${e.nombres} ${e.primerApellido} ya no es empleado de ${tienda?.nombre}`);
      recargar();
    } catch (err) {
      setErrorAccion(mensajeDeError(err));
    }
  }

  return (
    <section className="max-w-4xl space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-xl font-bold text-[#101010]">Empleados</h1>
          {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
        </div>
        {r && r.maxEmpleados > 0 && !agregando && (
          <button
            onClick={() => setAgregando(true)}
            disabled={lleno}
            title={lleno ? 'Ya usaste todos los lugares de tu plan' : undefined}
            className={`${BOTON} inline-flex items-center gap-1 text-[13px]`}
          >
            <IconoMas tamano={16} /> Agregar empleado
          </button>
        )}
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}
      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      {cargando ? (
        <p className="text-[#616161]">Cargando empleados...</p>
      ) : r && (
        <>
          <UsoDelPlan respuesta={r} />

          {agregando && (
            <FormularioEmpleado
              idTienda={idTienda}
              onCancelar={() => setAgregando(false)}
              onGuardado={(e) => {
                setAgregando(false);
                setAviso(`${e.nombres} ${e.primerApellido} ya puede entrar al panel con ${e.correo}`);
                recargar();
              }}
            />
          )}

          {r.empleados.length === 0 ? (
            r.maxEmpleados > 0 && !agregando && (
              <div className={`${TARJETA} flex flex-col items-center px-6 py-10 text-center`}>
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
                  <IconoEmpleados tamano={24} />
                </span>
                <p className="mt-3 font-medium text-[#101010]">Todavía no tienes empleados</p>
                <p className="mt-1 max-w-sm text-[13px] text-[#616161]">
                  Agrega a quien te ayuda con la tienda y elige qué puede hacer: atender pedidos, cargar productos,
                  contar el stock o atender clientes.
                </p>
              </div>
            )
          ) : (
            <div className={`${TARJETA} overflow-x-auto`}>
              <table className="w-full min-w-[640px] text-[13px]">
                <thead className={ENCABEZADO_TABLA}>
                  <tr>
                    <th className="px-4 py-2.5 font-medium">Empleado</th>
                    <th className="px-4 py-2.5 font-medium">Puede usar</th>
                    <th className="px-4 py-2.5 font-medium">Estado</th>
                    <th className="px-4 py-2.5 text-right font-medium">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {r.empleados.map((e) => (
                    editando === e.idLogin ? (
                      <tr key={e.idLogin} className="border-t border-[#ebebeb] bg-[#fafafa]">
                        <td colSpan={4} className="px-4 py-3">
                          <EditarPermisos
                            idTienda={idTienda}
                            empleado={e}
                            onCancelar={() => setEditando(null)}
                            onGuardado={() => {
                              setEditando(null);
                              setAviso(`Permisos de ${e.nombres} guardados`);
                              recargar();
                            }}
                          />
                        </td>
                      </tr>
                    ) : (
                      <tr key={e.idLogin} className="border-t border-[#ebebeb] align-top">
                        <td className="px-4 py-3">
                          <p className="font-medium text-[#101010]">{e.nombres} {e.primerApellido}</p>
                          <p className="text-[#616161]">{e.correo}</p>
                          <p className="mt-0.5 text-[12px] text-[#8a8a8a]">
                            Desde {formatoFecha(e.fechaCreacion)}{e.dobleFactor ? ' · con doble autenticación' : ''}
                          </p>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {PERMISOS.filter((p) => e[p.campo]).map((p) => (
                              <span key={p.clave} className={etiqueta('info')}>{p.texto}</span>
                            ))}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          {!e.cuentaActiva ? (
                            <span className={etiqueta('error')}>Cuenta desactivada</span>
                          ) : e.activo ? (
                            <span className={etiqueta('exito')}>Activo</span>
                          ) : (
                            <span className={etiqueta('neutro')}>Suspendido</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {porQuitar === e.idLogin ? (
                            <span className="inline-flex flex-wrap items-center justify-end gap-2">
                              <span className="text-[#616161]">¿Quitarlo de la tienda?</span>
                              <button onClick={() => quitar(e)} className="rounded-lg bg-[#c70a24] px-2.5 py-1 font-semibold text-white hover:bg-[#a30a1f]">
                                Sí, quitar
                              </button>
                              <button onClick={() => setPorQuitar(null)} className={BOTON_SECUNDARIO}>No</button>
                            </span>
                          ) : (
                            <span className="inline-flex flex-wrap justify-end gap-x-3 gap-y-1">
                              <button onClick={() => setEditando(e.idLogin)} className="text-[#1650d0] hover:underline">
                                Editar permisos
                              </button>
                              <button
                                onClick={() => cambiarEstado(e)}
                                disabled={!e.activo && lleno}
                                title={!e.activo && lleno ? 'Ya usaste todos los lugares de tu plan' : undefined}
                                className="text-[#303030] hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {e.activo ? 'Suspender' : 'Reactivar'}
                              </button>
                              <button onClick={() => setPorQuitar(e.idLogin)} className="text-[#c70a24] hover:underline">
                                Quitar
                              </button>
                            </span>
                          )}
                        </td>
                      </tr>
                    )
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}

/** Cuántos lugares del plan se usan. Con el plan Gratis invita a mejorar el plan. */
function UsoDelPlan({ respuesta: r }) {
  if (r.maxEmpleados === 0) {
    return (
      <div className={`${TARJETA} flex flex-wrap items-center justify-between gap-3 px-4 py-4`}>
        <div>
          <p className="font-semibold text-[#101010]">Tu plan {r.plan} no incluye empleados</p>
          <p className="text-[13px] text-[#616161]">
            Mejora tu plan para que otras personas te ayuden con la tienda, cada una con su propia cuenta y solo
            con los permisos que tú elijas.
          </p>
        </div>
        <Link to="/panel/plan" className={`${BOTON} text-[13px]`}>Ver planes</Link>
      </div>
    );
  }
  const porcentaje = Math.min(100, (r.activos / r.maxEmpleados) * 100);
  return (
    <div className={`${TARJETA} px-4 py-3`}>
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-[13px]">
        <span className="text-[#303030]">
          <b>{r.activos} de {r.maxEmpleados}</b> {r.maxEmpleados === 1 ? 'empleado activo' : 'empleados activos'} · plan {r.plan}
        </span>
        {r.activos >= r.maxEmpleados && (
          <Link to="/panel/plan" className="text-[#1650d0] hover:underline">Necesito más lugares</Link>
        )}
      </div>
      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-[#ebebeb]">
        <div className="h-full rounded-full bg-[#303030]" style={{ width: `${porcentaje}%` }} />
      </div>
    </div>
  );
}

/** Los cuatro permisos con su explicación, como casillas. */
function CasillasPermisos({ valores, onCambiar }) {
  return (
    <fieldset className="grid gap-2 sm:grid-cols-2">
      <legend className="mb-1 text-[13px] text-[#303030]">Qué puede hacer en la tienda</legend>
      {PERMISOS.map((p) => (
        <label
          key={p.clave}
          className={`flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-2 ${
            valores[p.campo] ? 'border-[#303030] bg-white' : 'border-[#e3e3e3] bg-white hover:border-[#b5b5b5]'
          }`}
        >
          <input
            type="checkbox"
            checked={Boolean(valores[p.campo])}
            onChange={(ev) => onCambiar(p.campo, ev.target.checked)}
            className="mt-0.5 h-4 w-4 accent-[#303030]"
          />
          <span>
            <span className="block text-[13px] font-medium text-[#101010]">{p.texto}</span>
            <span className="block text-[12px] text-[#616161]">{p.ayuda}</span>
          </span>
        </label>
      ))}
    </fieldset>
  );
}

function permisosDe(e) {
  return {
    puedePedidos: e.puedePedidos, puedeProductos: e.puedeProductos,
    puedeInventario: e.puedeInventario, puedeClientes: e.puedeClientes,
  };
}

function algunPermiso(v) {
  return PERMISOS.some((p) => v[p.campo]);
}

/** Agregar un empleado: sus datos, una contraseña para su primer ingreso y sus permisos. */
function FormularioEmpleado({ idTienda, onCancelar, onGuardado }) {
  const [d, setD] = useState(VACIO);
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  const cambiar = (campo) => (ev) => {
    setD((x) => ({ ...x, [campo]: ev.target.value }));
    setErrores((x) => ({ ...x, [campo]: undefined }));
  };

  async function guardar(ev) {
    ev.preventDefault();
    setError('');
    if (!algunPermiso(d)) {
      setError('Marca al menos un permiso para el empleado');
      return;
    }
    setGuardando(true);
    try {
      const guardado = await crearEmpleado(idTienda, {
        ...d,
        nombres: d.nombres.trim(),
        primerApellido: d.primerApellido.trim(),
        correo: d.correo.trim(),
        contrasena: d.contrasena || null,
      });
      onGuardado(guardado);
    } catch (e) {
      if (e.response?.data?.campos) {
        setErrores(e.response.data.campos);
      } else {
        setError(mensajeDeError(e));
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={guardar} className={`${TARJETA} space-y-4 p-4`}>
      <div>
        <h2 className="font-semibold text-[#101010]">Agregar empleado</h2>
        <p className="text-[13px] text-[#616161]">
          Entrará al panel con su correo y esta contraseña; después la cambia en Seguridad. Dásela en persona o por un
          mensaje privado.
        </p>
      </div>
      {error && <Mensaje tipo="error" texto={error} onCerrar={() => setError('')} />}
      <div className="grid gap-3 sm:grid-cols-2">
        <CampoPanel etiqueta="Nombres" error={errores.nombres}>
          <input required value={d.nombres} onChange={cambiar('nombres')} className={inputPanel(errores.nombres)} />
        </CampoPanel>
        <CampoPanel etiqueta="Apellido" error={errores.primerApellido}>
          <input required value={d.primerApellido} onChange={cambiar('primerApellido')} className={inputPanel(errores.primerApellido)} />
        </CampoPanel>
        <CampoPanel etiqueta="Correo" error={errores.correo}>
          <input required type="email" value={d.correo} onChange={cambiar('correo')} autoComplete="off" className={inputPanel(errores.correo)} />
        </CampoPanel>
        <CampoPanel
          etiqueta="Contraseña inicial"
          error={errores.contrasena}
          ayuda="Mínimo 8 caracteres, con una letra y un número. Si ya trabaja en otra tienda de Vendely, déjala vacía."
        >
          <input type="password" value={d.contrasena} onChange={cambiar('contrasena')} autoComplete="new-password" className={inputPanel(errores.contrasena)} />
        </CampoPanel>
      </div>
      <CasillasPermisos valores={d} onCambiar={(campo, valor) => setD((x) => ({ ...x, [campo]: valor }))} />
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancelar} className={`${BOTON_SECUNDARIO} text-[13px]`}>Cancelar</button>
        <button type="submit" disabled={guardando} className={`${BOTON} text-[13px]`}>
          {guardando ? 'Agregando...' : 'Agregar empleado'}
        </button>
      </div>
    </form>
  );
}

/** Cambiar los permisos de un empleado (dentro de su fila). */
function EditarPermisos({ idTienda, empleado, onCancelar, onGuardado }) {
  const [v, setV] = useState(permisosDe(empleado));
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  async function guardar() {
    setError('');
    if (!algunPermiso(v)) {
      setError('Marca al menos un permiso. Si ya no debe entrar, usa Suspender o Quitar.');
      return;
    }
    setGuardando(true);
    try {
      await editarEmpleado(idTienda, empleado.idLogin, { ...v, activo: empleado.activo });
      onGuardado();
    } catch (e) {
      setError(mensajeDeError(e));
      setGuardando(false);
    }
  }

  return (
    <div className="space-y-3">
      <p className="font-medium text-[#101010]">Permisos de {empleado.nombres} {empleado.primerApellido}</p>
      {error && <Mensaje tipo="error" texto={error} onCerrar={() => setError('')} />}
      <CasillasPermisos valores={v} onCambiar={(campo, valor) => setV((x) => ({ ...x, [campo]: valor }))} />
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancelar} className={`${BOTON_SECUNDARIO} text-[13px]`}>Cancelar</button>
        <button type="button" onClick={guardar} disabled={guardando} className={`${BOTON} text-[13px]`}>
          {guardando ? 'Guardando...' : 'Guardar permisos'}
        </button>
      </div>
    </div>
  );
}
