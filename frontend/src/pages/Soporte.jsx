import { useEffect, useState } from 'react';
import ConversacionSoporte from '../components/ConversacionSoporte';
import { IconoSoporte } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { obtenerSoporte, permitirSoporte, quitarSoporte } from '../services/soporte';
import { formatoFecha } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta, inputPanel } from '../utils/panel';

/** Lo que soporte puede y no puede hacer en la tienda (lo mismo que revisa el backend). */
const PUEDE = [
  'Subir tus productos, fotos, variantes, categorías y stock',
  'Configurar tus envíos y descuentos',
  'Elegir el tema y personalizar el diseño de tu tienda',
  'Revisar tus pedidos y clientes para ayudarte',
];
const NO_PUEDE = [
  'Cambiar tu plan ni hacer pagos',
  'Agregar o quitar empleados',
  'Eliminar ventas',
];

const DURACIONES = [
  { dias: 1, texto: '1 día' },
  { dias: 3, texto: '3 días' },
  { dias: 7, texto: '7 días' },
];

/** La sección del panel según la ruta de la petición: /api/panel/tiendas/5/productos/9 → "Productos". */
const SECCIONES = {
  productos: 'Productos',
  categorias: 'Categorías',
  inventario: 'Inventario',
  imagenes: 'Fotos',
  clientes: 'Clientes',
  pedidos: 'Pedidos',
  envios: 'Envíos',
  ofertas: 'Descuentos',
  cupones: 'Códigos de descuento',
};
const VERBOS = { POST: 'Agregó', PUT: 'Editó', PATCH: 'Editó', DELETE: 'Eliminó' };

/** Una línea del registro en palabras: quién y qué hizo. */
function describir(r) {
  if (r.accion === 'PERMITIDO') return { quien: 'Tú', que: 'Diste acceso a soporte de Vendely' };
  if (r.accion === 'QUITADO') return { quien: 'Tú', que: 'Quitaste el acceso a soporte de Vendely' };
  const partes = (r.ruta ?? '').split('/').filter(Boolean);   // api, panel, tiendas, 5, productos, ...
  const seccion = partes[4] ? SECCIONES[partes[4]] ?? partes[4] : 'Datos y diseño de la tienda';
  return { quien: 'Soporte de Vendely', que: `${VERBOS[r.metodo] ?? 'Cambió'} en ${seccion}` };
}

/**
 * Soporte de Vendely (solo el dueño): deja entrar al equipo de Vendely a armar la tienda por unos
 * días. El acceso vence solo y se puede quitar cuando quiera; todo lo que cambia soporte queda
 * en el registro de abajo.
 */
