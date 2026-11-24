import { useEffect, useState } from 'react';
import CampoPanel from './CampoPanel';
import ConfirmarAccion from './ConfirmarAccion';
import { IconoCerrar, IconoDescuento, IconoMas } from './Iconos';
import Mensaje from './Mensaje';
import { useToast } from '../context/ToastContext';
import { mensajeDeError } from '../services/api';
import { actualizarCupon, cambiarEstadoCupon, crearCupon, eliminarCupon, listarCupones } from '../services/cupones';
import { ejemploMonto, esMontoValido, formatoFecha, formatoMoneda, montoVacio, simboloMoneda } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta, inputPanel } from '../utils/panel';

const ESTADOS = {
  ACTIVO: { texto: 'Activo', tono: 'exito' },
  PROGRAMADO: { texto: 'Programado', tono: 'info' },
  VENCIDO: { texto: 'Vencido', tono: 'neutro' },
  AGOTADO: { texto: 'Agotado', tono: 'neutro' },
  PAUSADO: { texto: 'Pausado', tono: 'aviso' },
};

/** Fecha del backend (ISO) al formato del input datetime-local, en la hora del navegador. */
function aLocal(iso) {
  if (!iso) return '';
  const f = new Date(iso);
  const dos = (n) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}T${dos(f.getHours())}:${dos(f.getMinutes())}`;
}

/**
 * Pestaña "Códigos de descuento" de Panel → Descuentos: códigos como "VERANO10" que el cliente
 * escribe en el checkout. Rebajan todo el pedido (los productos, ya con sus ofertas automáticas).
 */
export default function CodigosDescuento({ idTienda, moneda }) {
  const { avisar } = useToast();
  const [datos, setDatos] = useState({ cargado: false, cupones: [], error: '' });
  const [version, setVersion] = useState(0);
  const [formulario, setFormulario] = useState(null);   // 'nuevo' o el código a editar
  const [porEliminar, setPorEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

  useEffect(() => {
    let vigente = true;
    listarCupones(idTienda)
      .then((cupones) => vigente && setDatos({ cargado: true, cupones, error: '' }))
      .catch((e) => vigente && setDatos({ cargado: true, cupones: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  async function pausar(c) {
    try {
      await cambiarEstadoCupon(idTienda, c.idCupon, !c.activo);
      avisar(c.activo ? 'Código pausado' : 'Código activado');
      setVersion((v) => v + 1);
    } catch (e) {
      setDatos((d) => ({ ...d, error: mensajeDeError(e) }));
    }
  }

  async function eliminar() {
    setEliminando(true);
    try {
      await eliminarCupon(idTienda, porEliminar.idCupon);
      avisar('Código eliminado');
      setVersion((v) => v + 1);
    } catch (e) {
      setDatos((d) => ({ ...d, error: mensajeDeError(e) }));
    } finally {
      setEliminando(false);
      setPorEliminar(null);
    }
  }

  const descuento = (c) => (c.tipoDescuento === 'PORCENTAJE' ? `${Number(c.valor)} %` : formatoMoneda(c.valor, moneda));

  return (
    <div className="space-y-3">
      {datos.error && <Mensaje tipo="error" texto={datos.error} onCerrar={() => setDatos((d) => ({ ...d, error: '' }))} />}
      <div className="flex justify-end">
        <button onClick={() => setFormulario('nuevo')} className={`${BOTON} inline-flex items-center gap-1 text-[13px]`}>
          <IconoMas tamano={16} /> Crear código
        </button>
      </div>

      <div className={`${TARJETA} overflow-x-auto`}>
        {datos.cargado && datos.cupones.length === 0 ? (
          <div className="flex flex-col items-center px-6 py-12 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
              <IconoDescuento tamano={24} />
            </span>
            <p className="mt-3 font-medium text-[#101010]">Todavía no tienes códigos</p>
            <p className="mt-1 max-w-sm text-[13px] text-[#616161]">
              Por ejemplo "BIENVENIDA" con 10 % para la primera compra, o "ENVIO15" con un monto fijo.
            </p>
            <button onClick={() => setFormulario('nuevo')} className={`${BOTON} mt-4`}>Crear código</button>
          </div>
        ) : (
          <table className="min-w-full text-[13px]">
            <thead className={ENCABEZADO_TABLA}>
              <tr>
                <th className="px-4 py-2.5 font-medium">Código</th>
                <th className="px-4 py-2.5 font-medium">Descuento</th>
                <th className="px-4 py-2.5 font-medium">Condiciones</th>
                <th className="px-4 py-2.5 font-medium">Vigencia</th>
                <th className="px-4 py-2.5 text-right font-medium">Usos</th>
                <th className="px-4 py-2.5 font-medium">Estado</th>
                <th className="px-4 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-[#ebebeb]">
              {!datos.cargado && (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-[#616161]">Cargando...</td></tr>
              )}
              {datos.cupones.map((c) => (
                <tr key={c.idCupon} className="hover:bg-[#f7f7f7]">
                  <td className="px-4 py-3 font-mono font-semibold text-[#101010]">{c.codigo}</td>
                  <td className="whitespace-nowrap px-4 py-3 font-medium">{descuento(c)}</td>
                  <td className="px-4 py-3 text-[#616161]">
                    <p>{c.compraMinima ? `Desde ${formatoMoneda(c.compraMinima, moneda)}` : 'Sin compra mínima'}</p>
                    {c.unaVezPorCliente && <p>Una vez por cliente</p>}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-[#616161]">
                    <p>Desde {formatoFecha(c.fechaInicio)}</p>
                    <p>{c.fechaFin ? `Hasta ${formatoFecha(c.fechaFin)}` : 'Sin fecha de fin'}</p>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    {c.usos}{c.usosMaximos ? ` / ${c.usosMaximos}` : ''}
                  </td>
                  <td className="px-4 py-3">
                    <span className={etiqueta(ESTADOS[c.estado]?.tono)}>{ESTADOS[c.estado]?.texto ?? c.estado}</span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    <button onClick={() => setFormulario(c)} className="rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">Editar</button>
                    <button onClick={() => pausar(c)} className="ml-1 rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">
                      {c.activo ? 'Pausar' : 'Activar'}
                    </button>
                    {c.usos === 0 && (
                      <button onClick={() => setPorEliminar(c)} className="ml-1 rounded-lg px-2 py-1 font-medium text-[#c70a24] hover:bg-[#ffe7e6]">
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
      <p className="text-xs text-[#616161]">
        Un uso es un pedido pagado con el código. El envío gratis "desde" se calcula con el subtotal antes del código.
      </p>

      {formulario && (
        <FormularioCodigo
          cupon={formulario === 'nuevo' ? null : formulario}
          moneda={moneda}
          onCerrar={() => setFormulario(null)}
          onGuardar={async (cuerpo) => {
            if (formulario === 'nuevo') await crearCupon(idTienda, cuerpo);
            else await actualizarCupon(idTienda, formulario.idCupon, cuerpo);
            avisar(formulario === 'nuevo' ? 'Código creado' : 'Código guardado');
            setFormulario(null);
            setVersion((v) => v + 1);
          }}
        />
      )}

      {porEliminar && (
        <ConfirmarAccion
          titulo={`¿Eliminar el código ${porEliminar.codigo}?`}
          texto="Tus clientes ya no podrán usarlo."
          boton="Eliminar"
          peligro
          enviando={eliminando}
          onConfirmar={eliminar}
          onCancelar={() => setPorEliminar(null)}
        />
      )}
    </div>
  );
}

function FormularioCodigo({ cupon, moneda, onCerrar, onGuardar }) {
  const [d, setD] = useState({
    codigo: cupon?.codigo ?? '',
    tipoDescuento: cupon?.tipoDescuento ?? 'PORCENTAJE',
    valor: cupon ? String(Number(cupon.valor)) : '',
    compraMinima: cupon?.compraMinima != null ? String(Number(cupon.compraMinima)) : '',
    usosMaximos: cupon?.usosMaximos != null ? String(cupon.usosMaximos) : '',
    unaVezPorCliente: cupon?.unaVezPorCliente ?? false,
    inicio: aLocal(cupon?.fechaInicio),
    fin: aLocal(cupon?.fechaFin),
    activo: cupon ? cupon.activo : true,
  });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const porcentaje = d.tipoDescuento === 'PORCENTAJE';
  const cambiar = (campo) => (e) => setD((v) => ({ ...v, [campo]: e.target.value }));

  async function enviar(e) {
    e.preventDefault();
    const er = {};
    const codigo = d.codigo.trim().toUpperCase();
    const valor = d.valor.trim().replace(',', '.');
    const minima = d.compraMinima.trim().replace(',', '.');
    if (!/^[A-Z0-9_-]{3,40}$/.test(codigo)) er.codigo = 'De 3 a 40 letras, números, guiones o guiones bajos, sin espacios';
    if (porcentaje) {
      if (!/^\d+(\.\d{1,2})?$/.test(valor) || Number(valor) <= 0 || Number(valor) >= 100) {
        er.valor = 'Un porcentaje mayor que 0 y menor que 100';
      }
    } else if (!esMontoValido(valor, moneda) || Number(valor) <= 0) {
      er.valor = `Un monto mayor que 0, ${ejemploMonto(moneda)}`;
    }
    if (minima && (!esMontoValido(minima, moneda) || Number(minima) <= 0)) {
      er.compraMinima = `Un monto mayor que 0, ${ejemploMonto(moneda)}`;
    }
    if (d.usosMaximos.trim() && !/^[1-9]\d*$/.test(d.usosMaximos.trim())) er.usosMaximos = 'Un número entero mayor que 0';
    if (d.fin && d.inicio && new Date(d.fin) <= new Date(d.inicio)) er.fin = 'Debe ser posterior al inicio';
    if (d.fin && !d.inicio && new Date(d.fin) <= new Date()) er.fin = 'Debe ser una fecha futura';
    setErrores(er);
    if (Object.keys(er).length) return;

    setGuardando(true);
    setError('');
    try {
      await onGuardar({
        codigo,
        tipoDescuento: d.tipoDescuento,
        valor,
        compraMinima: minima || null,
        usosMaximos: d.usosMaximos.trim() ? Number(d.usosMaximos) : null,
        unaVezPorCliente: d.unaVezPorCliente,
        fechaInicio: d.inicio ? new Date(d.inicio).toISOString() : null,
        fechaFin: d.fin ? new Date(d.fin).toISOString() : null,
        activo: d.activo,
      });
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else if (ex.response?.status === 409) setErrores({ codigo: mensajeDeError(ex) });
      else setError(mensajeDeError(ex));
      setGuardando(false);
    }
  }

  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:py-12">
      <form onSubmit={enviar} noValidate className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-[#ebebeb] px-5 py-3.5">
          <h2 className="font-semibold text-[#101010]">{cupon ? 'Editar código' : 'Crear código'}</h2>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="rounded-lg p-1 text-[#616161] hover:bg-[#f1f1f1]">
            <IconoCerrar tamano={18} />
          </button>
        </div>
        <div className="space-y-3 p-5 text-[13px]">
          {error && <Mensaje tipo="error" texto={error} />}
          {cupon?.usos > 0 && (
            <Mensaje tipo="info" texto={`Ya se usó en ${cupon.usos} pedido(s). Los cambios valen para los pedidos nuevos; los pagados no cambian.`} />
          )}

          <CampoPanel etiqueta="Código" error={errores.codigo} ayuda="El cliente lo escribe en el checkout (mayúsculas o minúsculas da igual)">
            <input
              value={d.codigo}
              onChange={cambiar('codigo')}
              placeholder="VERANO10"
              className={`${inputPanel(errores.codigo)} font-mono uppercase`}
            />
          </CampoPanel>

          <div className="grid gap-3 sm:grid-cols-2">
            <CampoPanel etiqueta="Tipo de descuento">
              <select value={d.tipoDescuento} onChange={cambiar('tipoDescuento')} className={inputPanel()}>
                <option value="PORCENTAJE">Porcentaje (%)</option>
                <option value="MONTO_FIJO">Monto fijo ({simboloMoneda(moneda)})</option>
              </select>
            </CampoPanel>
            <CampoPanel etiqueta={porcentaje ? 'Porcentaje' : `Monto (${simboloMoneda(moneda)})`} error={errores.valor}>
              <input
                inputMode="decimal"
                value={d.valor}
                onChange={cambiar('valor')}
                placeholder={porcentaje ? '10' : montoVacio(moneda)}
                className={inputPanel(errores.valor)}
              />
            </CampoPanel>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <CampoPanel etiqueta={`Compra mínima (${simboloMoneda(moneda)}, opcional)`} error={errores.compraMinima} ayuda="Subtotal de los productos">
              <input inputMode="decimal" value={d.compraMinima} onChange={cambiar('compraMinima')} placeholder="Sin mínimo" className={inputPanel(errores.compraMinima)} />
            </CampoPanel>
            <CampoPanel etiqueta="Máximo de usos (opcional)" error={errores.usosMaximos} ayuda="Pedidos pagados en total">
              <input inputMode="numeric" value={d.usosMaximos} onChange={cambiar('usosMaximos')} placeholder="Sin límite" className={inputPanel(errores.usosMaximos)} />
            </CampoPanel>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <CampoPanel etiqueta="Empieza" ayuda={cupon ? undefined : 'Vacío = empieza ahora'}>
              <input type="datetime-local" value={d.inicio} onChange={cambiar('inicio')} className={inputPanel()} />
            </CampoPanel>
            <CampoPanel etiqueta="Termina (opcional)" error={errores.fin} ayuda="Vacío = sin fecha de fin">
              <input type="datetime-local" value={d.fin} onChange={cambiar('fin')} className={inputPanel(errores.fin)} />
            </CampoPanel>
          </div>

          <label className="flex items-center gap-2">
            <input type="checkbox" checked={d.unaVezPorCliente} onChange={(e) => setD((v) => ({ ...v, unaVezPorCliente: e.target.checked }))} />
            Una sola vez por cliente
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={d.activo} onChange={(e) => setD((v) => ({ ...v, activo: e.target.checked }))} />
            Código activo (desmárcalo para dejarlo pausado)
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
