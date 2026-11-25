import { useEffect, useRef, useState } from 'react';
import Mensaje from './Mensaje';
import { mensajeDeError } from '../services/api';
import { enviarMensaje, obtenerMensajes } from '../services/soporte';
import { formatoFecha } from '../utils/formato';
import { BOTON, TARJETA, inputPanel } from '../utils/panel';

const MAXIMO = 2000;

/**
 * La conversación entre el dueño y soporte de Vendely sobre lo que hay que armar en la tienda.
 * La usan las dos partes: el dueño en «Soporte de Vendely» y el superadmin en «Soporte a tiendas».
 *
 * soyDeSoporte: quien mira es soporte (sus mensajes van a la derecha).
 * puedeEscribir: false muestra "bloqueado" en lugar de la caja de texto.
 */
export default function ConversacionSoporte({ idTienda, soyDeSoporte = false, puedeEscribir = true, bloqueado, titulo, compacto = false }) {
  const [estado, setEstado] = useState({ idTienda: null, mensajes: [], error: '' });
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [errorEnvio, setErrorEnvio] = useState('');
  const final = useRef(null);

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true;
    obtenerMensajes(idTienda)
      .then((mensajes) => vigente && setEstado({ idTienda, mensajes, error: '' }))
      .catch((e) => vigente && setEstado({ idTienda, mensajes: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda]);

  useEffect(() => {
    final.current?.scrollIntoView({ block: 'nearest' });
  }, [estado.mensajes.length]);

  async function enviar(e) {
    e.preventDefault();
    if (!texto.trim()) {
      return;
    }
    setEnviando(true);
    setErrorEnvio('');
    try {
      const mensajes = await enviarMensaje(idTienda, texto.trim());
      setEstado({ idTienda, mensajes, error: '' });
      setTexto('');
    } catch (err) {
      setErrorEnvio(mensajeDeError(err));
    } finally {
      setEnviando(false);
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
              : 'Explica qué quieres en tu tienda. Al equipo le llega un correo, y su respuesta te llega aquí y a tu correo.'}
          </p>
        </div>
      )}

      <div className="max-h-96 space-y-3 overflow-y-auto px-4 py-4">
        {cargando ? (
          <p className="text-[13px] text-[#616161]">Cargando...</p>
        ) : estado.error ? (
          <Mensaje tipo="error" texto={estado.error} />
        ) : estado.mensajes.length === 0 ? (
          <p className="text-[13px] text-[#8a8a8a]">Todavía no hay mensajes.</p>
        ) : (
          estado.mensajes.map((m) => {
            const mio = m.deSoporte === soyDeSoporte;
            return (
              <div key={m.idMensaje} className={`flex ${mio ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[85%] rounded-xl px-3.5 py-2.5 text-[13px] ${mio ? 'bg-[#303030] text-white' : 'bg-[#f1f1f1] text-[#303030]'}`}>
                  <p className={`mb-0.5 text-[11px] ${mio ? 'text-[#d4d4d4]' : 'text-[#616161]'}`}>
                    {mio ? 'Tú' : otro} · {formatoFecha(m.fecha)}
                  </p>
                  <p className="whitespace-pre-wrap break-words">{m.texto}</p>
                </div>
              </div>
            );
          })
        )}
        <div ref={final} />
      </div>

      <div className="border-t border-[#ebebeb] px-4 py-3">
        {puedeEscribir ? (
          <form onSubmit={enviar} className="space-y-2">
            {errorEnvio && <Mensaje tipo="error" texto={errorEnvio} onCerrar={() => setErrorEnvio('')} />}
            <textarea
              value={texto}
              onChange={(e) => setTexto(e.target.value.slice(0, MAXIMO))}
              rows={3}
              placeholder={soyDeSoporte ? 'Responde al dueño...' : 'Ej.: Quiero subir 20 polos con tallas S, M y L. Las fotos están en este enlace...'}
              className={`${inputPanel(false)} w-full resize-y`}
            />
            <div className="flex items-center justify-between gap-3">
              <span className="text-[11px] text-[#8a8a8a]">{texto.length}/{MAXIMO}</span>
              <button type="submit" disabled={enviando || !texto.trim()} className={`${BOTON} text-[13px]`}>
                {enviando ? 'Enviando...' : 'Enviar mensaje'}
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