export default function Soporte() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const [datos, setDatos] = useState({ idTienda: null, respuesta: null, error: '' });
  const [dias, setDias] = useState(3);
  const [pedido, setPedido] = useState('');
  // Cambia al dar acceso con mensaje, para que la conversación se vuelva a cargar.
  const [versionMensajes, setVersionMensajes] = useState(0);
  const [guardando, setGuardando] = useState(false);
  const [confirmarQuitar, setConfirmarQuitar] = useState(false);
  const [aviso, setAviso] = useState('');
  const [errorAccion, setErrorAccion] = useState('');

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true;
    obtenerSoporte(idTienda)
      .then((respuesta) => vigente && setDatos({ idTienda, respuesta, error: '' }))
      .catch((e) => vigente && setDatos({ idTienda, respuesta: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda]);

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
    return <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para pedir ayuda a soporte." />;
  }

  const cargando = cargandoTiendas || datos.idTienda !== idTienda;
  const r = datos.respuesta;
  const activo = Boolean(r?.activoHasta);

  async function ejecutar(accion, textoExito) {
    setGuardando(true);
    setErrorAccion('');
    try {
      const respuesta = await accion();
      setDatos({ idTienda, respuesta, error: '' });
      setAviso(textoExito(respuesta));
    } catch (e) {
      setErrorAccion(mensajeDeError(e));
    } finally {
      setGuardando(false);
      setConfirmarQuitar(false);
    }
  }

  const permitir = () => ejecutar(async () => {
    const resp = await permitirSoporte(idTienda, dias, pedido.trim());
    if (pedido.trim()) {
      setPedido('');
      setVersionMensajes((v) => v + 1);
    }
    return resp;
  }, (resp) => `Listo: soporte de Vendely puede entrar a ${tienda?.nombre} hasta el ${formatoFecha(resp.activoHasta)}. Te escribiremos por aquí y a tu correo.`);
  const quitar = () => ejecutar(() => quitarSoporte(idTienda), () => 'Soporte de Vendely ya no puede entrar a tu tienda');

  return (
    <section className="max-w-4xl space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-bold text-[#101010]">Soporte de Vendely</h1>
        {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}
      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      <div className={`${TARJETA} p-5`}>
        <div className="flex items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#f1f1f1] text-[#303030]">
            <IconoSoporte tamano={22} />
          </span>
          <div className="space-y-1">
            <p className="font-semibold text-[#101010]">Te armamos la tienda</p>
            <p className="text-[13px] text-[#616161]">
              Da acceso al equipo de Vendely por unos días y nosotros subimos tus productos, configuramos los envíos y dejamos
              el diseño listo. No necesitas darnos tu contraseña, no ocupa un lugar de empleado y funciona con cualquier plan.
            </p>
          </div>
        </div>

        <div className="mt-4 grid gap-4 text-[13px] sm:grid-cols-2">
          <div>
            <p className="mb-1.5 font-medium text-[#101010]">Soporte puede</p>
            <ul className="space-y-1 text-[#303030]">
              {PUEDE.map((t) => <li key={t} className="flex gap-2"><span className="text-[#0c5132]">✓</span>{t}</li>)}
            </ul>
          </div>
          <div>
            <p className="mb-1.5 font-medium text-[#101010]">Soporte no puede</p>
            <ul className="space-y-1 text-[#303030]">
              {NO_PUEDE.map((t) => <li key={t} className="flex gap-2"><span className="text-[#8e1f0b]">✕</span>{t}</li>)}
            </ul>
          </div>
        </div>

        <div className="mt-5 border-t border-[#ebebeb] pt-4">
          {cargando ? (
            <p className="text-[13px] text-[#616161]">Cargando...</p>
          ) : activo ? (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-[13px] text-[#303030]">
                <span className={`${etiqueta('exito')} mr-2`}>Acceso activo</span>
                Soporte puede entrar hasta el <span className="font-semibold">{formatoFecha(r.activoHasta)}</span>.
              </p>
              {confirmarQuitar ? (
                <span className="inline-flex flex-wrap items-center gap-2 text-[13px]">
                  <span className="text-[#616161]">¿Quitar el acceso ahora?</span>
                  <button onClick={quitar} disabled={guardando} className="rounded-lg bg-[#c70a24] px-3 py-1.5 font-semibold text-white hover:bg-[#a30a1f] disabled:opacity-60">
                    Sí, quitar
                  </button>
                  <button onClick={() => setConfirmarQuitar(false)} className={BOTON_SECUNDARIO}>No</button>
                </span>
              ) : (
                <button onClick={() => setConfirmarQuitar(true)} className={`${BOTON_SECUNDARIO} text-[13px]`}>
                  Quitar acceso
                </button>
              )}
            </div>
          ) : (
            <div className="flex flex-wrap items-end gap-3">
              <label className="w-full text-[13px] text-[#303030]">
                <span className="mb-1 block font-medium">¿Qué necesitas?</span>
                <textarea
                  value={pedido}
                  onChange={(e) => setPedido(e.target.value.slice(0, 2000))}
                  rows={4}
                  placeholder="Cuéntanos qué quieres: qué productos subir (nombres, precios, tallas), dónde están las fotos, qué colores o estilo te gusta para la tienda..."
                  className={`${inputPanel(false)} w-full resize-y`}
                />
              </label>
              <label className="text-[13px] text-[#303030]">
                <span className="mb-1 block font-medium">Durante</span>
                <select value={dias} onChange={(e) => setDias(Number(e.target.value))} className={`${inputPanel(false)} w-36`}>
                  {DURACIONES.map((d) => <option key={d.dias} value={d.dias}>{d.texto}</option>)}
                </select>
              </label>
              <button onClick={permitir} disabled={guardando} className={`${BOTON} text-[13px]`}>
                {guardando ? 'Guardando...' : 'Dar acceso a soporte'}
              </button>
              <p className="w-full text-[12px] text-[#8a8a8a]">El acceso se corta solo al terminar el plazo. Puedes quitarlo antes cuando quieras.</p>
            </div>
          )}
        </div>
      </div>

      {idTienda && !cargando && (
        <ConversacionSoporte
          key={`${idTienda}-${versionMensajes}`}
          idTienda={idTienda}
          titulo="Mensajes con soporte"
          puedeEscribir={activo}
          bloqueado="Para escribirle a soporte, primero da acceso arriba (puedes contar lo que necesitas ahí mismo)."
        />
      )}

      {r && r.registro.length > 0 && (
        <div className={`${TARJETA} overflow-x-auto`}>
          <div className="border-b border-[#ebebeb] px-4 py-3">
            <h2 className="font-semibold text-[#101010]">Registro</h2>
            <p className="text-[12px] text-[#616161]">Todo lo que hizo soporte en tu tienda, y cuándo diste o quitaste el acceso.</p>
          </div>
          <table className="w-full min-w-[520px] text-[13px]">
            <thead className={ENCABEZADO_TABLA}>
              <tr>
                <th className="px-4 py-2.5 font-medium">Fecha</th>
                <th className="px-4 py-2.5 font-medium">Quién</th>
                <th className="px-4 py-2.5 font-medium">Qué hizo</th>
              </tr>
            </thead>
            <tbody>
              {r.registro.map((linea, i) => {
                const { quien, que } = describir(linea);
                return (
                  <tr key={`${linea.fecha}-${i}`} className="border-t border-[#ebebeb]">
                    <td className="whitespace-nowrap px-4 py-2.5 text-[#616161]">{formatoFecha(linea.fecha)}</td>
                    <td className="px-4 py-2.5 text-[#303030]">{quien}</td>
                    <td className="px-4 py-2.5 text-[#303030]">{que}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
