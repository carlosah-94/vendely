import { useEffect, useState } from 'react';
import { buscarDistritos } from '../services/clientes';
import { estiloInput } from '../utils/estilos';
import Campo from './Campo';

// Un ejemplo conocido de cada país para el placeholder.
const EJEMPLO = { PE: 'Miraflores', CL: 'Providencia' };

/**
 * Elegir la división del último nivel escribiendo su nombre: el distrito en Perú ("mira" →
 * Miraflores), la comuna en Chile ("provi" → Providencia). Se usa la lista oficial (ubigeo en
 * Perú, CUT en Chile) porque el backend exige una división de esa lista. No importan las tildes.
 * Espera 300 ms después de la última tecla para no llamar al servidor en cada letra.
 */
export default function BuscadorDistrito({ idPais, etiqueta = 'Distrito', error, onElegir }) {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState([]);
  const [elegido, setElegido] = useState(null);

  useEffect(() => {
    if (elegido || texto.trim().length < 2) {
      return undefined;
    }
    const espera = setTimeout(() => {
      buscarDistritos(idPais, texto.trim())
        .then(setResultados)
        .catch(() => setResultados([]));
    }, 300);
    return () => clearTimeout(espera);
  }, [texto, idPais, elegido]);

  function elegir(distrito) {
    setElegido(distrito);
    setTexto(`${distrito.nombre}${distrito.provincia ? ` (${distrito.provincia})` : ''}`);
    setResultados([]);
    onElegir(distrito.idDistrito);
  }

  return (
    <div className="relative">
      <Campo etiqueta={etiqueta} error={error} ayuda="Escribe al menos 2 letras y elige de la lista">
        <input
          value={texto}
          onChange={(e) => {
            // Si cambia el texto después de elegir, hay que volver a elegir.
            setTexto(e.target.value);
            if (e.target.value.trim().length < 2) {
              setResultados([]);
            }
            if (elegido) {
              setElegido(null);
              onElegir(null);
            }
          }}
          placeholder={EJEMPLO[idPais] ?? ''}
          autoComplete="off"
          className={estiloInput(error)}
        />
      </Campo>
      {resultados.length > 0 && (
        <ul className="absolute z-30 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-slate-200 bg-white shadow-lg">
          {resultados.map((d) => (
            <li key={d.idDistrito}>
              <button
                type="button"
                onClick={() => elegir(d)}
                className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
              >
                {d.nombre} <span className="text-slate-500">{d.provincia ? `· ${d.provincia}` : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
