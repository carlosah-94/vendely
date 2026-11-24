import { useEffect, useMemo, useState } from 'react';
import CampoPanel from '../components/CampoPanel';
import ConfirmarAccion from '../components/ConfirmarAccion';
import { IconoCerrar, IconoDescuento, IconoMas } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import {
  actualizarOferta, cambiarEstadoOferta, crearOferta, eliminarOferta, listarOfertas,
} from '../services/ofertas';
import { listarProductos } from '../services/productos';
import { ejemploMonto, esMontoValido, formatoFecha, formatoMoneda, montoVacio, simboloMoneda } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta, inputPanel } from '../utils/panel';

const MOTIVOS = { LIQUIDACION: 'Liquidación', TEMPORADA: 'Temporada', LANZAMIENTO: 'Lanzamiento', OTRO: 'Otro' };

const ESTADOS = {
  ACTIVA: { texto: 'Activa', tono: 'exito' },
  PROGRAMADA: { texto: 'Programada', tono: 'info' },
  VENCIDA: { texto: 'Vencida', tono: 'neutro' },
  PAUSADA: { texto: 'Pausada', tono: 'aviso' },
};

/** "20 %" o "S/ 10.00 menos". */
function textoDescuento(o, moneda) {
  return o.tipoDescuento === 'PORCENTAJE' ? `${Number(o.valor)} %` : `${formatoMoneda(o.valor, moneda)}`;
}

/**
 * Descuentos automáticos, como en Shopify: un porcentaje o un monto fijo sobre algunos productos,
 * entre dos fechas. Mientras la oferta está vigente, la tienda muestra el precio rebajado (con el de
 * antes tachado) y el pedido se cobra con el descuento, sin que el cliente escriba ningún código.
 */
