import { useEffect, useState } from 'react';
import CampoPanel from '../components/CampoPanel';
import ConfirmarAccion from '../components/ConfirmarAccion';
import { IconoCerrar, IconoEnvio, IconoMas } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import {
  guardarTarifaInternacional, guardarTarifaNacional, listarPaises, obtenerEnvios, quitarTarifaInternacional,
} from '../services/envios';
import { formatoMoneda } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta, inputPanel } from '../utils/panel';

const ESTADO = {
  ACTIVA: ['Activa', 'exito'],
  PAUSADA: ['Pausada', 'aviso'],
  SIN_TARIFA: ['No envía', 'neutro'],
};

function textoDias(t) {
  if (t.diasEntregaMin == null && t.diasEntregaMax == null) return '-';
  if (t.diasEntregaMin === t.diasEntregaMax || t.diasEntregaMax == null) return `${t.diasEntregaMin} días`;
  if (t.diasEntregaMin == null) return `hasta ${t.diasEntregaMax} días`;
  return `${t.diasEntregaMin} a ${t.diasEntregaMax} días`;
}

/**
 * Envíos de la tienda, como "Envío y entrega" en Shopify:
 * - Nacionales: una tarifa por departamento del país de la tienda.
 * - Internacionales: los países a los que la tienda decide vender. Sin tarifa, no vende ahí:
 *   el cliente de ese país ve "Esta tienda no envía a ..." antes de pagar.
 */
