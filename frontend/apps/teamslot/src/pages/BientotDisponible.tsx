/** Page provisoire des écrans pas encore refaits dans le nouveau front (étapes 3 et 4). */
export default function BientotDisponible({ titre }: { titre: string }) {
  return (
    <main className="mx-auto max-w-6xl px-5 py-12 sm:px-8">
      <h1 className="text-4xl font-extrabold tracking-tight text-creme">{titre}</h1>
      <p className="mt-3 max-w-md text-sauge">Cette page arrive dans la prochaine étape du nouveau front.</p>
    </main>
  )
}
