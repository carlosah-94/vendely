/* eslint-disable react-refresh/only-export-components */
import CampoPanel from './CampoPanel';
import { inputPanel } from '../utils/panel';
import { TEXTOS } from './tienda/rubros/comun';

/**
 * "Contenido de tu plantilla" en Personalizar (V23): los textos y secciones de la plantilla por
 * rubro que el dueño adapta a su negocio, en cualquier plan. Un campo vacío usa el texto de la
 * plantilla (se ve como ejemplo gris). El backend vuelve a validar todo (ContenidoRubro.java).
 */

export const MAX = { etiqueta: 60, boton: 30, sello: 30, anticipacion: 40, garantia: 40, lema: 120, globo: 16, pasoTitulo: 30, pasoTexto: 140, pregunta: 120, respuesta: 500 };
const MAX_PASOS = 4;
const MAX_PREGUNTAS = 6;
const SIN_SIGNOS = /^[^<>]*$/;

/** Qué se puede editar en cada plantilla (además de la frase, el botón y las preguntas). */
const CAMPOS = {
  horno: ['sello', 'anticipacion', 'pasos', 'mostrarFechaEspecial'],
  circuito: ['garantia', 'mostrarMarcas'],
  impulso: ['lema'],
  pasarela: ['tallas'],
  glow: ['sello', 'pasos'],
  nido: [],
  huellitas: ['globos'],
};

/** El formulario a partir de lo guardado (diseno.contenidoRubro). Los pasos empiezan con los que se ven. */
export function formularioContenido(guardado, tema) {
  const c = guardado ?? {};
  const base = TEXTOS[tema] ?? {};
  return {
    etiqueta: c.etiqueta ?? '', boton: c.boton ?? '', sello: c.sello ?? '', anticipacion: c.anticipacion ?? '',
    garantia: c.garantia ?? '', lema: c.lema ?? '', globo1: c.globo1 ?? '', globo2: c.globo2 ?? '',
    tallas: c.tallas ?? base.tallas ?? 'ropa',
    mostrarFechaEspecial: c.mostrarFechaEspecial ?? true,
    mostrarMarcas: c.mostrarMarcas ?? true,
    mostrarPasos: c.mostrarPasos ?? true,
    pasos: (c.pasos?.length ? c.pasos : base.pasos ?? []).map((p) => ({ titulo: p.titulo ?? '', texto: p.texto ?? '' })),
    preguntas: (c.preguntas ?? []).map((q) => ({ pregunta: q.pregunta ?? '', respuesta: q.respuesta ?? '' })),
  };
}

/** Lo que se envía al backend (vacío = null: la plantilla usa su texto). */
export function contenidoParaEnviar(f) {
  const t = (s) => s.trim() || null;
  return {
    etiqueta: t(f.etiqueta), boton: t(f.boton), sello: t(f.sello), anticipacion: t(f.anticipacion),
    garantia: t(f.garantia), lema: t(f.lema), globo1: t(f.globo1), globo2: t(f.globo2), tallas: f.tallas,
    mostrarFechaEspecial: f.mostrarFechaEspecial, mostrarMarcas: f.mostrarMarcas, mostrarPasos: f.mostrarPasos,
    pasos: f.pasos.filter((p) => p.titulo.trim()).map((p) => ({ titulo: p.titulo.trim(), texto: p.texto.trim() || null })),
    preguntas: f.preguntas.filter((q) => q.pregunta.trim() || q.respuesta.trim())
      .map((q) => ({ pregunta: q.pregunta.trim(), respuesta: q.respuesta.trim() })),
  };
}

/** Los mismos controles que el backend: largos, sin < ni >, preguntas completas. Devuelve { campo: error }. */
export function validarContenido(f) {
  const er = {};
  const revisar = (clave, valor, max) => {
    if (valor.length > max) er[clave] = `Hasta ${max} caracteres`;
    else if (!SIN_SIGNOS.test(valor)) er[clave] = 'No se permiten los signos < ni >';
  };
  [['etiqueta', MAX.etiqueta], ['boton', MAX.boton], ['sello', MAX.sello], ['anticipacion', MAX.anticipacion],
    ['garantia', MAX.garantia], ['lema', MAX.lema], ['globo1', MAX.globo], ['globo2', MAX.globo]]
    .forEach(([clave, max]) => revisar(`contenidoRubro.${clave}`, f[clave], max));
  f.pasos.forEach((p, i) => {
    revisar(`contenidoRubro.pasos[${i}].titulo`, p.titulo, MAX.pasoTitulo);
    revisar(`contenidoRubro.pasos[${i}].texto`, p.texto, MAX.pasoTexto);
  });
  f.preguntas.forEach((q, i) => {
    revisar(`contenidoRubro.preguntas[${i}].pregunta`, q.pregunta, MAX.pregunta);
    revisar(`contenidoRubro.preguntas[${i}].respuesta`, q.respuesta, MAX.respuesta);
    if (q.pregunta.trim() && !q.respuesta.trim()) er[`contenidoRubro.preguntas[${i}].respuesta`] = 'Escribe la respuesta';
    if (!q.pregunta.trim() && q.respuesta.trim()) er[`contenidoRubro.preguntas[${i}].pregunta`] = 'Escribe la pregunta';
  });
  return er;
}

