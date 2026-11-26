import { Client } from '@stomp/stompjs';
import { tokenStorage, URL_API } from './api';

/**
 * La conexión de tiempo real con el backend (issue #4): WebSocket con STOMP en /ws.
 *
 * Hay UNA sola conexión por pestaña, compartida por todas las pantallas. Cada pantalla se
 * suscribe a los canales que le importan y se desuscribe al cerrarse; cuando no queda ninguna,
 * la conexión se cierra.
 *
 * Tres cosas que resuelve sola, para que las pantallas no tengan que pensar en ellas:
 *  - Reconexión: si se corta (wifi, el servidor se reinicia, Render despierta), reintenta cada
 *    5 segundos y vuelve a suscribir todos los canales.
 *  - Sesión: el token viaja en el CONNECT (el navegador no deja mandar cabeceras al abrir un
 *    WebSocket). Si cambia la sesión (login, logout), se reconecta con la nueva identidad.
 *  - Latidos cada 10 s, para detectar una conexión muerta y que ningún proxy la cierre.
 */

/** http://localhost:8080/api → ws://localhost:8080/ws · https://x.onrender.com/api → wss://x.onrender.com/ws */
function direccionWebSocket() {
  const url = new URL(URL_API, window.location.origin);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = `${url.pathname.replace(/\/api\/?$/, '')}/ws`;
  url.search = '';
  return url.toString();
}

const suscripciones = new Map();   // id → { destino, alRecibir, stomp }
let siguienteId = 1;
let cliente = null;
let tokenDeLaConexion = null;

function crearCliente() {
  const nuevo = new Client({
    brokerURL: direccionWebSocket(),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // Se lee el token en cada intento: si el usuario inició o cerró sesión entre medio,
    // el siguiente intento ya usa el que corresponde.
    beforeConnect: () => {
      tokenDeLaConexion = tokenStorage.get();
      nuevo.connectHeaders = tokenDeLaConexion ? { Authorization: `Bearer ${tokenDeLaConexion}` } : {};
    },
    onConnect: () => {
      for (const s of suscripciones.values()) abrir(s);
    },
    // Un canal sin permiso o una sesión vencida: el backend responde con un ERROR. Queda en la
    // consola para quien desarrolla; la pantalla sigue funcionando, solo que sin avisos en vivo.
    onStompError: (trama) => console.warn('Tiempo real:', trama.headers.message),
  });
  nuevo.activate();
  return nuevo;
}

function abrir(s) {
  try {
    s.stomp = cliente.subscribe(s.destino, (mensaje) => {
      try {
        s.alRecibir(JSON.parse(mensaje.body));
      } catch {
        // un aviso que no se puede leer no debe romper la pantalla
      }
    });
  } catch {
    s.stomp = null;   // se vuelve a intentar en la próxima conexión
  }
}

/**
 * Escucha un canal. Devuelve la función para dejar de escucharlo (la que usa el useEffect).
 * Los canales los decide el backend (Canales.java): uno sin permiso simplemente no recibe nada.
 */
export function suscribir(destino, alRecibir) {
  // La sesión cambió desde que se abrió la conexión: se abre otra con la identidad nueva.
  if (cliente && tokenStorage.get() !== tokenDeLaConexion) {
    cliente.deactivate();
    cliente = null;
  }
  if (!cliente) {
    cliente = crearCliente();
  }
  const id = siguienteId++;
  const s = { destino, alRecibir, stomp: null };
  suscripciones.set(id, s);
  if (cliente.connected) abrir(s);

  return () => {
    try {
      s.stomp?.unsubscribe();
    } catch {
      // la conexión ya estaba cerrada
    }
    suscripciones.delete(id);
    if (suscripciones.size === 0 && cliente) {
      cliente.deactivate();
      cliente = null;
    }
  };
}

/** Los canales, escritos en un solo lugar (los mismos de Canales.java en el backend). */
export const canales = {
  pedidosDeTienda: (idTienda) => `/topic/tiendas/${idTienda}/pedidos`,
  inventarioDeTienda: (idTienda) => `/topic/tiendas/${idTienda}/inventario`,
  stockDeTienda: (idTienda) => `/topic/tiendas/${idTienda}/stock`,
  soporteDeTienda: (idTienda) => `/topic/tiendas/${idTienda}/soporte`,
  soporteEquipo: '/topic/soporte',
  misPedidos: '/user/queue/pedidos',
};
