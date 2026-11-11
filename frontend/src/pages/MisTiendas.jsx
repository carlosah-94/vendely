import { useState } from 'react';
import CampoPanel from '../components/CampoPanel';
import Mensaje from '../components/Mensaje';
import { IconoCerrar, IconoCopiar, IconoEnlaceExterno, IconoMas, IconoTienda } from '../components/Iconos';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { NOMBRE_MONEDA, usePaisesHabilitados } from '../hooks/usePaisesHabilitados';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { BOTON, BOTON_SECUNDARIO, inputPanel } from '../utils/panel';

/** Convierte un nombre en un subdominio válido: "Moda Santiago" → "moda-santiago". */
function aSubdominio(texto) {
  return texto
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 63);
}

/**
 * Mis tiendas: por cada una, su país (moneda e impuesto) y el enlace público para compartir.
 * Desde aquí se crea otra tienda, en Perú o en Chile, como "Agregar tienda" en Shopify.
 */
export default function MisTiendas() {
  const { tiendas, cambiarTienda, recargar, cargando, error } = useTiendaActual();
  const { avisar } = useToast();
  const [copiada, setCopiada] = useState(null);
  const [creando, setCreando] = useState(false);

  async function copiar(idTienda, enlace) {
    try {
      await navigator.clipboard.writeText(enlace);
      setCopiada(idTienda);
      avisar('Enlace copiado');
      setTimeout(() => setCopiada(null), 2000);
    } catch {
      avisar('No se pudo copiar: selecciona el enlace y cópialo a mano');
    }
  }

  if (error) {
    return <Mensaje tipo="error" texto={error} />;
  }

  return (
    <section className="max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold text-[#101010]">Mis tiendas</h1>
        <button onClick={() => setCreando(true)} className={`${BOTON} inline-flex items-center gap-1 text-[13px]`}>
          <IconoMas tamano={16} /> Crear tienda
        </button>
      </div>

      {cargando ? (
        <p className="text-[#616161]">Cargando tiendas...</p>
      ) : tiendas.length === 0 ? (
        <div className="rounded-xl border border-[#e3e3e3] bg-white p-10 text-center">
          <span className="mx-auto mb-3 block w-fit text-[#b5b5b5]"><IconoTienda tamano={40} /></span>
          <p className="text-lg font-semibold">Todavía no tienes tiendas</p>
          <p className="text-[#616161]">Cuando crees una, aquí verás el enlace para compartirla.</p>
          <button onClick={() => setCreando(true)} className={`${BOTON} mt-4 text-[13px]`}>Crear tienda</button>
        </div>
      ) : (
        tiendas.map((t) => {
          // El frontend tiene su propia dirección: el enlace se arma con ella.
          const enlace = `${window.location.origin}/tienda/${t.subdominio}`;
          return (
            <article key={t.idTienda} className="space-y-3 rounded-xl border border-[#e3e3e3] bg-white p-5">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-lg font-semibold">{t.nombre}</h2>
                <span
                  className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                    t.estado === 'ACTIVA' ? 'bg-green-100 text-green-800' : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {t.estado === 'ACTIVA' ? 'Activa' : 'Suspendida'}
                </span>
              </div>
              {t.pais && (
                <p className="text-[13px] text-[#616161]">
                  {t.pais} · cobra en {t.moneda}
                  {t.impuesto ? ` · precios con ${t.impuesto} ${Number(t.tasaImpuesto)} % incluido` : ''}
                </p>
              )}
              <div>
                <p className="mb-1 text-xs text-[#616161]">Enlace de la tienda</p>
                <p className="break-all rounded-md border border-[#e3e3e3] bg-[#f7f7f7] px-3 py-2 text-sm">{enlace}</p>
              </div>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => copiar(t.idTienda, enlace)}
                  className="inline-flex h-8 items-center gap-2 rounded-lg border border-[#c9c9c9] bg-white px-3 hover:bg-[#f3f3f3]"
                >
                  <IconoCopiar tamano={16} /> {copiada === t.idTienda ? 'Copiado' : 'Copiar enlace'}
                </button>
                <a
                  href={enlace}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex h-8 items-center gap-2 rounded-lg bg-[#303030] px-3 text-white hover:bg-black"
                >
                  <IconoEnlaceExterno tamano={16} /> Ver tienda
                </a>
              </div>
            </article>
          );
        })
      )}

      {creando && (
        <FormularioTienda
          onCerrar={() => setCreando(false)}
          onCreada={(nueva) => {
            avisar(`Tienda ${nueva.nombre} creada`);
            setCreando(false);
            cambiarTienda(nueva.idTienda);   // el panel pasa a trabajar con la tienda nueva
            recargar();
          }}
        />
      )}
    </section>
  );
}

