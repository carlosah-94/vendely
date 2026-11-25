import { useState } from 'react';
import BuscadorDistrito from './BuscadorDistrito';
import CampoPanel from './CampoPanel';
import { useToast } from '../context/ToastContext';
import { mensajeDeError } from '../services/api';
import { guardarRecojo, guardarZonaDelivery, quitarZonaDelivery } from '../services/envios';
import { ejemploMonto, esMontoValido, formatoMoneda } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, inputPanel } from '../utils/panel';

/**
 * Entregas sin courier (V18), como "Entrega local" y "Retiro en tienda" de Shopify:
 * - Delivery de la tienda: los distritos a los que el dueño lleva sus pedidos, con su costo.
 *   Pensado para la pastelería que reparte en su zona el mismo día.
 * - Recojo en el local: gratis para el cliente, con dirección y horario.
 * El cliente ve estas opciones en el checkout solo si aplican a su dirección.
 */
export default function EntregaLocal({ envios, idTienda, idPais, onActualizado }) {
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Delivery envios={envios} idTienda={idTienda} idPais={idPais} onActualizado={onActualizado} />
      <Recojo recojo={envios.recojo} idTienda={idTienda} onActualizado={onActualizado} />
    </div>
  );
}

function Delivery({ envios, idTienda, idPais, onActualizado }) {
  const { avisar } = useToast();
  const [nuevo, setNuevo] = useState({ idDivision: null, costo: '', gratis: '', clave: 0 });
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const zonas = envios.delivery ?? [];

  async function agregar(e) {
    e.preventDefault();
    if (!nuevo.idDivision) return setError('Elige un distrito de la lista');
    if (!esMontoValido(nuevo.costo, envios.moneda)) return setError(`Escribe el costo ${ejemploMonto(envios.moneda)}`);
    if (nuevo.gratis && !esMontoValido(nuevo.gratis, envios.moneda)) return setError('El monto para delivery gratis no es válido');
    setGuardando(true);
    setError('');
    try {
      const datos = await guardarZonaDelivery(idTienda, nuevo.idDivision, {
        costo: Number(nuevo.costo.replace(',', '.')),
        envioGratisDesde: nuevo.gratis ? Number(nuevo.gratis.replace(',', '.')) : null,
      });
      onActualizado(datos);
      avisar('Distrito agregado');
      // La clave nueva limpia el buscador de distritos.
      setNuevo((n) => ({ idDivision: null, costo: n.costo, gratis: n.gratis, clave: n.clave + 1 }));
    } catch (ex) {
      setError(mensajeDeError(ex));
    } finally {
      setGuardando(false);
    }
  }

  async function quitar(z) {
    try {
      onActualizado(await quitarZonaDelivery(idTienda, z.idDivision));
      avisar(`Ya no haces delivery a ${z.distrito}`);
    } catch (ex) {
      setError(mensajeDeError(ex));
    }
  }

  return (
    <div className={TARJETA}>
      <div className="border-b border-[#ebebeb] px-4 py-3">
        <h2 className="font-semibold text-[#101010]">Delivery de tu tienda</h2>
        <p className="text-[13px] text-[#616161]">
          Tú llevas el pedido, el mismo día. Ideal para comida, flores o pedidos en tu zona. Solo lo ven los clientes de estos distritos.
        </p>
      </div>
      {zonas.length > 0 ? (
        <table className="min-w-full text-[13px]">
          <thead className={ENCABEZADO_TABLA}>
            <tr>
              <th className="px-4 py-2 font-medium">Distrito</th>
              <th className="px-4 py-2 text-right font-medium">Costo</th>
              <th className="px-4 py-2 text-right font-medium">Gratis desde</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {zonas.map((z) => (
              <tr key={z.idDivision} className="border-t border-[#f1f1f1]">
                <td className="px-4 py-2">
                  {z.distrito}
                  {z.provincia && <span className="text-[#8a8a8a]"> · {z.provincia}</span>}
                </td>
                <td className="px-4 py-2 text-right">{formatoMoneda(z.costo, envios.moneda)}</td>
                <td className="px-4 py-2 text-right">{z.envioGratisDesde ? formatoMoneda(z.envioGratisDesde, envios.moneda) : '-'}</td>
                <td className="px-4 py-2 text-right">
                  <button type="button" onClick={() => quitar(z)} className="text-[12px] font-medium text-[#8e1f0b] hover:underline">
                    Quitar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="px-4 py-3 text-[13px] text-[#616161]">Todavía no haces delivery. Agrega el primer distrito.</p>
      )}
      <form onSubmit={agregar} noValidate className="space-y-3 border-t border-[#ebebeb] px-4 py-3 text-[13px]">
        <BuscadorDistrito key={nuevo.clave} idPais={idPais} onElegir={(id) => setNuevo((n) => ({ ...n, idDivision: id }))} />
        <div className="grid grid-cols-2 gap-3">
          <CampoPanel etiqueta={`Costo (${envios.moneda})`}>
            <input value={nuevo.costo} onChange={(e) => setNuevo((n) => ({ ...n, costo: e.target.value }))} inputMode="decimal" placeholder="7.00" className={inputPanel()} />
          </CampoPanel>
          <CampoPanel etiqueta="Gratis desde (opcional)">
            <input value={nuevo.gratis} onChange={(e) => setNuevo((n) => ({ ...n, gratis: e.target.value }))} inputMode="decimal" placeholder="100.00" className={inputPanel()} />
          </CampoPanel>
        </div>
        {error && <p className="text-[12px] text-[#8e1f0b]">{error}</p>}
        <div className="flex justify-end">
          <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : 'Agregar distrito'}</button>
        </div>
      </form>
    </div>
  );
}

function Recojo({ recojo, idTienda, onActualizado }) {
  const { avisar } = useToast();
  const [v, setV] = useState({
    activo: recojo?.activo ?? false,
    direccion: recojo?.direccion ?? '',
    horario: recojo?.horario ?? '',
  });
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  async function guardar(e) {
    e.preventDefault();
    if (v.activo && !v.direccion.trim()) return setError('Escribe la dirección donde tus clientes recogen sus pedidos');
    setGuardando(true);
    setError('');
    try {
      onActualizado(await guardarRecojo(idTienda, {
        activo: v.activo, direccion: v.direccion.trim() || null, horario: v.horario.trim() || null,
      }));
      avisar(v.activo ? 'Recojo en tienda activado' : 'Recojo en tienda desactivado');
    } catch (ex) {
      setError(mensajeDeError(ex));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={guardar} noValidate className={TARJETA}>
      <div className="flex items-start justify-between gap-3 border-b border-[#ebebeb] px-4 py-3">
        <div>
          <h2 className="font-semibold text-[#101010]">Recojo en tienda</h2>
          <p className="text-[13px] text-[#616161]">El cliente pasa a recoger su pedido. No paga envío.</p>
        </div>
        <label className="flex shrink-0 cursor-pointer items-center gap-2 text-[13px] font-medium">
          <input type="checkbox" checked={v.activo} onChange={(e) => setV({ ...v, activo: e.target.checked })} className="h-4 w-4 accent-[#303030]" />
          {v.activo ? 'Activo' : 'Inactivo'}
        </label>
      </div>
      <div className="space-y-3 px-4 py-3 text-[13px]">
        <CampoPanel etiqueta="Dirección del local">
          <input value={v.direccion} onChange={(e) => setV({ ...v, direccion: e.target.value })} placeholder="Av. Benavides 456, Miraflores" className={inputPanel()} />
        </CampoPanel>
        <CampoPanel etiqueta="Horario de atención (opcional)">
          <input value={v.horario} onChange={(e) => setV({ ...v, horario: e.target.value })} placeholder="Lunes a sábado de 10 a. m. a 7 p. m." className={inputPanel()} />
        </CampoPanel>
        {error && <p className="text-[12px] text-[#8e1f0b]">{error}</p>}
        <div className="flex justify-end gap-2">
          <button type="button" onClick={() => setV({ activo: recojo?.activo ?? false, direccion: recojo?.direccion ?? '', horario: recojo?.horario ?? '' })} className={BOTON_SECUNDARIO}>
            Descartar
          </button>
          <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : 'Guardar'}</button>
        </div>
      </div>
    </form>
  );
}
