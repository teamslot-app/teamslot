/**
 * SCRUM-135 — mêmes règles que PolitiqueMotDePasse (identity-service). Ici elles servent seulement à guider
 * l'utilisateur pendant qu'il tape : c'est le serveur qui décide.
 */
export type Regle = { id: string; libelle: string; respectee: boolean }

export type Contexte = { nom: string; email: string; telephone: string }

export const LONGUEUR_MIN = 12

function familles(mdp: string): number {
  return [/\p{Ll}/u, /\p{Lu}/u, /\p{Nd}/u, /[^\p{Ll}\p{Lu}\p{Nd}]/u].filter((f) => f.test(mdp)).length
}

function contient(mdp: string, morceau: string): boolean {
  const m = morceau.trim().toLowerCase()
  return m.length >= 3 && mdp.toLowerCase().includes(m)
}

export function reglesMotDePasse(mdp: string, { nom, email, telephone }: Contexte): Regle[] {
  const chiffres = telephone.replace(/\D/g, '')
  const finNumero = chiffres.length >= 6 ? chiffres.slice(-6) : ''
  const partieLocale = email.includes('@') ? email.slice(0, email.indexOf('@')) : ''
  return [
    {
      id: 'longueur',
      libelle: `${LONGUEUR_MIN} caractères minimum`,
      respectee: mdp.length >= LONGUEUR_MIN,
    },
    {
      id: 'familles',
      libelle: '3 types parmi : minuscules, majuscules, chiffres, symboles',
      respectee: familles(mdp) >= 3,
    },
    {
      id: 'personnel',
      libelle: 'Ni ton nom, ni ton e-mail, ni ton numéro',
      respectee:
        mdp.length > 0 &&
        !contient(mdp, nom) &&
        !contient(mdp, partieLocale) &&
        !(finNumero && mdp.includes(finNumero)),
    },
  ]
}

export function motDePasseValide(mdp: string, contexte: Contexte): boolean {
  return mdp.length <= 128 && reglesMotDePasse(mdp, contexte).every((r) => r.respectee)
}