export default function Descuentos() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const { avisar } = useToast();
  const [datos, setDatos] = useState({ idTienda: null, ofertas: [], productos: [], error: '' });
  const [version, setVersion] = useState(0);
  const [formulario, setFormulario] = useState(null);   // 'nueva' o la oferta a editar
  const [porEliminar, setPorEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

  useEffect(() => {
    if (!idTienda) return undefined;
    let vigente = true;
    Promise.all([listarOfertas(idTienda), listarProductos(idTienda)])
      .then(([ofertas, productos]) => vigente && setDatos({ idTienda, ofertas, productos, error: '' }))
      .catch((e) => vigente && setDatos({ idTienda, ofertas: [], productos: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  async function pausar(o) {
    try {
      await cambiarEstadoOferta(idTienda, o.idOferta, !o.activa);
      avisar(o.activa ? 'Oferta pausada' : 'Oferta activada');
      setVersion((v) => v + 1);
    } catch (e) {
      setDatos((d) => ({ ...d, error: mensajeDeError(e) }));
    }
  }

  async function eliminar() {
    setEliminando(true);
    try {
      await eliminarOferta(idTienda, porEliminar.idOferta);
      avisar('Oferta eliminada');
      setVersion((v) => v + 1);
    } catch (e) {
      setDatos((d) => ({ ...d, error: mensajeDeError(e) }));
    } finally {
      setEliminando(false);
      setPorEliminar(null);
    }
  }

  if (errorTiendas) return <Mensaje tipo="error" texto={errorTiendas} />;
  const cargando = cargandoTiendas || (idTienda !== null && datos.idTienda !== idTienda);
  const moneda = tienda?.moneda ?? 'PEN';

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Descuentos</h1>
          <p className="text-[13px] text-[#616161]">
            Ofertas automáticas: el precio rebajado se ve en tu tienda y se cobra solo, sin códigos.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
          {tienda && (
            <button onClick={() => setFormulario('nueva')} className={`${BOTON} inline-flex items-center gap-1`}>
              <IconoMas tamano={16} /> Crear oferta
            </button>
          )}
        </div>
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} onCerrar={() => setDatos((d) => ({ ...d, error: '' }))} />}
      {!cargandoTiendas && !tienda && <Mensaje tipo="info" texto="Primero crea una tienda." />}

      {tienda && (
        <div className={`${TARJETA} overflow-x-auto`}>
          {!cargando && datos.ofertas.length === 0 ? (
            <div className="flex flex-col items-center px-6 py-12 text-center">
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
                <IconoDescuento tamano={24} />
              </span>
              <p className="mt-3 font-medium text-[#101010]">Todavía no tienes ofertas</p>
              <p className="mt-1 max-w-sm text-[13px] text-[#616161]">
                Por ejemplo "20 % en polos por fin de temporada" o "S/ 10 menos en el lanzamiento".
              </p>
              <button onClick={() => setFormulario('nueva')} className={`${BOTON} mt-4`}>Crear oferta</button>
            </div>
          ) : (
            <table className="min-w-full text-[13px]">
              <thead className={ENCABEZADO_TABLA}>
                <tr>
                  <th className="px-4 py-2.5 font-medium">Oferta</th>
                  <th className="px-4 py-2.5 font-medium">Descuento</th>
                  <th className="px-4 py-2.5 font-medium">Vigencia</th>
                  <th className="px-4 py-2.5 text-right font-medium">Productos</th>
                  <th className="px-4 py-2.5 text-right font-medium">Vendidos</th>
                  <th className="px-4 py-2.5 font-medium">Estado</th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-[#ebebeb]">
                {cargando && (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-[#616161]">Cargando...</td></tr>
                )}
                {!cargando && datos.ofertas.map((o) => (
                  <tr key={o.idOferta} className="hover:bg-[#f7f7f7]">
                    <td className="px-4 py-3">
                      <p className="font-medium text-[#101010]">{o.nombre}</p>
                      <p className="text-xs text-[#616161]">{MOTIVOS[o.motivo]}</p>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-medium">{textoDescuento(o, moneda)}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-[#616161]">
                      <p>Desde {formatoFecha(o.fechaInicio)}</p>
                      <p>{o.fechaFin ? `Hasta ${formatoFecha(o.fechaFin)}` : 'Sin fecha de fin'}</p>
                    </td>
                    <td className="px-4 py-3 text-right">{o.productos.length}</td>
                    <td className="px-4 py-3 text-right">{o.unidadesVendidas}</td>
                    <td className="px-4 py-3">
                      <span className={etiqueta(ESTADOS[o.estado]?.tono)}>{ESTADOS[o.estado]?.texto ?? o.estado}</span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      <button onClick={() => setFormulario(o)} className="rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">Editar</button>
                      <button onClick={() => pausar(o)} className="ml-1 rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">
                        {o.activa ? 'Pausar' : 'Activar'}
                      </button>
                      {o.unidadesVendidas === 0 && (
                        <button onClick={() => setPorEliminar(o)} className="ml-1 rounded-lg px-2 py-1 font-medium text-[#c70a24] hover:bg-[#ffe7e6]">
                          Eliminar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {formulario && (
        <FormularioOferta
          oferta={formulario === 'nueva' ? null : formulario}
          productos={datos.productos}
          moneda={moneda}
          onCerrar={() => setFormulario(null)}
          onGuardar={async (cuerpo) => {
            if (formulario === 'nueva') await crearOferta(idTienda, cuerpo);
            else await actualizarOferta(idTienda, formulario.idOferta, cuerpo);
            avisar(formulario === 'nueva' ? 'Oferta creada' : 'Oferta guardada');
            setFormulario(null);
            setVersion((v) => v + 1);
          }}
        />
      )}

      {porEliminar && (
        <ConfirmarAccion
          titulo={`¿Eliminar la oferta ${porEliminar.nombre}?`}
          texto="Sus productos vuelven a su precio normal. Los productos no se borran."
          boton="Eliminar"
          peligro
          enviando={eliminando}
          onConfirmar={eliminar}
          onCancelar={() => setPorEliminar(null)}
        />
      )}
    </section>
  );
}

/** Fecha del backend (ISO) al formato del input datetime-local, en la hora del navegador. */
function aLocal(iso) {
  if (!iso) return '';
  const f = new Date(iso);
  const dos = (n) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}T${dos(f.getHours())}:${dos(f.getMinutes())}`;
}

function aIso(local) {
  return local ? new Date(local).toISOString() : null;
}

function FormularioOferta({ oferta, productos, moneda, onCerrar, onGuardar }) {
  // Si ya se vendió con la oferta, su tipo, valor e inicio no cambian (la base lo protege).
  const usada = (oferta?.unidadesVendidas ?? 0) > 0;
  const [d, setD] = useState({
    nombre: oferta?.nombre ?? '',
    motivo: oferta?.motivo ?? 'TEMPORADA',
    tipoDescuento: oferta?.tipoDescuento ?? 'PORCENTAJE',
    valor: oferta ? String(Number(oferta.valor)) : '',
    inicio: aLocal(oferta?.fechaInicio),
    fin: aLocal(oferta?.fechaFin),
    productos: oferta?.productos ?? [],
    activa: oferta ? oferta.activa : true,
  });
  const [buscar, setBuscar] = useState('');
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  const visibles = useMemo(() => {
    const texto = buscar.trim().toLowerCase();
    return texto ? productos.filter((p) => p.nombre.toLowerCase().includes(texto)) : productos;
  }, [productos, buscar]);
  const elegidos = new Set(d.productos);
  const porcentaje = d.tipoDescuento === 'PORCENTAJE';

  function alternar(idProducto) {
    setD((v) => ({
      ...v,
      productos: v.productos.includes(idProducto)
        ? v.productos.filter((id) => id !== idProducto)
        : [...v.productos, idProducto],
    }));
  }

  // Ejemplo del precio rebajado con el primer producto elegido, para que el dueño vea el efecto.
  const ejemplo = productos.find((p) => elegidos.has(p.idProducto) && p.precio != null);
  const valorNumero = Number(String(d.valor).replace(',', '.'));
  let precioEjemplo = null;
  if (ejemplo && valorNumero > 0) {
    const precio = Number(ejemplo.precio);
    precioEjemplo = Math.max(precio - (porcentaje ? (precio * valorNumero) / 100 : valorNumero), 0);
  }

  async function enviar(e) {
    e.preventDefault();
    const er = {};
    const valor = String(d.valor).trim().replace(',', '.');
    if (!d.nombre.trim()) er.nombre = 'El nombre es obligatorio';
    else if (d.nombre.trim().length > 120) er.nombre = 'Hasta 120 caracteres';
    if (porcentaje) {
      if (!/^\d+(\.\d{1,2})?$/.test(valor) || Number(valor) <= 0 || Number(valor) >= 100) {
        er.valor = 'Un porcentaje mayor que 0 y menor que 100';
      }
    } else if (!esMontoValido(valor, moneda) || Number(valor) <= 0) {
      er.valor = `Un monto mayor que 0, ${ejemploMonto(moneda)}`;
    }
    if (d.fin && d.inicio && new Date(d.fin) <= new Date(d.inicio)) er.fin = 'Debe ser posterior al inicio';
    if (d.fin && !d.inicio && new Date(d.fin) <= new Date()) er.fin = 'Debe ser una fecha futura';
    if (d.productos.length === 0) er.productos = 'Elige al menos un producto';
    setErrores(er);
    if (Object.keys(er).length) return;

    setGuardando(true);
    setError('');
    try {
      await onGuardar({
        nombre: d.nombre.trim(),
        motivo: d.motivo,
        tipoDescuento: d.tipoDescuento,
        valor,
        fechaInicio: aIso(d.inicio),
        fechaFin: aIso(d.fin),
        productos: d.productos,
        activa: d.activa,
      });
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else setError(mensajeDeError(ex));
      setGuardando(false);
    }
  }

  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:py-12">
      <form onSubmit={enviar} noValidate className="w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-[#ebebeb] px-5 py-3.5">
          <h2 className="font-semibold text-[#101010]">{oferta ? 'Editar oferta' : 'Crear oferta'}</h2>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="rounded-lg p-1 text-[#616161] hover:bg-[#f1f1f1]">
            <IconoCerrar tamano={18} />
          </button>
        </div>
        <div className="space-y-3 p-5 text-[13px]">
          {error && <Mensaje tipo="error" texto={error} />}
          {usada && (
            <Mensaje
              tipo="info"
              texto={`Ya se vendieron ${oferta.unidadesVendidas} unidad(es) con esta oferta: su descuento y su inicio no se pueden cambiar. Puedes pausarla y crear otra.`}
            />
          )}

          <div className="grid gap-3 sm:grid-cols-[1fr_180px]">
            <CampoPanel etiqueta="Nombre" error={errores.nombre}>
              <input
                value={d.nombre}
                onChange={(e) => setD((v) => ({ ...v, nombre: e.target.value }))}
                placeholder="Fin de temporada"
                className={inputPanel(errores.nombre)}
              />
            </CampoPanel>
            <CampoPanel etiqueta="Motivo">
              <select value={d.motivo} onChange={(e) => setD((v) => ({ ...v, motivo: e.target.value }))} className={inputPanel()}>
                {Object.entries(MOTIVOS).map(([clave, texto]) => <option key={clave} value={clave}>{texto}</option>)}
              </select>
            </CampoPanel>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <CampoPanel etiqueta="Tipo de descuento">
              <select
                value={d.tipoDescuento}
                disabled={usada}
                onChange={(e) => setD((v) => ({ ...v, tipoDescuento: e.target.value }))}
                className={inputPanel()}
              >
                <option value="PORCENTAJE">Porcentaje (%)</option>
                <option value="MONTO_FIJO">Monto fijo ({simboloMoneda(moneda)})</option>
              </select>
            </CampoPanel>
            <CampoPanel
              etiqueta={porcentaje ? 'Porcentaje' : `Monto a descontar (${simboloMoneda(moneda)})`}
              error={errores.valor}
              ayuda={precioEjemplo != null && !errores.valor
                ? `${ejemplo.nombre}: ${formatoMoneda(ejemplo.precio, moneda)} → ${formatoMoneda(precioEjemplo, moneda)}`
                : undefined}
            >
              <input
                inputMode="decimal"
                value={d.valor}
                disabled={usada}
                onChange={(e) => setD((v) => ({ ...v, valor: e.target.value }))}
                placeholder={porcentaje ? '20' : montoVacio(moneda)}
                className={inputPanel(errores.valor)}
              />
            </CampoPanel>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <CampoPanel etiqueta="Empieza" ayuda={oferta ? undefined : 'Vacío = empieza ahora'}>
              <input
                type="datetime-local"
                value={d.inicio}
                disabled={usada}
                onChange={(e) => setD((v) => ({ ...v, inicio: e.target.value }))}
                className={inputPanel()}
              />
            </CampoPanel>
            <CampoPanel etiqueta="Termina (opcional)" error={errores.fin} ayuda="Vacío = sin fecha de fin">
              <input
                type="datetime-local"
                value={d.fin}
                onChange={(e) => setD((v) => ({ ...v, fin: e.target.value }))}
                className={inputPanel(errores.fin)}
              />
            </CampoPanel>
          </div>

          {/* No va dentro de CampoPanel (un <label>): la lista tiene sus propios checkbox con label */}
          <div>
            <span className="text-[13px] text-[#303030]">
              Productos ({d.productos.length} elegido{d.productos.length === 1 ? '' : 's'})
            </span>
            <input
              aria-label="Buscar producto"
              value={buscar}
              onChange={(e) => setBuscar(e.target.value)}
              placeholder="Buscar producto"
              className={`${inputPanel()} mt-1`}
            />
            <ul className="mt-2 max-h-56 divide-y divide-[#ebebeb] overflow-y-auto rounded-lg border border-[#ebebeb]">
              {visibles.length === 0 && <li className="px-3 py-3 text-[#616161]">No hay productos con ese nombre.</li>}
              {visibles.map((p) => (
                <li key={p.idProducto}>
                  <label className="flex cursor-pointer items-center gap-3 px-3 py-2 hover:bg-[#f7f7f7]">
                    <input type="checkbox" checked={elegidos.has(p.idProducto)} onChange={() => alternar(p.idProducto)} />
                    <span className="flex-1">
                      {p.nombre}
                      {p.estado !== 'ACTIVO' && <span className="ml-2 text-xs text-[#8a8a8a]">(no está a la venta)</span>}
                    </span>
                    <span className="text-[#616161]">{p.precio != null ? formatoMoneda(p.precio, moneda) : '-'}</span>
                  </label>
                </li>
              ))}
            </ul>
            {errores.productos && <span className="mt-1 block text-[12px] text-[#8e1f0b]">{errores.productos}</span>}
          </div>

          <label className="flex items-center gap-2">
            <input type="checkbox" checked={d.activa} onChange={(e) => setD((v) => ({ ...v, activa: e.target.checked }))} />
            Oferta activa (desmárcala para dejarla pausada)
          </label>
        </div>
        <div className="flex justify-end gap-2 border-t border-[#ebebeb] bg-[#f7f7f7] px-5 py-3">
          <button type="button" onClick={onCerrar} className={BOTON_SECUNDARIO}>Cancelar</button>
          <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : 'Guardar'}</button>
        </div>
      </form>
    </div>
  );
}
