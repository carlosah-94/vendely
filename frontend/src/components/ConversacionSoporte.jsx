import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import Mensaje from './Mensaje';
import { marcarSoporteVisto } from '../hooks/useAvisosSoporte';
import { useTiempoReal } from '../hooks/useTiempoReal';
import { mensajeDeError } from '../services/api';
import { enviarMensaje, obtenerMensajes } from '../services/soporte';
import { canales } from '../services/tiempoReal';
import { formatoFecha } from '../utils/formato';
import { BOTON, TARJETA, inputPanel } from '../utils/panel';

const MAXIMO = 2000;
const RESPALDO_MS = 20000;
/** A menos de esto del final, se considera que la persona está leyendo lo último. */
const CERCA_DEL_FINAL = 80;

/**
 * La conversación entre el dueño y soporte de Vendely sobre lo que hay que armar en la tienda.
 * La usan las dos partes: el dueño en «Soporte de Vendely» y el superadmin en «Soporte a tiendas».
 *
 * Se actualiza sola: lo que escribe el otro llega en vivo (WebSocket) y, por si la conexión se
 * cae, también al volver a la pestaña y cada 20 s. Lo que escribes aparece al instante, antes de
 * que responda el servidor.
 *
 * soyDeSoporte: quien mira es soporte (sus mensajes van a la derecha).
 * puedeEscribir: false muestra "bloqueado" en lugar de la caja de texto.
 * alCambiar: se llama cada vez que llega la conversación (para refrescar contadores afuera).
 */
