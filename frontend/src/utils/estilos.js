/** Clases de un input: borde rojo si tiene error. */
export function estiloInput(error) {
  return `w-full rounded-lg border bg-white px-3 py-2.5 text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 ${
    error ? 'border-red-400 focus:ring-red-200' : 'border-slate-300 focus:border-violet-500 focus:ring-violet-200'
  }`;
}
