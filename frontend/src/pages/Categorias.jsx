import { useEffect, useState } from 'react';
import CampoPanel from '../components/CampoPanel';
import ConfirmarAccion from '../components/ConfirmarAccion';
import { IconoCategoria, IconoCerrar, IconoMas } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { actualizarCategoria, crearCategoria, eliminarCategoria, listarCategorias } from '../services/categorias';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta, inputPanel } from '../utils/panel';

/**
 * Categorías de productos ("Polos", "Gorras"), como las colecciones de Shopify.
 * Se asignan a cada producto en su formulario, y en la tienda sirven para filtrar el catálogo.
 */
export default function Categorias() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const { avisar } = useToast();
  const [datos, setDatos] = useState({ idTienda: null, categorias: [], error: '' });
  const [version, setVersion] = useState(0);
  const [formulario, setFormulario] = useState(null);   // 'nueva' o la categoría a editar
  const [porEliminar, setPorEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

  useEffect(() => {
    if (!idTienda) return undefined;
    let vigente = true;
    listarCategorias(idTienda)
      .then((categorias) => vigente && setDatos({ idTienda, categorias, error: '' }))
      .catch((e) => vigente && setDatos({ idTienda, categorias: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  async function eliminar() {
    setEliminando(true);
    try {
      await eliminarCategoria(idTienda, porEliminar.idCategoria);
      avisar('Categoría eliminada');
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

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Categorías</h1>
          <p className="text-[13px] text-[#616161]">Agrupa tus productos para que tus clientes los encuentren rápido.</p>
        </div>
        <div className="flex items-center gap-3">
          {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
          {tienda && (
            <button onClick={() => setFormulario('nueva')} className={`${BOTON} inline-flex items-center gap-1`}>
              <IconoMas tamano={16} /> Crear categoría
            </button>
          )}
        </div>
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} onCerrar={() => setDatos((d) => ({ ...d, error: '' }))} />}
      {!cargandoTiendas && !tienda && <Mensaje tipo="info" texto="Primero crea una tienda." />}

      {tienda && (
        <div className={`${TARJETA} overflow-hidden`}>
          {!cargando && datos.categorias.length === 0 ? (
            <div className="flex flex-col items-center px-6 py-12 text-center">
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
                <IconoCategoria tamano={24} />
              </span>
              <p className="mt-3 font-medium text-[#101010]">Todavía no tienes categorías</p>
              <p className="mt-1 max-w-sm text-[13px] text-[#616161]">
                Por ejemplo "Polos", "Gorras" o "Ofertas". Luego elige en cada producto a cuáles pertenece.
              </p>
              <button onClick={() => setFormulario('nueva')} className={`${BOTON} mt-4`}>Crear categoría</button>
            </div>
          ) : (
            <table className="min-w-full text-[13px]">
              <thead className={ENCABEZADO_TABLA}>
                <tr>
                  <th className="px-4 py-2.5 font-medium">Categoría</th>
                  <th className="px-4 py-2.5 text-right font-medium">Productos</th>
                  <th className="px-4 py-2.5 font-medium">Estado</th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-[#ebebeb]">
                {cargando && (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-[#616161]">Cargando...</td></tr>
                )}
                {!cargando && datos.categorias.map((c) => (
                  <tr key={c.idCategoria} className="hover:bg-[#f7f7f7]">
                    <td className="px-4 py-3">
                      <p className="font-medium text-[#101010]">{c.nombre}</p>
                      {c.descripcion && <p className="text-xs text-[#616161]">{c.descripcion}</p>}
                    </td>
                    <td className="px-4 py-3 text-right">{c.productos}</td>
                    <td className="px-4 py-3">
                      <span className={etiqueta(c.estado === 'ACTIVA' ? 'exito' : 'neutro')}>
                        {c.estado === 'ACTIVA' ? 'Visible' : 'Oculta'}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      <button onClick={() => setFormulario(c)} className="rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">Editar</button>
                      <button onClick={() => setPorEliminar(c)} className="ml-1 rounded-lg px-2 py-1 font-medium text-[#c70a24] hover:bg-[#ffe7e6]">
                        Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {formulario && (
        <FormularioCategoria
          categoria={formulario === 'nueva' ? null : formulario}
          onCerrar={() => setFormulario(null)}
          onGuardar={async (cuerpo) => {
            if (formulario === 'nueva') await crearCategoria(idTienda, cuerpo);
            else await actualizarCategoria(idTienda, formulario.idCategoria, cuerpo);
            avisar(formulario === 'nueva' ? 'Categoría creada' : 'Categoría guardada');
            setFormulario(null);
            setVersion((v) => v + 1);
          }}
        />
      )}

      {porEliminar && (
        <ConfirmarAccion
          titulo={`¿Eliminar la categoría ${porEliminar.nombre}?`}
          texto={`Sus ${porEliminar.productos} producto(s) no se borran: solo dejan de estar en esta categoría.`}
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

function FormularioCategoria({ categoria, onCerrar, onGuardar }) {
  const [d, setD] = useState({
    nombre: categoria?.nombre ?? '',
    descripcion: categoria?.descripcion ?? '',
    activa: categoria ? categoria.estado === 'ACTIVA' : true,
  });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    const er = {};
    if (!d.nombre.trim()) er.nombre = 'El nombre es obligatorio';
    else if (d.nombre.trim().length > 100) er.nombre = 'Hasta 100 caracteres';
    if (d.descripcion.length > 500) er.descripcion = 'Hasta 500 caracteres';
    setErrores(er);
    if (Object.keys(er).length) return;
    setGuardando(true);
    setError('');
    try {
      await onGuardar({ nombre: d.nombre.trim(), descripcion: d.descripcion.trim() || null, activa: d.activa });
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else if (ex.response?.status === 409) setErrores({ nombre: mensajeDeError(ex) });
      else setError(mensajeDeError(ex));
      setGuardando(false);
    }
  }

  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:py-16">
      <form onSubmit={enviar} noValidate className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-[#ebebeb] px-5 py-3.5">
          <h2 className="font-semibold text-[#101010]">{categoria ? 'Editar categoría' : 'Crear categoría'}</h2>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="rounded-lg p-1 text-[#616161] hover:bg-[#f1f1f1]">
            <IconoCerrar tamano={18} />
          </button>
        </div>
        <div className="space-y-3 p-5 text-[13px]">
          {error && <Mensaje tipo="error" texto={error} />}
          <CampoPanel etiqueta="Nombre" error={errores.nombre}>
            <input
              value={d.nombre}
              onChange={(e) => setD((v) => ({ ...v, nombre: e.target.value }))}
              placeholder="Polos"
              className={inputPanel(errores.nombre)}
            />
          </CampoPanel>
          <CampoPanel etiqueta="Descripción (opcional)" error={errores.descripcion}>
            <textarea
              rows={3}
              value={d.descripcion}
              onChange={(e) => setD((v) => ({ ...v, descripcion: e.target.value }))}
              className={inputPanel(errores.descripcion)}
            />
          </CampoPanel>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={d.activa} onChange={(e) => setD((v) => ({ ...v, activa: e.target.checked }))} />
            Visible en la tienda
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