function Texto({ etiqueta, ayuda, valor, ejemplo, max, error, onCambiar }) {
  return (
    <CampoPanel etiqueta={etiqueta} error={error} ayuda={ayuda ?? `${valor.length} / ${max}`}>
      <input value={valor} maxLength={max + 20} placeholder={ejemplo} onChange={(e) => onCambiar(e.target.value)} className={inputPanel(error)} />
    </CampoPanel>
  );
}

function Casilla({ etiqueta, valor, onCambiar }) {
  return (
    <label className="flex cursor-pointer items-center gap-2 text-[13px] text-[#303030]">
      <input type="checkbox" checked={valor} onChange={(e) => onCambiar(e.target.checked)} className="h-4 w-4 accent-[#303030]" />
      {etiqueta}
    </label>
  );
}

const LINK = 'text-[13px] font-semibold text-[#1650d0] hover:underline disabled:cursor-not-allowed disabled:text-[#b5b5b5] disabled:no-underline';

export default function ContenidoPlantilla({ tema, valor: f, errores, onCambiar }) {
  const base = TEXTOS[tema];
  const campos = CAMPOS[tema] ?? [];
  const err = (clave) => errores[`contenidoRubro.${clave}`];
  const set = (campo) => (x) => onCambiar({ ...f, [campo]: x });
  const cambiarLista = (lista, i, campo, x) => onCambiar({ ...f, [lista]: f[lista].map((it, j) => (j === i ? { ...it, [campo]: x } : it)) });
  const quitar = (lista, i) => onCambiar({ ...f, [lista]: f[lista].filter((_, j) => j !== i) });
  if (!base) return null;

  return (
    <div className="space-y-4">
      <p className="rounded-lg bg-[#f7f7f7] px-3 py-2 text-[12px] text-[#616161]">
        Adapta la plantilla a tu negocio. Si dejas un campo vacío, se usa el texto de ejemplo (en gris).
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <Texto etiqueta="Frase de la portada" valor={f.etiqueta} ejemplo={base.etiqueta} max={MAX.etiqueta} error={err('etiqueta')} onCambiar={set('etiqueta')} />
        <Texto etiqueta="Texto del botón" valor={f.boton} ejemplo={base.boton} max={MAX.boton} error={err('boton')} onCambiar={set('boton')} />
        {campos.includes('sello') && (
          <Texto etiqueta={tema === 'glow' ? 'Sello redondo' : 'Texto del sello que gira'} valor={f.sello} ejemplo={base.sello} max={MAX.sello}
            ayuda="Por ejemplo: Hecho hoy, Sin gluten, Vegano" error={err('sello')} onCambiar={set('sello')} />
        )}
        {campos.includes('anticipacion') && (
          <Texto etiqueta="Anticipación de los pedidos" valor={f.anticipacion} ejemplo={base.anticipacion} max={MAX.anticipacion}
            ayuda='Se lee "Pide con 24 horas de anticipación"' error={err('anticipacion')} onCambiar={set('anticipacion')} />
        )}
        {campos.includes('garantia') && (
          <Texto etiqueta="Tiempo de garantía" valor={f.garantia} ejemplo={base.garantia} max={MAX.garantia}
            ayuda='Se lee "Garantía de 12 meses"' error={err('garantia')} onCambiar={set('garantia')} />
        )}
        {campos.includes('lema') && (
          <Texto etiqueta="Palabras de la cinta" valor={f.lema} ejemplo={base.lema} max={MAX.lema}
            ayuda="Sepáralas con comas" error={err('lema')} onCambiar={set('lema')} />
        )}
        {campos.includes('globos') && (
          <>
            <Texto etiqueta="Globo 1" valor={f.globo1} ejemplo={base.globo1} max={MAX.globo} error={err('globo1')} onCambiar={set('globo1')} />
            <Texto etiqueta="Globo 2" valor={f.globo2} ejemplo={base.globo2} max={MAX.globo} error={err('globo2')} onCambiar={set('globo2')} />
          </>
        )}
        {campos.includes('tallas') && (
          <CampoPanel etiqueta="Guía de tallas" ayuda="Según lo que vendes">
            <select value={f.tallas} onChange={(e) => set('tallas')(e.target.value)} className={inputPanel()}>
              <option value="ropa">Ropa (XS a XL)</option>
              <option value="calzado">Calzado (36 a 42)</option>
              <option value="ninos">Niños (2 a 12 años)</option>
              <option value="ninguna">No mostrar (accesorios, bolsos...)</option>
            </select>
          </CampoPanel>
        )}
      </div>

      {campos.includes('mostrarFechaEspecial') && (
        <Casilla etiqueta='Mostrar "¿Lo necesitas para una fecha especial?" (con tu WhatsApp)' valor={f.mostrarFechaEspecial} onCambiar={set('mostrarFechaEspecial')} />
      )}
      {campos.includes('mostrarMarcas') && (
        <Casilla etiqueta="Mostrar las marcas de tus productos" valor={f.mostrarMarcas} onCambiar={set('mostrarMarcas')} />
      )}

      {campos.includes('pasos') && (
        <div className="space-y-3 rounded-lg border border-[#e3e3e3] p-3">
          <Casilla etiqueta={tema === 'glow' ? 'Mostrar la rutina en pasos' : 'Mostrar "¿Cómo hago mi pedido?"'} valor={f.mostrarPasos} onCambiar={set('mostrarPasos')} />
          {f.mostrarPasos && f.pasos.map((p, i) => (
            <div key={i} className="grid gap-2 sm:grid-cols-[1fr_2fr_auto] sm:items-start">
              <input value={p.titulo} placeholder={`Paso ${i + 1}`} onChange={(e) => cambiarLista('pasos', i, 'titulo', e.target.value)}
                className={inputPanel(errores[`contenidoRubro.pasos[${i}].titulo`])} aria-label={`Título del paso ${i + 1}`} />
              <input value={p.texto} placeholder="Explicación corta" onChange={(e) => cambiarLista('pasos', i, 'texto', e.target.value)}
                className={inputPanel(errores[`contenidoRubro.pasos[${i}].texto`])} aria-label={`Texto del paso ${i + 1}`} />
              <button type="button" onClick={() => quitar('pasos', i)} className="px-2 py-2 text-[13px] text-[#8e1f0b] hover:underline">Quitar</button>
              {(errores[`contenidoRubro.pasos[${i}].titulo`] || errores[`contenidoRubro.pasos[${i}].texto`]) && (
                <p className="text-[12px] text-[#8e1f0b] sm:col-span-3">{errores[`contenidoRubro.pasos[${i}].titulo`] || errores[`contenidoRubro.pasos[${i}].texto`]}</p>
              )}
            </div>
          ))}
          {f.mostrarPasos && (
            <button type="button" disabled={f.pasos.length >= MAX_PASOS} className={LINK}
              onClick={() => onCambiar({ ...f, pasos: [...f.pasos, { titulo: '', texto: '' }] })}>
              + Agregar paso ({f.pasos.length}/{MAX_PASOS})
            </button>
          )}
        </div>
      )}

      <div className="space-y-3 rounded-lg border border-[#e3e3e3] p-3">
        <p className="text-[13px] font-semibold text-[#303030]">Tus preguntas frecuentes</p>
        <p className="text-[12px] text-[#616161]">Se muestran antes de las automáticas (pagos, envíos, recojo y delivery salen de tu configuración).</p>
        {f.preguntas.map((q, i) => (
          <div key={i} className="space-y-2 rounded-lg bg-[#fafafa] p-2.5">
            <input value={q.pregunta} placeholder="¿Hacen tortas sin azúcar?" onChange={(e) => cambiarLista('preguntas', i, 'pregunta', e.target.value)}
              className={inputPanel(errores[`contenidoRubro.preguntas[${i}].pregunta`])} aria-label={`Pregunta ${i + 1}`} />
            <textarea rows={2} value={q.respuesta} placeholder="Sí, por encargo con 2 días de anticipación." onChange={(e) => cambiarLista('preguntas', i, 'respuesta', e.target.value)}
              className={inputPanel(errores[`contenidoRubro.preguntas[${i}].respuesta`])} aria-label={`Respuesta ${i + 1}`} />
            <div className="flex items-center justify-between gap-2">
              <p className="text-[12px] text-[#8e1f0b]">{errores[`contenidoRubro.preguntas[${i}].pregunta`] || errores[`contenidoRubro.preguntas[${i}].respuesta`]}</p>
              <button type="button" onClick={() => quitar('preguntas', i)} className="text-[13px] text-[#8e1f0b] hover:underline">Quitar</button>
            </div>
          </div>
        ))}
        <button type="button" disabled={f.preguntas.length >= MAX_PREGUNTAS} className={LINK}
          onClick={() => onCambiar({ ...f, preguntas: [...f.preguntas, { pregunta: '', respuesta: '' }] })}>
          + Agregar pregunta ({f.preguntas.length}/{MAX_PREGUNTAS})
        </button>
      </div>
    </div>
  );
}