export default function Envios() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const { avisar } = useToast();
  const [datos, setDatos] = useState({ idTienda: null, envios: null, error: '' });
  const [paises, setPaises] = useState([]);
  const [editando, setEditando] = useState(null);     // { tipo: 'nacional'|'internacional', tarifa }
  const [porQuitar, setPorQuitar] = useState(null);
  const [quitando, setQuitando] = useState(false);
  const [verTodas, setVerTodas] = useState(false);

  useEffect(() => {
    if (!idTienda) return undefined;
    let vigente = true;
    obtenerEnvios(idTienda)
      .then((envios) => vigente && setDatos({ idTienda, envios, error: '' }))
      .catch((e) => vigente && setDatos({ idTienda, envios: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda]);

  useEffect(() => {
    listarPaises().then(setPaises).catch(() => setPaises([]));
  }, []);

  const cargando = cargandoTiendas || (idTienda !== null && datos.idTienda !== idTienda);
  const envios = datos.envios;

  async function quitar() {
    setQuitando(true);
    try {
      const envios = await quitarTarifaInternacional(idTienda, porQuitar.idZona);
      setDatos({ idTienda, envios, error: '' });
      avisar(`Ya no envías a ${porQuitar.zona}`);
    } catch (e) {
      setDatos((d) => ({ ...d, error: mensajeDeError(e) }));
    } finally {
      setQuitando(false);
      setPorQuitar(null);
    }
  }

  if (errorTiendas) return <Mensaje tipo="error" texto={errorTiendas} />;

  const nacionales = envios?.nacionales ?? [];
  const activasNac = nacionales.filter((t) => t.estado === 'ACTIVA').length;
  const nacionalesVisibles = verTodas ? nacionales : nacionales.slice(0, 8);

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Envíos</h1>
          <p className="text-[13px] text-[#616161]">Dónde vendes y cuánto cobras por el envío.</p>
        </div>
        {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} onCerrar={() => setDatos((d) => ({ ...d, error: '' }))} />}
      {!cargandoTiendas && !tienda && <Mensaje tipo="info" texto="Primero crea una tienda." />}
      {cargando && tienda && <p className="text-[#616161]">Cargando envíos...</p>}

      {!cargando && envios && (
        <>
          {/* Nacionales */}
          <div className={TARJETA}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#ebebeb] px-4 py-3">
              <div>
                <h2 className="font-semibold text-[#101010]">Envíos nacionales · {envios.pais}</h2>
                <p className="text-[13px] text-[#616161]">
                  Envías a {activasNac} de {nacionales.length} regiones. Los montos van en {envios.moneda} e incluyen IGV.
                </p>
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-full text-[13px]">
                <thead className={ENCABEZADO_TABLA}>
                  <tr>
                    <th className="px-4 py-2.5 font-medium">Región</th>
                    <th className="px-4 py-2.5 text-right font-medium">Costo</th>
                    <th className="px-4 py-2.5 text-right font-medium">Gratis desde</th>
                    <th className="px-4 py-2.5 font-medium">Entrega</th>
                    <th className="px-4 py-2.5 font-medium">Estado</th>
                    <th className="px-4 py-2.5" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#ebebeb]">
                  {nacionalesVisibles.map((t) => (
                    <FilaTarifa key={t.idZona} tarifa={t} onEditar={() => setEditando({ tipo: 'nacional', tarifa: t })} />
                  ))}
                </tbody>
              </table>
            </div>
            {nacionales.length > 8 && (
              <button
                onClick={() => setVerTodas((v) => !v)}
                className="w-full border-t border-[#ebebeb] px-4 py-2.5 text-left text-[13px] text-[#1650d0] hover:bg-[#f7f7f7]"
              >
                {verTodas ? 'Ver menos' : `Ver las ${nacionales.length} regiones`}
              </button>
            )}
          </div>

          {/* Internacionales */}
          <div className={TARJETA}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#ebebeb] px-4 py-3">
              <div>
                <h2 className="font-semibold text-[#101010]">Envíos internacionales</h2>
                <p className="text-[13px] text-[#616161]">
                  Solo vendes a los países que agregues aquí. Las ventas al extranjero no llevan IGV.
                </p>
              </div>
              <button onClick={() => setEditando({ tipo: 'internacional', tarifa: null })} className={`${BOTON} inline-flex items-center gap-1`}>
                <IconoMas tamano={16} /> Agregar país
              </button>
            </div>
            {envios.internacionales.length === 0 ? (
              <div className="flex flex-col items-center px-6 py-10 text-center">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
                  <IconoEnvio tamano={24} />
                </span>
                <p className="mt-3 font-medium text-[#101010]">Todavía no vendes fuera de {envios.pais}</p>
                <p className="mt-1 max-w-sm text-[13px] text-[#616161]">
                  Agrega un país con su costo de envío y sus clientes podrán comprar en tu tienda.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-[13px]">
                  <thead className={ENCABEZADO_TABLA}>
                    <tr>
                      <th className="px-4 py-2.5 font-medium">País</th>
                      <th className="px-4 py-2.5 text-right font-medium">Costo</th>
                      <th className="px-4 py-2.5 text-right font-medium">Gratis desde</th>
                      <th className="px-4 py-2.5 font-medium">Entrega</th>
                      <th className="px-4 py-2.5 font-medium">Estado</th>
                      <th className="px-4 py-2.5" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#ebebeb]">
                    {envios.internacionales.map((t) => (
                      <FilaTarifa
                        key={t.idZona}
                        tarifa={t}
                        onEditar={() => setEditando({ tipo: 'internacional', tarifa: t })}
                        onQuitar={() => setPorQuitar(t)}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      {editando && (
        <FormularioTarifa
          tipo={editando.tipo}
          tarifa={editando.tarifa}
          moneda={envios?.moneda}
          paises={paises.filter(
            (p) => p.idPais !== envios?.idPais && !envios?.internacionales.some((t) => t.idZona === p.idPais),
          )}
          onCerrar={() => setEditando(null)}
          onGuardar={async (idZona, cuerpo) => {
            const nuevos = editando.tipo === 'nacional'
              ? await guardarTarifaNacional(idTienda, idZona, cuerpo)
              : await guardarTarifaInternacional(idTienda, idZona, cuerpo);
            setDatos({ idTienda, envios: nuevos, error: '' });
            setEditando(null);
            avisar('Tarifa guardada');
          }}
        />
      )}

      {porQuitar && (
        <ConfirmarAccion
          titulo={`¿Dejar de enviar a ${porQuitar.zona}?`}
          texto="Sus clientes ya no podrán comprar en tu tienda. Los pedidos que ya se hicieron no cambian."
          boton="Dejar de enviar"
          peligro
          enviando={quitando}
          onConfirmar={quitar}
          onCancelar={() => setPorQuitar(null)}
        />
      )}
    </section>
  );
}

function FilaTarifa({ tarifa: t, onEditar, onQuitar }) {
  const [texto, tono] = ESTADO[t.estado] ?? ESTADO.SIN_TARIFA;
  return (
    <tr className="hover:bg-[#f7f7f7]">
      <td className="px-4 py-2.5 font-medium text-[#101010]">{t.zona}</td>
      <td className="whitespace-nowrap px-4 py-2.5 text-right">
        {t.costo == null ? '-' : Number(t.costo) === 0 ? 'Gratis' : formatoMoneda(t.costo)}
      </td>
      <td className="whitespace-nowrap px-4 py-2.5 text-right text-[#616161]">
        {t.envioGratisDesde == null ? '-' : formatoMoneda(t.envioGratisDesde)}
      </td>
      <td className="whitespace-nowrap px-4 py-2.5 text-[#616161]">{textoDias(t)}</td>
      <td className="px-4 py-2.5"><span className={etiqueta(tono)}>{texto}</span></td>
      <td className="whitespace-nowrap px-4 py-2.5 text-right">
        <button onClick={onEditar} className="rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">
          {t.estado === 'SIN_TARIFA' ? 'Configurar' : 'Editar'}
        </button>
        {onQuitar && (
          <button onClick={onQuitar} className="ml-1 rounded-lg px-2 py-1 font-medium text-[#c70a24] hover:bg-[#ffe7e6]">
            Quitar
          </button>
        )}
      </td>
    </tr>
  );
}

const MONTO = /^\d+(\.\d{1,2})?$/;

/** Ventana para crear o editar una tarifa (de una región o de un país). */
function FormularioTarifa({ tipo, tarifa, moneda, paises, onCerrar, onGuardar }) {
  const nueva = !tarifa;
  const [d, setD] = useState({
    idPais: '',
    costo: tarifa?.costo != null ? String(tarifa.costo) : '',
    gratis: tarifa?.envioGratisDesde != null ? String(tarifa.envioGratisDesde) : '',
    diasMin: tarifa?.diasEntregaMin != null ? String(tarifa.diasEntregaMin) : '',
    diasMax: tarifa?.diasEntregaMax != null ? String(tarifa.diasEntregaMax) : '',
    activa: tarifa ? tarifa.estado !== 'PAUSADA' : true,
  });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const cambiar = (campo) => (e) => {
    setD((v) => ({ ...v, [campo]: e.target.value }));
    setErrores((er) => ({ ...er, [campo]: undefined }));
  };

  async function enviar(e) {
    e.preventDefault();
    const er = {};
    if (nueva && tipo === 'internacional' && !d.idPais) er.idPais = 'Elige un país';
    if (!MONTO.test(d.costo.trim())) er.costo = 'Escribe un monto (0 = gratis), con hasta 2 decimales';
    if (d.gratis.trim() && (!MONTO.test(d.gratis.trim()) || Number(d.gratis) <= 0)) er.gratis = 'Monto mayor que 0';
    if (d.diasMin.trim() && !/^\d{1,3}$/.test(d.diasMin.trim())) er.diasMin = 'Número de días';
    if (d.diasMax.trim() && !/^\d{1,3}$/.test(d.diasMax.trim())) er.diasMax = 'Número de días';
    if (!er.diasMin && !er.diasMax && d.diasMin.trim() && d.diasMax.trim() && Number(d.diasMin) > Number(d.diasMax)) {
      er.diasMax = 'Debe ser mayor o igual al mínimo';
    }
    setErrores(er);
    if (Object.keys(er).length) return;

    setGuardando(true);
    setError('');
    try {
      await onGuardar(nueva && tipo === 'internacional' ? d.idPais : tarifa.idZona, {
        costo: Number(d.costo),
        envioGratisDesde: d.gratis.trim() ? Number(d.gratis) : null,
        diasEntregaMin: d.diasMin.trim() ? Number(d.diasMin) : null,
        diasEntregaMax: d.diasMax.trim() ? Number(d.diasMax) : null,
        activa: d.activa,
      });
    } catch (ex) {
      setError(mensajeDeError(ex));
      setGuardando(false);
    }
  }

  const titulo = nueva ? 'Agregar país' : tarifa.zona;
  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:py-16">
      <form onSubmit={enviar} noValidate className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-[#ebebeb] px-5 py-3.5">
          <h2 className="font-semibold text-[#101010]">{titulo}</h2>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="rounded-lg p-1 text-[#616161] hover:bg-[#f1f1f1]">
            <IconoCerrar tamano={18} />
          </button>
        </div>
        <div className="space-y-3 p-5 text-[13px]">
          {error && <Mensaje tipo="error" texto={error} />}
          {nueva && tipo === 'internacional' && (
            <CampoPanel etiqueta="País" error={errores.idPais}>
              <select value={d.idPais} onChange={cambiar('idPais')} className={inputPanel(errores.idPais)}>
                <option value="">Elige un país</option>
                {paises.map((p) => (
                  <option key={p.idPais} value={p.idPais}>{p.nombre}</option>
                ))}
              </select>
            </CampoPanel>
          )}
          <div className="grid grid-cols-2 gap-3">
            <CampoPanel etiqueta={`Costo (${moneda})`} error={errores.costo} ayuda="0 = envío gratis">
              <input inputMode="decimal" value={d.costo} onChange={cambiar('costo')} placeholder="10.00" className={inputPanel(errores.costo)} />
            </CampoPanel>
            <CampoPanel etiqueta={`Gratis desde (${moneda})`} error={errores.gratis} ayuda="Opcional">
              <input inputMode="decimal" value={d.gratis} onChange={cambiar('gratis')} placeholder="200.00" className={inputPanel(errores.gratis)} />
            </CampoPanel>
            <CampoPanel etiqueta="Entrega: días mínimos" error={errores.diasMin}>
              <input inputMode="numeric" value={d.diasMin} onChange={cambiar('diasMin')} placeholder="1" className={inputPanel(errores.diasMin)} />
            </CampoPanel>
            <CampoPanel etiqueta="Días máximos" error={errores.diasMax}>
              <input inputMode="numeric" value={d.diasMax} onChange={cambiar('diasMax')} placeholder="5" className={inputPanel(errores.diasMax)} />
            </CampoPanel>
          </div>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={d.activa} onChange={(e) => setD((v) => ({ ...v, activa: e.target.checked }))} />
            Activa (si la desmarcas, se pausa: no se vende ahí por ahora)
          </label>
          {tipo === 'internacional' && (
            <p className="rounded-lg bg-[#f7f7f7] px-3 py-2 text-[12px] text-[#616161]">
              El cliente paga el producto y el envío; los impuestos de importación de su país los paga al recibir.
            </p>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-[#ebebeb] bg-[#f7f7f7] px-5 py-3">
          <button type="button" onClick={onCerrar} className={BOTON_SECUNDARIO}>Cancelar</button>
          <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : 'Guardar'}</button>
        </div>
      </form>
    </div>
  );
}
