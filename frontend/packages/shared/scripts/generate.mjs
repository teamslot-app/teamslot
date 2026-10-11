// SCRUM-69 : un fichier de types par contrat de docs/api. Ne rien écrire à la main dans src/generated.
import { execSync } from 'node:child_process';
import { mkdirSync, readdirSync, rmSync } from 'node:fs';
import { basename, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const contrats = join(fileURLToPath(new URL('../../../../', import.meta.url)), 'docs', 'api');
const sortie = fileURLToPath(new URL('../src/generated/', import.meta.url));

// On repart de zéro : un contrat retiré fait disparaître son fichier, et la CI le voit.
rmSync(sortie, { recursive: true, force: true });
mkdirSync(sortie, { recursive: true });

for (const fichier of readdirSync(contrats).filter((f) => f.endsWith('.yaml')).sort()) {
  const nom = basename(fichier, '.yaml');
  console.log(`Génération : ${fichier} -> src/generated/${nom}.ts`);
  execSync(
    `npx --no-install openapi-typescript "${join(contrats, fichier)}" -o "${join(sortie, `${nom}.ts`)}"`,
    { stdio: 'inherit' },
  );
}
