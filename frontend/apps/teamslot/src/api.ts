import { createApi } from '@teamslot/shared'
import { session } from './auth/session'

const reseau = (requete: Request) => globalThis.fetch(requete)

/**
 * Un appel authentifié qui reçoit 401 : on tente UN rafraîchissement, puis on rejoue la requête une fois ;
 * si le rafraîchissement échoue, on ferme la session (retour à l'écran de connexion). Pas de boucle possible :
 * la requête rejouée ne repasse pas par cette fonction.
 */
async function avecRafraichissement(requete: Request): Promise<Response> {
  const copie = requete.clone() // le corps d'une requête ne se lit qu'une fois
  const reponse = await reseau(requete)
  const authentifie = requete.headers.has('Authorization')
  const routeAuth = new URL(requete.url).pathname.startsWith('/api/v1/auth/')
  if (reponse.status !== 401 || !authentifie || routeAuth) return reponse
  if (!(await session.refresh())) {
    session.fermer()
    return reponse
  }
  copie.headers.set('Authorization', `Bearer ${session.accessToken()}`)
  return reseau(copie)
}

// Même origine : nginx (image) ou le proxy de Vite (npm run dev) renvoie /api vers la passerelle.
export const api = createApi({
  baseUrl: window.location.origin,
  getToken: () => session.accessToken(),
  fetch: avecRafraichissement,
})
