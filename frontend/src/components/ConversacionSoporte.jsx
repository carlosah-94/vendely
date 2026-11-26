import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import Mensaje from './Mensaje';
import { IconoArchivo, IconoCerrar, IconoClip, IconoDescargar } from './Iconos';
import { marcarSoporteVisto } from '../hooks/useAvisosSoporte';
import { useTiempoReal } from '../hooks/useTiempoReal';
import { mensajeDeError } from '../services/api';
import { EXTENSIONES_ADJUNTO, enviarAdjunto, enviarMensaje, obtenerMensajes, revisarAdjunto } from '../services/soporte';
import { canales } from '../services/tiempoReal';
import { formatoFecha } from '../utils/formato';
import { BOTON, TARJETA, inputPanel } from '../utils/panel';

const MAXIMO = 2000;
const RESPALDO_MS = 20000;
/** A menos de esto del final, se considera que la persona está leyendo lo último. */
const CERCA_DEL_FINAL = 80;
const ACEPTA = EXTENSIONES_ADJUNTO.map((e) => `.${e}`).join(',');

/** 2048 → "2 KB", 3_500_000 → "3,3 MB". */
function tamano(bytes) {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString('es-PE', { maximumFractionDigits: 1 })} MB`;
}

/**
 * La conversación entre el dueño y soporte de Vendely sobre lo que hay que armar en la tienda.
 * La usan las dos partes: el dueño en «Soporte de Vendely» y el superadmin en «Soporte a tiendas».
 *
 * Se actualiza sola: lo que escribe el otro llega en vivo (WebSocket) y, por si la conexión se
 * cae, también al volver a la pestaña y cada 20 s. Lo que escribes aparece al instante, antes de
 * que responda el servidor.
 *
 * Cada mensaje puede llevar un archivo (V26): el Excel de productos, las fotos, un PDF... Se
 * elige con el clip o arrastrándolo sobre la conversación.
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
  const [archivo, setArchivo] = useState(null);
  const [progreso, setProgreso] = useState(null);     // 0..100 mientras sube un archivo
  const [arrastrando, setArrastrando] = useState(false);
  const [errorEnvio, setErrorEnvio] = useState('');
  const caja = useRef(null);
  const selector = useRef(null);
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

  function elegir(elegido) {
    if (!elegido) return;
    const problema = revisarAdjunto(elegido);
    if (problema) {
      setErrorEnvio(problema);
      return;
    }
    setErrorEnvio('');
    setArchivo(elegido);
  }

  async function enviar(e) {
    e?.preventDefault();
    const limpio = texto.trim();
    if ((!limpio && !archivo) || progreso !== null) return;
    const adjunto = archivo;
    const temporal = {
      idMensaje: `tmp-${Date.now()}`, deSoporte: soyDeSoporte, texto: limpio, fecha: new Date().toISOString(), pendiente: true,
      adjunto: adjunto ? { nombre: adjunto.name, bytes: adjunto.size, esImagen: false } : null,
    };
    setPendientes((p) => [...p, temporal]);
    setTexto('');
    setArchivo(null);
    setErrorEnvio('');
    pegadoAlFinal.current = true;
    try {
      if (adjunto) {
        setProgreso(0);
        recibir(await enviarAdjunto(idTienda, adjunto, limpio, setProgreso));
      } else {
        recibir(await enviarMensaje(idTienda, limpio));
      }
    } catch (err) {
      setErrorEnvio(mensajeDeError(err));
      // Se devuelve todo a la caja para no perderlo.
      setTexto((t) => t || limpio);
      if (adjunto) setArchivo((a) => a ?? adjunto);
    } finally {
      setProgreso(null);
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

  // Arrastrar un archivo sobre la conversación lo adjunta.
  const arrastre = puedeEscribir ? {
    onDragOver: (e) => {
      if (!e.dataTransfer.types.includes('Files')) return;
      e.preventDefault();
      setArrastrando(true);
    },
    onDragLeave: (e) => {
      if (!e.currentTarget.contains(e.relatedTarget)) setArrastrando(false);
    },
    onDrop: (e) => {
      e.preventDefault();
      setArrastrando(false);
      elegir(e.dataTransfer.files?.[0]);
    },
  } : {};

  const cargando = estado.idTienda !== idTienda;
  const otro = soyDeSoporte ? 'El dueño' : 'Soporte de Vendely';
  const subiendo = progreso !== null;

  return (
    <div className={`relative ${compacto ? '' : TARJETA}`} {...arrastre}>
      {arrastrando && (
        <div className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center rounded-xl border-2 border-dashed border-[#303030] bg-white/85 text-[14px] font-semibold text-[#303030]">
          Suelta el archivo para adjuntarlo
        </div>
      )}

      {!compacto && (
        <div className="border-b border-[#ebebeb] px-4 py-3">
          <h2 className="font-semibold text-[#101010]">{titulo ?? 'Mensajes'}</h2>
          <p className="text-[12px] text-[#616161]">
            {soyDeSoporte
              ? 'Lo que pidió el dueño. Al responder, le llega un correo.'
              : 'Explica qué quieres en tu tienda y adjunta tu Excel o tus fotos. Al equipo le llega un aviso al instante, y su respuesta aparece aquí sola y en tu correo.'}
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
                <div className={`max-w-[85%] rounded-xl px-3.5 py-2.5 text-[13px] ${mio ? 'bg-[#303030] text-white' : 'bg-[#f1f1f1] text-[#303030]'} ${m.pendiente ? 'opacity-70' : ''}`}>
                  <p className={`mb-0.5 text-[11px] ${mio ? 'text-[#d4d4d4]' : 'text-[#616161]'}`}>
                    {mio ? 'Tú' : otro} · {m.pendiente
                      ? (m.adjunto && subiendo ? `Subiendo ${progreso}%...` : 'Enviando...')
                      : formatoFecha(m.fecha)}
                  </p>
                  {m.adjunto && <Adjunto adjunto={m.adjunto} mio={mio} pendiente={m.pendiente} />}
                  {m.texto && <p className={`whitespace-pre-wrap break-words ${m.adjunto ? 'mt-2' : ''}`}>{m.texto}</p>}
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
            {archivo && (
              <div className="flex items-center gap-2.5 rounded-lg border border-[#e3e3e3] bg-[#fafafa] px-3 py-2 text-[13px]">
                <span className="text-[#616161]"><IconoArchivo tamano={18} /></span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium text-[#303030]">{archivo.name}</span>
                  <span className="block text-[11px] text-[#8a8a8a]">{tamano(archivo.size)} · se envía con tu mensaje</span>
                </span>
                <button
                  type="button"
                  onClick={() => setArchivo(null)}
                  className="rounded-md p-1 text-[#616161] hover:bg-[#ebebeb]"
                  aria-label={`Quitar ${archivo.name}`}
                >
                  <IconoCerrar tamano={16} />
                </button>
              </div>
            )}
            <textarea
              value={texto}
              onChange={(e) => setTexto(e.target.value.slice(0, MAXIMO))}
              onKeyDown={alTeclear}
              rows={3}
              placeholder={soyDeSoporte
                ? 'Responde al dueño...'
                : 'Ej.: Quiero subir 20 polos con tallas S, M y L. Te adjunto el Excel con los precios.'}
              className={`${inputPanel(false)} w-full resize-y`}
            />
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-2">
                <input
                  ref={selector}
                  type="file"
                  accept={ACEPTA}
                  className="hidden"
                  onChange={(e) => {
                    elegir(e.target.files?.[0]);
                    e.target.value = '';   // para poder elegir el mismo archivo otra vez
                  }}
                />
                <button
                  type="button"
                  onClick={() => selector.current?.click()}
                  disabled={subiendo}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-[#d4d4d4] bg-white px-2.5 py-1.5 text-[13px] font-medium text-[#303030] hover:bg-[#f7f7f7] disabled:opacity-60"
                  title="Excel, CSV, PDF, Word, TXT, fotos o ZIP, hasta 10 MB"
                >
                  <IconoClip tamano={16} />
                  Adjuntar
                </button>
                <span className="hidden truncate text-[11px] text-[#8a8a8a] sm:inline">
                  {texto.length}/{MAXIMO} · Enter envía · No envíes contraseñas ni datos de pago
                </span>
              </div>
              <button type="submit" disabled={(!texto.trim() && !archivo) || subiendo} className={`${BOTON} text-[13px]`}>
                {subiendo ? `Subiendo ${progreso}%` : 'Enviar mensaje'}
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

/** El archivo de un mensaje: las fotos en miniatura; el resto, una tarjeta para descargar. */
function Adjunto({ adjunto, mio, pendiente }) {
  if (adjunto.esImagen && adjunto.url) {
    return (
      <a href={adjunto.url} target="_blank" rel="noreferrer" className="block" title={`Abrir ${adjunto.nombre}`}>
        <img
          src={adjunto.url}
          alt={adjunto.nombre}
          loading="lazy"
          className="max-h-56 max-w-full rounded-lg bg-white object-contain"
        />
      </a>
    );
  }
  const contenido = (
    <>
      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${mio ? 'bg-[#454545]' : 'bg-white'}`}>
        <IconoArchivo tamano={18} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-medium">{adjunto.nombre}</span>
        <span className={`block text-[11px] ${mio ? 'text-[#d4d4d4]' : 'text-[#8a8a8a]'}`}>{tamano(adjunto.bytes)}</span>
      </span>
      {!pendiente && <IconoDescargar tamano={18} />}
    </>
  );
  const clase = `flex min-w-[14rem] items-center gap-2.5 rounded-lg border px-2.5 py-2 ${mio ? 'border-[#4a4a4a]' : 'border-[#e0e0e0]'}`;
  if (pendiente || !adjunto.url) {
    return <div className={clase}>{contenido}</div>;
  }
  return (
    <a href={adjunto.url} target="_blank" rel="noreferrer" download={adjunto.nombre}
      className={`${clase} ${mio ? 'hover:bg-[#3a3a3a]' : 'hover:bg-white'}`} title={`Descargar ${adjunto.nombre}`}>
      {contenido}
    </a>
  );
}
