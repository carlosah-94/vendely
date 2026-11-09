/** Página provisional para las secciones que se construyen en otras tareas. */
export default function EnConstruccion({ titulo }) {
  return (
    <section className="space-y-2">
      <h1 className="text-2xl font-bold text-slate-800">{titulo}</h1>
      <p className="text-slate-600">Esta sección todavía está en construcción.</p>
    </section>
  );
}