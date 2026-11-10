import { useEffect, useState } from 'react';
import { mensajeDeError } from '../services/api';
import { listarMisTiendas } from '../services/tiendas';

const CLAVE = 'vendely_tienda';

/**
 * Elige en qué tienda trabaja el dueño.
 *
 * Por qué existe: las rutas del panel (/panel/clientes, /panel/productos…) no llevan
 * la tienda, pero la API sí la necesita (/api/panel/tiendas/{idTienda}/...) y un dueño
 * puede tener varias tiendas.
 * - Con una sola tienda, se elige sola.
 * - La elección se recuerda en el navegador para no pedirla en cada pantalla.
 * Se puede reutilizar en Productos y Ventas: <SelectorTienda onCambio={setTienda} />
 *
 * Avisa la tienda elegida (objeto completo: idTienda, nombre, idPais…) con onCambio.
 */
export default function SelectorTienda({ onCambio }) {
  const [tiendas, setTiendas] = useState([]);
  const [elegida, setElegida] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    listarMisTiendas()
      .then((lista) => {
        setTiendas(lista);
        // Se intenta usar la que quedó guardada; si ya no existe, la primera.
        const guardada = Number(localStorage.getItem(CLAVE));
        const inicial = lista.find((t) => t.idTienda === guardada) ?? lista[0] ?? null;
        setElegida(inicial);
        onCambio(inicial);
      })
      .catch((e) => setError(mensajeDeError(e)))
      .finally(() => setCargando(false));
    // Solo al montar: onCambio cambia en cada render del padre.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function cambiar(evento) {
    const tienda = tiendas.find((t) => t.idTienda === Number(evento.target.value)) ?? null;
    setElegida(tienda);
    if (tienda) {
      localStorage.setItem(CLAVE, String(tienda.idTienda));
    }
    onCambio(tienda);
  }

  if (cargando) {
    return <p className="text-sm text-slate-500">Cargando tus tiendas...</p>;
  }
  if (error) {
    return <p className="text-sm text-red-600">{error}</p>;
  }
  if (tiendas.length === 0) {
    return (
      <p className="rounded-md bg-amber-50 border border-amber-200 px-3 py-2 text-sm text-amber-800">
        Todavía no tienes tiendas. Crea una en «Mis tiendas» para gestionar sus clientes.
      </p>
    );
  }
  // Con una sola tienda no hay nada que elegir: solo se muestra su nombre.
  if (tiendas.length === 1) {
    return <p className="text-sm text-slate-600">Tienda: <span className="font-semibold">{elegida?.nombre}</span></p>;
  }
  return (
    <label className="flex items-center gap-2 text-sm text-slate-600">
      Tienda:
      <select
        value={elegida?.idTienda ?? ''}
        onChange={cambiar}
        className="rounded-md border border-slate-300 bg-white px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-violet-500"
      >
        {tiendas.map((t) => (
          <option key={t.idTienda} value={t.idTienda}>{t.nombre}</option>
        ))}
      </select>
    </label>
  );
}
