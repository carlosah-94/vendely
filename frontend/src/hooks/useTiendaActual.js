import { useEffect, useState } from 'react';
import { api, mensajeDeError } from '../services/api';

const CLAVE_TIENDA = 'vendely_tienda';

function leerGuardada() {
  try {
    return Number(localStorage.getItem(CLAVE_TIENDA));
  } catch {
    return null;
  }
}

/**
 * Las rutas del panel llevan el número de tienda (/panel/tiendas/{idTienda}/...),
 * porque un dueño puede tener varias. Este hook carga las tiendas del dueño y
 * decide con cuál se trabaja: la última elegida si sigue siendo suya, o la primera.
 *
 * Lo pueden usar todas las pantallas del panel (ventas, clientes, productos).
 */
export function useTiendaActual() {
  const [tiendas, setTiendas] = useState([]);
  const [idTienda, setIdTienda] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/panel/tiendas')
      .then(({ data }) => {
        setTiendas(data);
        const guardada = leerGuardada();
        // Si la guardada no es de este dueño (otra cuenta en el mismo navegador), se usa la primera.
        const elegida = data.find((t) => t.idTienda === guardada) ?? data[0];
        setIdTienda(elegida ? elegida.idTienda : null);
      })
      .catch((e) => setError(mensajeDeError(e)))
      .finally(() => setCargando(false));
  }, []);

  function cambiarTienda(id) {
    try {
      localStorage.setItem(CLAVE_TIENDA, String(id));
    } catch {
      // Si el navegador no deja guardar, solo se pierde la elección al recargar.
    }
    setIdTienda(id);
  }

  const tienda = tiendas.find((t) => t.idTienda === idTienda) ?? null;
  return { tiendas, tienda, idTienda, cambiarTienda, cargando, error };
}
