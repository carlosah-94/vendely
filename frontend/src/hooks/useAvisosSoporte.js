import { useCallback, useEffect, useState } from 'react';
import { useTiempoReal } from './useTiempoReal';
import { obtenerBandeja, obtenerMensajes } from '../services/soporte';
import { canales } from '../services/tiempoReal';

/**
 * Avisos de la conversación con soporte de Vendely, para las dos partes:
 *
 *  - useNoLeidosSoporte (el dueño): cuántas respuestas de soporte no ha visto todavía.
 *  - useBandejaSoporte (el superadmin): por tienda, cuántos mensajes del dueño esperan respuesta.
 *
 * Los dos se actualizan en vivo por WebSocket y, por si la conexión se cae, también al volver a
 * la pestaña y cada cierto tiempo.
 */

const EVENTO_VISTO = 'vendely:soporte-visto';
const RESPALDO_MS = 60000;

function claveVisto(idTienda) {
  return `vendely_soporte_visto_${idTienda}`;
}

function leerVisto(idTienda) {
  try {
    const v = localStorage.getItem(claveVisto(idTienda));
    return v === null ? 0 : Number(v);
  } catch {
    return 0;
  }
}

/** El dueño abrió la conversación: lo que hay hasta idMensaje ya está visto. */
export function marcarSoporteVisto(idTienda, idMensaje) {
  if (!idTienda || !idMensaje || idMensaje <= leerVisto(idTienda)) return;
  try {
    localStorage.setItem(claveVisto(idTienda), String(idMensaje));
  } catch {
    // Sin almacenamiento solo se pierde el contador al recargar.
  }
  window.dispatchEvent(new CustomEvent(EVENTO_VISTO, { detail: { idTienda } }));
}

/** Vuelve a ejecutar "accion" al volver a la pestaña y cada RESPALDO_MS mientras se ve. */
function useRespaldo(accion, activo) {
  useEffect(() => {
    if (!activo) return undefined;
    const alVolver = () => document.visibilityState === 'visible' && accion();
    const reloj = setInterval(alVolver, RESPALDO_MS);
    window.addEventListener('focus', alVolver);
    document.addEventListener('visibilitychange', alVolver);
    return () => {
      clearInterval(reloj);
      window.removeEventListener('focus', alVolver);
      document.removeEventListener('visibilitychange', alVolver);
    };
  }, [accion, activo]);
}

/**
 * El dueño: respuestas de soporte que todavía no vio en esta tienda. alResponder(aviso) se llama
 * cuando soporte escribe en vivo (para mostrar un aviso).
 */
export function useNoLeidosSoporte(idTienda, activo, alResponder) {
  const [estado, setEstado] = useState({ idTienda: null, mensajes: [], visto: 0 });

  const cargar = useCallback(() => {
    if (!idTienda) return;
    obtenerMensajes(idTienda)
      .then((mensajes) => setEstado({ idTienda, mensajes, visto: leerVisto(idTienda) }))
      .catch(() => {});
  }, [idTienda]);

  useEffect(() => {
    if (activo) cargar();
  }, [activo, cargar]);

  useRespaldo(cargar, activo && Boolean(idTienda));

  useEffect(() => {
    function alVer(e) {
      if (e.detail?.idTienda === idTienda) setEstado((s) => ({ ...s, visto: leerVisto(idTienda) }));
    }
    window.addEventListener(EVENTO_VISTO, alVer);
    return () => window.removeEventListener(EVENTO_VISTO, alVer);
  }, [idTienda]);

  useTiempoReal(activo && idTienda ? canales.soporteDeTienda(idTienda) : null, (aviso) => {
    if (aviso.tipo !== 'MENSAJE') return;
    cargar();
    if (aviso.deSoporte) alResponder?.(aviso);
  });

  if (!activo || estado.idTienda !== idTienda) return 0;
  return estado.mensajes.filter((m) => m.deSoporte && m.idMensaje > estado.visto).length;
}

/**
 * El superadmin: la bandeja de soporte (por tienda con acceso, cuántos mensajes esperan
 * respuesta). alEscribir(aviso) se llama cuando un dueño escribe en vivo; alCambiarAcceso, cuando
 * un dueño da o quita el acceso (la lista de tiendas cambia).
 */
export function useBandejaSoporte(activo, { alEscribir, alCambiarAcceso } = {}) {
  const [bandeja, setBandeja] = useState([]);

  const cargar = useCallback(() => {
    obtenerBandeja().then(setBandeja).catch(() => {});
  }, []);

  useEffect(() => {
    if (activo) cargar();
  }, [activo, cargar]);

  useRespaldo(cargar, activo);

  useTiempoReal(activo ? canales.soporteEquipo : null, (aviso) => {
    cargar();
    if (aviso.tipo === 'MENSAJE' && !aviso.deSoporte) alEscribir?.(aviso);
    if (aviso.tipo === 'ACCESO') alCambiarAcceso?.(aviso);
  });

  const porTienda = Object.fromEntries(bandeja.map((b) => [b.idTienda, b]));
  const sinResponder = bandeja.reduce((total, b) => total + (b.sinResponder ?? 0), 0);
  return { bandeja, porTienda, sinResponder, recargar: cargar };
}
