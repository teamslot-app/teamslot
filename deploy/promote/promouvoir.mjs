// SCRUM-106 : quelles images promouvoir de staging vers la production ?
// Entrée : l'overlay de staging et celui de prod. Sortie : une ligne « nom<TAB>tag<TAB>empreinte » par image à promouvoir.
// Seules les images DÉJÀ listées dans l'overlay de prod sont promues (la prod est volontairement réduite).
import { readFileSync } from 'node:fs';

/** Lit le bloc « images: » d'un kustomization.yaml (format de kustomize, des robots et de la prod écrite à la main). */
export function lireImages(texte) {
  const images = [];
  let dansImages = false;
  let courante = null;
  for (const ligne of texte.split(/\r?\n/)) {
    if (/^images:\s*$/.test(ligne)) {
      dansImages = true;
      continue;
    }
    if (!dansImages) continue;
    if (/^[A-Za-z_]+:/.test(ligne)) break; // autre clé de premier niveau : fin du bloc
    if (ligne.trim() === '' || ligne.trim().startsWith('#')) continue;
    const debut = ligne.match(/^\s*- ([A-Za-z]+):\s*(.*)$/);
    const suite = ligne.match(/^\s+([A-Za-z]+):\s*(.*)$/);
    if (debut) {
      courante = { [debut[1]]: nettoyer(debut[2]) };
      images.push(courante);
    } else if (suite && courante) {
      courante[suite[1]] = nettoyer(suite[2]);
    }
  }
  return images;
}

const nettoyer = (valeur) => valeur.trim().replace(/^["']|["']$/g, '');

const NOM = /^ghcr\.io\/teamslot-app\/[a-z0-9-]+$/;
const TAG = /^[0-9a-f]{40}$/;
const EMPREINTE = /^sha256:[0-9a-f]{64}$/;

/** Les images à promouvoir, avec le tag et l'empreinte déjà déployés (et vérifiés) en staging. */
export function planifier(texteStaging, texteProd) {
  const staging = new Map(lireImages(texteStaging).map((image) => [image.name, image]));
  const plan = [];
  for (const prod of lireImages(texteProd)) {
    if (!prod.name || !NOM.test(prod.name)) throw new Error("Nom d'image inattendu dans l'overlay de prod.");
    const source = staging.get(prod.name);
    if (!source) {
      throw new Error(`${prod.name} est dans l'overlay de prod mais pas dans celui de staging : rien à promouvoir.`);
    }
    if (!source.newTag || !source.digest) {
      throw new Error(
        `${prod.name} : staging n'a pas de tag ou d'empreinte (digest) : relancer le workflow de ce service sur main avant de promouvoir.`,
      );
    }
    if (!TAG.test(source.newTag) || !EMPREINTE.test(source.digest)) {
      throw new Error(`${prod.name} : tag ou empreinte de staging au format inattendu (refusé par prudence).`);
    }
    if (prod.newTag === source.newTag && prod.digest === source.digest) continue; // déjà à jour
    plan.push({ name: prod.name, tag: source.newTag, digest: source.digest });
  }
  return plan;
}

if (process.argv[1]?.endsWith('promouvoir.mjs')) {
  const [staging, prod] = process.argv.slice(2);
  if (!staging || !prod) {
    console.error('Usage : node promouvoir.mjs <overlay staging> <overlay prod>');
    process.exit(2);
  }
  try {
    for (const { name, tag, digest } of planifier(readFileSync(staging, 'utf8'), readFileSync(prod, 'utf8'))) {
      console.log(`${name}\t${tag}\t${digest}`);
    }
  } catch (erreur) {
    console.error(`::error::${erreur.message}`);
    process.exit(1);
  }
}