export default function ConversacionSoporte({
  idTienda, soyDeSoporte = false, puedeEscribir = true, bloqueado, titulo, compacto = false, alCambiar,
}) {
  const [estado, setEstado] = useState({ idTienda: null, mensajes: [], error: '' });
  const [pendientes, setPendientes] = useState([]);   // enviados que el servidor todavía no confirma
  const [texto, setTexto] = useState('');
  const [errorEnvio, setErrorEnvio] = useState('');
  const caja = useRef(null);
  const pegadoAlFinal = useRef(true);
  const alCambiarRef = useRef(alCambiar);

  useEffect(() => {
    alCambiarRef.current = alCambiar;
  });

  const recibir = useCallback((mensajes) => {
    setEstado({ idTienda, mensajes, error: '' });
    if (!soyDeSoporte) marcarSoporteVisto(idTienda, mensajes.at(-1)?.idMensaje);
    alCambiarRef.current?.(mensajes);
  }, [idTienda, soyDeSoporte]);

  const cargar = useCallback(({ silencioso = false } = {}) => {
    if (!idTienda) return Promise.resolve();
    return obtenerMensajes(idTienda)
      .then(recibir)
      .catch((e) => {
        // Si falla una recarga de fondo, se deja lo que ya se veía.
        if (!silencioso) setEstado({ idTienda, mensajes: [], error: mensajeDeError(e) });
      });
  }, [idTienda, recibir]);

  useEffect(() => {
    pegadoAlFinal.current = true;
    cargar();
  }, [cargar]);

  // En vivo: el otro escribió.
  useTiempoReal(idTienda ? canales.soporteDeTienda(idTienda) : null, (aviso) => {
    if (aviso.tipo === 'MENSAJE') cargar({ silencioso: true });
  });

  // Respaldo por si el WebSocket no está: al volver a la pestaña y cada cierto tiempo.
  useEffect(() => {
    const alVolver = () => document.visibilityState === 'visible' && cargar({ silencioso: true });
    const reloj = setInterval(alVolver, RESPALDO_MS);
    window.addEventListener('focus', alVolver);
    return () => {
      clearInterval(reloj);
      window.removeEventListener('focus', alVolver);
    };
  }, [cargar]);

  const mensajes = [...estado.mensajes, ...pendientes];

  // Baja al último mensaje si la persona ya estaba abajo (no la mueve si está leyendo más arriba).
  useLayoutEffect(() => {
    const el = caja.current;
    if (el && pegadoAlFinal.current) el.scrollTop = el.scrollHeight;
  }, [mensajes.length, estado.idTienda]);

  function alDesplazar() {
    const el = caja.current;
    if (el) pegadoAlFinal.current = el.scrollHeight - el.scrollTop - el.clientHeight < CERCA_DEL_FINAL;
  }

  async function enviar(e) {
    e?.preventDefault();
    const limpio = texto.trim();
    if (!limpio) return;
    const temporal = { idMensaje: `tmp-${Date.now()}`, deSoporte: soyDeSoporte, texto: limpio, fecha: new Date().toISOString(), pendiente: true };
    setPendientes((p) => [...p, temporal]);
    setTexto('');
    setErrorEnvio('');
    pegadoAlFinal.current = true;
    try {
      recibir(await enviarMensaje(idTienda, limpio));
    } catch (err) {
      setErrorEnvio(mensajeDeError(err));
      setTexto((t) => t || limpio);   // se devuelve a la caja para no perderlo
    } finally {
      setPendientes((p) => p.filter((m) => m.idMensaje !== temporal.idMensaje));
    }
  }

  // Enter envía; Shift+Enter hace un salto de línea (como en cualquier chat).
  function alTeclear(e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      enviar();
    }
  }

  const cargando = estado.idTienda !== idTienda;
  const otro = soyDeSoporte ? 'El dueño' : 'Soporte de Vendely';

  return (
    <div className={compacto ? '' : TARJETA}>
      {!compacto && (
        <div className="border-b border-[#ebebeb] px-4 py-3">
          <h2 className="font-semibold text-[#101010]">{titulo ?? 'Mensajes'}</h2>
          <p className="text-[12px] text-[#616161]">
            {soyDeSoporte
              ? 'Lo que pidió el dueño. Al responder, le llega un correo.'
              : 'Explica qué quieres en tu tienda. Al equipo le llega un aviso al instante, y su respuesta aparece aquí sola y en tu correo.'}
          </p>
        </div>
      )}

      <div ref={caja} onScroll={alDesplazar} className="max-h-96 space-y-3 overflow-y-auto px-4 py-4" aria-live="polite">
        {cargando ? (
          <p className="text-[13px] text-[#616161]">Cargando...</p>
        ) : estado.error ? (
          <Mensaje tipo="error" texto={estado.error} />
        ) : mensajes.length === 0 ? (
          <p className="text-[13px] text-[#8a8a8a]">Todavía no hay mensajes.</p>
        ) : (
          mensajes.map((m) => {
            const mio = m.deSoporte === soyDeSoporte;
            return (
              <div key={m.idMensaje} className={`flex ${mio ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[85%] rounded-xl px-3.5 py-2.5 text-[13px] ${mio ? 'bg-[#303030] text-white' : 'bg-[#f1f1f1] text-[#303030]'} ${m.pendiente ? 'opacity-60' : ''}`}>
                  <p className={`mb-0.5 text-[11px] ${mio ? 'text-[#d4d4d4]' : 'text-[#616161]'}`}>
                    {mio ? 'Tú' : otro} · {m.pendiente ? 'Enviando...' : formatoFecha(m.fecha)}
                  </p>
                  <p className="whitespace-pre-wrap break-words">{m.texto}</p>
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="border-t border-[#ebebeb] px-4 py-3">
        {puedeEscribir ? (
          <form onSubmit={enviar} className="space-y-2">
            {errorEnvio && <Mensaje tipo="error" texto={errorEnvio} onCerrar={() => setErrorEnvio('')} />}
            <textarea
              value={texto}
              onChange={(e) => setTexto(e.target.value.slice(0, MAXIMO))}
              onKeyDown={alTeclear}
              rows={3}
              placeholder={soyDeSoporte ? 'Responde al dueño...' : 'Ej.: Quiero subir 20 polos con tallas S, M y L. Las fotos están en este enlace...'}
              className={`${inputPanel(false)} w-full resize-y`}
            />
            <div className="flex items-center justify-between gap-3">
              <span className="text-[11px] text-[#8a8a8a]">
                {texto.length}/{MAXIMO} · Enter para enviar, Shift+Enter para otra línea
              </span>
              <button type="submit" disabled={!texto.trim()} className={`${BOTON} text-[13px]`}>
                Enviar mensaje
              </button>
            </div>
          </form>
        ) : (
          <p className="text-[12px] text-[#8a8a8a]">{bloqueado}</p>
        )}
      </div>
    </div>
  );
}