/**
 * Crear otra tienda. El país se elige una sola vez: define la moneda en que cobra (soles o pesos
 * chilenos), el impuesto incluido en los precios (IGV o IVA) y la lista de distritos o comunas.
 */
function FormularioTienda({ onCerrar, onCreada }) {
  const { usuario } = useAuth();
  const paises = usePaisesHabilitados();
  const [d, setD] = useState({ nombre: '', subdominio: '', correoContacto: usuario?.correo ?? '', idPais: 'PE' });
  const [subdominioEditado, setSubdominioEditado] = useState(false);
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const pais = paises.find((p) => p.idPais === d.idPais);

  function cambiar(campo) {
    return (e) => {
      const valor = e.target.value;
      setD((v) => {
        const nuevo = { ...v, [campo]: valor };
        if (campo === 'nombre' && !subdominioEditado) nuevo.subdominio = aSubdominio(valor);
        if (campo === 'subdominio') nuevo.subdominio = valor.toLowerCase();
        return nuevo;
      });
      if (campo === 'subdominio') setSubdominioEditado(true);
      setErrores((er) => ({ ...er, [campo]: undefined }));
    };
  }

  async function enviar(e) {
    e.preventDefault();
    const er = {};
    if (!d.nombre.trim()) er.nombre = 'Escribe el nombre de la tienda';
    if (!/^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$/.test(d.subdominio)) {
      er.subdominio = 'Entre 3 y 63 caracteres: letras, números y guiones (sin guion al inicio ni al final)';
    }
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(d.correoContacto.trim())) er.correoContacto = 'El correo no es válido';
    setErrores(er);
    if (Object.keys(er).length) return;

    setGuardando(true);
    setError('');
    try {
      const { data } = await api.post('/panel/tiendas', {
        nombre: d.nombre.trim(),
        subdominio: d.subdominio,
        correoContacto: d.correoContacto.trim(),
        permiteInvitados: true,
        idPais: d.idPais,
      });
      onCreada(data);
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else if (ex.response?.status === 409) setErrores({ subdominio: mensajeDeError(ex) });
      else setError(mensajeDeError(ex));
      setGuardando(false);
    }
  }

  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:py-16">
      <form onSubmit={enviar} noValidate className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-[#ebebeb] px-5 py-3.5">
          <h2 className="font-semibold text-[#101010]">Crear tienda</h2>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="rounded-lg p-1 text-[#616161] hover:bg-[#f1f1f1]">
            <IconoCerrar tamano={18} />
          </button>
        </div>
        <div className="space-y-3 p-5 text-[13px]">
          {error && <Mensaje tipo="error" texto={error} />}
          <CampoPanel etiqueta="Nombre de la tienda" error={errores.nombre}>
            <input value={d.nombre} onChange={cambiar('nombre')} placeholder="Moda Santiago" className={inputPanel(errores.nombre)} />
          </CampoPanel>
          <CampoPanel etiqueta="Dirección web" error={errores.subdominio} ayuda="No se puede cambiar después.">
            <div className="flex">
              <input value={d.subdominio} onChange={cambiar('subdominio')} placeholder="moda-santiago" className={`${inputPanel(errores.subdominio)} rounded-r-none`} />
              <span className="flex items-center rounded-r-lg border border-l-0 border-[#8a8a8a]/50 bg-[#f7f7f7] px-2 text-[#616161]">.vendely.pe</span>
            </div>
          </CampoPanel>
          <CampoPanel
            etiqueta="País"
            error={errores.idPais}
            ayuda={pais ? `Cobrarás en ${NOMBRE_MONEDA[pais.moneda] ?? pais.moneda} (${pais.moneda}). No se puede cambiar después.` : undefined}
          >
            <select value={d.idPais} onChange={cambiar('idPais')} className={inputPanel(errores.idPais)}>
              {paises.map((p) => (
                <option key={p.idPais} value={p.idPais}>{p.nombre}</option>
              ))}
            </select>
          </CampoPanel>
          <CampoPanel etiqueta="Correo de contacto" error={errores.correoContacto}>
            <input type="email" value={d.correoContacto} onChange={cambiar('correoContacto')} className={inputPanel(errores.correoContacto)} />
          </CampoPanel>
        </div>
        <div className="flex justify-end gap-2 border-t border-[#ebebeb] bg-[#f7f7f7] px-5 py-3">
          <button type="button" onClick={onCerrar} className={BOTON_SECUNDARIO}>Cancelar</button>
          <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Creando...' : 'Crear tienda'}</button>
        </div>
      </form>
    </div>
  );
}
