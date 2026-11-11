import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Mensaje from '../components/Mensaje';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { formatoMoneda } from '../utils/formato';

// Cada fila de producto necesita una clave fija para que React no confunda las filas al eliminar una.
let siguienteClave = 1;
function filaNueva() {
  return { clave: siguienteClave++, idVariante: '', cantidad: 1 };
}

/**
 * Registrar una venta (RF3.1): elegir cliente y dirección, y agregar productos con
 * los botones "Añadir producto" y "Eliminar". El stock, el precio y el total final los
 * calcula el backend en una sola transacción; aquí se muestra una vista previa.
 */
export default function NuevaVenta() {
  const { tienda, idTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const navigate = useNavigate();

  const [catalogo, setCatalogo] = useState({ idTienda: null, clientes: [], productos: [], error: '' });
  const [idCliente, setIdCliente] = useState('');
  const [direccion, setDireccion] = useState({ estado: 'sin-cliente', valor: null, error: '' });
  const [lineas, setLineas] = useState(() => [filaNueva()]);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [guardando, setGuardando] = useState(false);

  // Clientes y productos de la tienda, para llenar las listas del formulario.
  useEffect(() => {
    if (!idTienda) {
      return;
    }
    Promise.all([
      api.get(`/panel/tiendas/${idTienda}/clientes`),
      api.get(`/panel/tiendas/${idTienda}/productos`),
    ])
      .then(([c, p]) => setCatalogo({
        idTienda,
        clientes: c.data.filter((x) => x.estado === 'ACTIVO'),
        // Solo se venden productos activos que tengan precio. Cada variante (talla, color) es una
        // opción de la lista, porque se vende y descuenta stock por variante.
        productos: p.data
          .filter((x) => x.estado === 'ACTIVO' && x.precio !== null)
          .flatMap((x) => ((x.variantes ?? []).length > 0 ? x.variantes : [{ idVariante: x.idVariante, nombre: 'Única', precio: x.precio, stock: x.stock }])
            .filter((v) => v.precio !== null)
            .map((v) => ({
              ...x,
              idVariante: v.idVariante,
              nombre: (x.opciones ?? []).length ? `${x.nombre} - ${v.nombre}` : x.nombre,
              precio: v.precio,
              stock: v.stock,
            }))),
        error: '',
      }))
      .catch((e) => setCatalogo({ idTienda, clientes: [], productos: [], error: mensajeDeError(e) }));
  }, [idTienda]);

  const cargando = cargandoTiendas || (idTienda !== null && catalogo.idTienda !== idTienda);

  // Al elegir un cliente se pide su ficha, que trae su dirección de envío.
  async function elegirCliente(valor) {
    setIdCliente(valor);
    setErrores((e) => ({ ...e, cliente: undefined, direccion: undefined }));
    if (!valor) {
      setDireccion({ estado: 'sin-cliente', valor: null, error: '' });
      return;
    }
    setDireccion({ estado: 'cargando', valor: null, error: '' });
    try {
      const { data } = await api.get(`/panel/tiendas/${idTienda}/clientes/${valor}`);
      setDireccion({ estado: 'lista', valor: data.direccion, error: '' });
    } catch (e) {
      setDireccion({ estado: 'lista', valor: null, error: mensajeDeError(e) });
    }
  }

  function producto(idVariante) {
    return catalogo.productos.find((p) => p.idVariante === Number(idVariante));
  }

  function cambiarLinea(clave, campo, valor) {
    setLineas((ls) => ls.map((l) => (l.clave === clave ? { ...l, [campo]: valor } : l)));
    setErrores((e) => ({ ...e, lineas: { ...e.lineas, [clave]: undefined } }));
  }

  function anadirLinea() {
    setLineas((ls) => [...ls, filaNueva()]);
  }

  function eliminarLinea(clave) {
    setLineas((ls) => ls.filter((l) => l.clave !== clave));
  }

  // Vista previa: precio vigente x cantidad. El envío depende del departamento y lo suma el backend.
  const subtotal = lineas.reduce((suma, l) => {
    const p = producto(l.idVariante);
    const cantidad = Number(l.cantidad);
    return p && cantidad > 0 ? suma + Number(p.precio) * cantidad : suma;
  }, 0);

  /** Revisa el formulario antes de enviarlo, para mostrar el error junto a cada campo. */
  function validar() {
    const nuevos = { lineas: {} };
    if (!idCliente) {
      nuevos.cliente = 'Elige un cliente';
    } else if (direccion.estado === 'cargando') {
      nuevos.direccion = 'Espera a que cargue la dirección del cliente';
    } else if (!direccion.valor) {
      nuevos.direccion = 'Este cliente no tiene dirección de envío. Agrégala en la pantalla de Clientes.';
    }
    lineas.forEach((l) => {
      const p = producto(l.idVariante);
      const cantidad = Number(l.cantidad);
      if (!p) {
        nuevos.lineas[l.clave] = 'Elige un producto';
      } else if (!Number.isInteger(cantidad) || cantidad < 1) {
        nuevos.lineas[l.clave] = 'La cantidad debe ser un número entero mayor que 0';
      } else if (p.stock !== null && cantidad > p.stock) {
        nuevos.lineas[l.clave] = `Solo hay ${p.stock} en stock`;
      }
    });
    setErrores(nuevos);
    return !nuevos.cliente && !nuevos.direccion && Object.keys(nuevos.lineas).length === 0;
  }

  async function guardar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    if (!validar()) {
      return;
    }
    setGuardando(true);
    try {
      const { data } = await api.post(`/panel/tiendas/${idTienda}/pedidos`, {
        idLogin: Number(idCliente),
        idDireccionEnvio: direccion.valor.idDireccion,
        lineas: lineas.map((l) => ({ idVariante: Number(l.idVariante), cantidad: Number(l.cantidad) })),
      });
      navigate(`/panel/ventas/${data.idPedido}`, {
        state: { aviso: `Venta #${data.numeroPedido} registrada. El stock ya se descontó.` },
      });
    } catch (e) {
      // Por ejemplo, 409 "Stock insuficiente de Polo básico (Única)": la venta no se guardó.
      setErrorGeneral(mensajeDeError(e));
      setGuardando(false);
    }
  }

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (cargando) {
    return <p className="text-slate-500">Cargando...</p>;
  }
  if (!tienda) {
    return <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para registrar ventas." />;
  }

  // La vista previa usa soles; el monto final, con su moneda, lo devuelve el backend al guardar.
  const moneda = 'PEN';

  // Un producto ya elegido en otra fila no se ofrece de nuevo: para más unidades se sube la cantidad.
  const elegidos = lineas.map((l) => Number(l.idVariante));

  return (
    <section className="max-w-4xl space-y-4">
      <div>
        <p className="text-sm text-slate-500">
          <Link to="/panel/ventas" className="hover:underline">Ventas</Link> / Registrar venta
        </p>
        <h1 className="text-xl font-bold text-[#101010]">Registrar venta</h1>
        <p className="text-sm text-slate-600">Tienda: <span className="font-medium">{tienda.nombre}</span></p>
      </div>

      {catalogo.error && <Mensaje tipo="error" texto={catalogo.error} />}
      {errorGeneral && <Mensaje tipo="error" texto={errorGeneral} onCerrar={() => setErrorGeneral('')} />}

      <form onSubmit={guardar} noValidate className="space-y-4">
        {/* Cliente y dirección */}
        <div className="space-y-3 rounded-lg bg-white p-5 shadow-sm">
          <h2 className="font-semibold text-slate-800">Cliente</h2>
          {catalogo.clientes.length === 0 ? (
            <p className="text-sm text-slate-600">
              Esta tienda no tiene clientes activos. Regístralos primero en la pantalla de Clientes.
            </p>
          ) : (
            <div>
              <select
                value={idCliente}
                onChange={(e) => elegirCliente(e.target.value)}
                className={`w-full rounded-md border bg-white px-3 py-2 ${errores.cliente ? 'border-red-500' : 'border-slate-300'}`}
              >
                <option value="">Elige un cliente</option>
                {catalogo.clientes.map((c) => (
                  <option key={c.idLogin} value={c.idLogin}>
                    {c.nombres} {c.primerApellido} ({c.correo})
                  </option>
                ))}
              </select>
              {errores.cliente && <p className="mt-1 text-sm text-red-600">{errores.cliente}</p>}
            </div>
          )}

          {direccion.estado === 'cargando' && <p className="text-sm text-slate-500">Cargando dirección...</p>}
          {direccion.estado === 'lista' && direccion.valor && (
            <div className="rounded-md bg-slate-50 p-3 text-sm text-slate-700">
              <p className="font-medium text-slate-800">Dirección de envío</p>
              <p>{direccion.valor.direccion}, {direccion.valor.distrito ?? [direccion.valor.ciudad, direccion.valor.pais].filter(Boolean).join(", ")}</p>
              {direccion.valor.referencia && <p className="text-slate-500">Ref.: {direccion.valor.referencia}</p>}
            </div>
          )}
          {direccion.error && <p className="text-sm text-red-600">{direccion.error}</p>}
          {errores.direccion && <p className="text-sm text-red-600">{errores.direccion}</p>}
        </div>

        {/* Productos: filas dinámicas */}
        <div className="space-y-3 rounded-lg bg-white p-5 shadow-sm">
          <h2 className="font-semibold text-slate-800">Productos</h2>
          {catalogo.productos.length === 0 && (
            <p className="text-sm text-slate-600">
              Esta tienda no tiene productos activos con precio. Regístralos primero en la pantalla de Productos.
            </p>
          )}

          {lineas.map((l) => {
            const p = producto(l.idVariante);
            const cantidad = Number(l.cantidad);
            const error = errores.lineas?.[l.clave];
            return (
              <div key={l.clave} className="rounded-md border border-slate-200 p-3">
                <div className="flex flex-wrap items-start gap-3">
                  <select
                    value={l.idVariante}
                    onChange={(e) => cambiarLinea(l.clave, 'idVariante', e.target.value)}
                    className={`min-w-60 flex-1 rounded-md border bg-white px-3 py-2 ${error ? 'border-red-500' : 'border-slate-300'}`}
                  >
                    <option value="">Elige un producto</option>
                    {catalogo.productos.map((op) => (
                      <option
                        key={op.idVariante}
                        value={op.idVariante}
                        disabled={op.idVariante !== Number(l.idVariante) && elegidos.includes(op.idVariante)}
                      >
                        {op.nombre} - {formatoMoneda(op.precio, moneda)} (stock: {op.stock ?? 0})
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    min="1"
                    value={l.cantidad}
                    onChange={(e) => cambiarLinea(l.clave, 'cantidad', e.target.value)}
                    aria-label="Cantidad"
                    className={`w-24 rounded-md border px-3 py-2 ${error ? 'border-red-500' : 'border-slate-300'}`}
                  />
                  <p className="w-28 py-2 text-right font-medium text-slate-800">
                    {p && cantidad > 0 ? formatoMoneda(Number(p.precio) * cantidad, moneda) : '-'}
                  </p>
                  <button
                    type="button"
                    onClick={() => eliminarLinea(l.clave)}
                    disabled={lineas.length === 1}
                    className="rounded-md border border-slate-300 px-3 py-2 text-sm text-red-600 hover:bg-red-50 disabled:opacity-40"
                  >
                    Eliminar
                  </button>
                </div>
                {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
              </div>
            );
          })}

          <button
            type="button"
            onClick={anadirLinea}
            disabled={lineas.length >= catalogo.productos.length}
            className="rounded-md border border-violet-300 px-3 py-2 text-sm font-medium text-violet-700 hover:bg-violet-50 disabled:opacity-40"
          >
            + Añadir producto
          </button>
        </div>

        {/* Total y guardar */}
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-white p-5 shadow-sm">
          <div>
            <p className="text-sm text-slate-600">Total de productos</p>
            <p className="text-2xl font-bold text-slate-800">{formatoMoneda(subtotal, moneda)}</p>
            <p className="text-xs text-slate-500">
              El costo de envío se suma al guardar, según el departamento de la dirección.
            </p>
          </div>
          <div className="flex gap-3">
            <Link to="/panel/ventas" className="rounded-md border border-slate-300 px-4 py-2 text-slate-700 hover:bg-slate-100">
              Cancelar
            </Link>
            <button
              type="submit"
              disabled={guardando}
              className="rounded-md bg-violet-600 px-4 py-2 font-medium text-white hover:bg-violet-700 disabled:opacity-50"
            >
              {guardando ? 'Guardando...' : 'Guardar venta'}
            </button>
          </div>
        </div>
      </form>
    </section>
  );
}
