import { useEffect, useState } from 'react';
import { api, mensajeDeError } from '../services/api';
import { usarMoneda } from '../utils/formato';

const CLAVE_TIENDA = 'vendely_tienda';
/** Aviso para que el menú y la página elegida usen la misma tienda al cambiarla. */
const EVENTO_TIENDA = 'vendely:tienda-cambiada';
/** Aviso para que todas las listas de tiendas (menú, selector, páginas) se vuelvan a cargar. */
const EVENTO_RECARGAR = 'vendely:tiendas-recargar';

function leerGuardada() {
  try {
    return Number(localStorage.getItem(CLAVE_TIENDA));
  } catch {
    return null;
  }
}

/**
 * Las rutas del panel llevan el número de tienda (/panel/tiendas/{idTienda}/...),
 * porque un dueño puede tener varias (y un empleado puede trabajar en varias). Cada tienda trae
 * esDueno y permisos: lo que esta cuenta puede hacer en ella. Este hook carga las tiendas del dueño y
 * decide con cuál se trabaja: la última elegida si sigue siendo suya, o la primera.
 *
 * Lo pueden usar todas las pantallas del panel (ventas, clientes, productos).
 *
 * activo = false: no pide nada. Lo usa el marco del panel cuando entra el superadmin, que no
 * tiene tiendas: /api/panel/tiendas le responde 403 y lo mandaría a "Acceso denegado".
 */
export function useTiendaActual(activo = true) {
  const [tiendas, setTiendas] = useState([]);
  const [idTienda, setIdTienda] = useState(null);
  const [cargando, setCargando] = useState(activo);
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);   // sube al crear una tienda: se vuelve a cargar la lista

  useEffect(() => {
    if (!activo) {
      return;
    }
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
  }, [version, activo]);

  // Si otra parte del panel cambia la tienda (por ejemplo el selector de una página), el menú
  // también la cambia: así muestra las opciones que esa cuenta tiene en ESA tienda.
  useEffect(() => {
    const alCambiar = (e) => setIdTienda(e.detail);
    const alRecargar = () => setVersion((v) => v + 1);
    window.addEventListener(EVENTO_TIENDA, alCambiar);
    window.addEventListener(EVENTO_RECARGAR, alRecargar);
    return () => {
      window.removeEventListener(EVENTO_TIENDA, alCambiar);
      window.removeEventListener(EVENTO_RECARGAR, alRecargar);
    };
  }, []);

  function cambiarTienda(id) {
    try {
      localStorage.setItem(CLAVE_TIENDA, String(id));
    } catch {
      // Si el navegador no deja guardar, solo se pierde la elección al recargar.
    }
    setIdTienda(id);
    window.dispatchEvent(new CustomEvent(EVENTO_TIENDA, { detail: id }));
  }

  const tienda = tiendas.find((t) => t.idTienda === idTienda) ?? null;
  // Los montos de la pantalla (precios, envíos, ventas) se muestran en la moneda de esta tienda.
  usarMoneda(tienda?.moneda);
  // Al crear o editar una tienda se recargan TODAS las listas (también la del selector del menú).
  const recargar = () => window.dispatchEvent(new Event(EVENTO_RECARGAR));
  return { tiendas, tienda, idTienda, cambiarTienda, recargar, cargando, error };
}
