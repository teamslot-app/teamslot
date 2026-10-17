import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { lireImages, planifier } from './promouvoir.mjs';

const PREFIXE = 'ghcr.io/teamslot-app/';
const SHA = (c) => c.repeat(40);
const DIGEST = (c) => `sha256:${c.repeat(64)}`;

// Format écrit par kustomize et le robot de staging : liste sans indentation, empreinte avant le nom.
function entreeStaging(nom, tag, digest) {
  const lignes = digest ? [`- digest: ${digest}`, `  name: ${PREFIXE}${nom}`] : [`- name: ${PREFIXE}${nom}`];
  return [...lignes, `  newName: ${PREFIXE}${nom}`, `  newTag: ${tag}`].join('\n');
}
const stagingAvec = (...entrees) =>
  [
    'apiVersion: kustomize.config.k8s.io/v1beta1', 'kind: Kustomization', 'namespace: staging',
    'resources:', '- ../../base', 'images:', ...entrees,
    '# SCRUM-99 : copie les tags d\'image déployés', 'replacements:', '- path: smoke-test-versions.yaml',
    'patches:', '- patch: |-', '    - op: add', '  target:', '    kind: Deployment', '',
  ].join('\n');
// Format de la prod : liste indentée, valeurs entre guillemets.
const prodAvec = (...images) =>
  [
    'apiVersion: kustomize.config.k8s.io/v1beta1', 'kind: Kustomization', 'namespace: prod',
    'resources:', '  - ../../base', 'images:',
    ...images.flatMap(([nom, tag, digest]) => [
      `  - name: ${PREFIXE}${nom}`,
      `    newTag: "${tag}"`,
      ...(digest ? [`    digest: ${digest}`] : []),
    ]),
    '',
  ].join('\n');

const STAGING = stagingAvec(
  entreeStaging('gateway', SHA('1'), DIGEST('a')),
  entreeStaging('identity-service', SHA('3'), DIGEST('c')),
  entreeStaging('service-template', SHA('2'), DIGEST('b')),
);
const PROD = prodAvec(['gateway', SHA('9')], ['service-template', SHA('8')]);

describe('lireImages', () => {
  it('lit le format de staging (empreinte avant le nom, liste sans indentation)', () => {
    const images = lireImages(STAGING);
    assert.equal(images.length, 3);
    assert.deepEqual(images[0], {
      digest: DIGEST('a'), name: `${PREFIXE}gateway`, newName: `${PREFIXE}gateway`, newTag: SHA('1'),
    });
  });

  it('lit le format de prod (liste indentée, valeurs entre guillemets) et s’arrête avant les autres blocs', () => {
    assert.deepEqual(lireImages(PROD), [
      { name: `${PREFIXE}gateway`, newTag: SHA('9') },
      { name: `${PREFIXE}service-template`, newTag: SHA('8') },
    ]);
  });
});

describe('planifier', () => {
  it('ne promeut que les images déjà listées en prod, avec le tag et l’empreinte de staging', () => {
    assert.deepEqual(planifier(STAGING, PROD), [
      { name: `${PREFIXE}gateway`, tag: SHA('1'), digest: DIGEST('a') },
      { name: `${PREFIXE}service-template`, tag: SHA('2'), digest: DIGEST('b') },
    ]);
  });

  it('ignore les images déjà à jour (même tag et même empreinte)', () => {
    const prod = prodAvec(['gateway', SHA('1'), DIGEST('a')], ['service-template', SHA('8')]);
    assert.deepEqual(planifier(STAGING, prod).map((p) => p.name), [`${PREFIXE}service-template`]);
  });

  it('ne change rien quand tout est à jour', () => {
    const prod = prodAvec(['gateway', SHA('1'), DIGEST('a')], ['service-template', SHA('2'), DIGEST('b')]);
    assert.deepEqual(planifier(STAGING, prod), []);
  });

  it('refuse une image de prod absente de staging', () => {
    assert.throws(() => planifier(STAGING, prodAvec(['venue-service', SHA('9')])), /pas dans celui de staging/);
  });

  it('refuse une image de staging sans empreinte', () => {
    const staging = stagingAvec(entreeStaging('gateway', SHA('1')));
    assert.throws(() => planifier(staging, prodAvec(['gateway', SHA('9')])), /empreinte/);
  });

  it('refuse un tag ou une empreinte au format inattendu (valeurs envoyées dans un shell)', () => {
    const staging = stagingAvec(entreeStaging('gateway', '$(whoami)', DIGEST('a')));
    assert.throws(() => planifier(staging, prodAvec(['gateway', SHA('9')])), /format inattendu/);
  });

  it('refuse un nom d’image hors de teamslot-app', () => {
    const prod = ['images:', '  - name: docker.io/evil/image', '    newTag: "x"', ''].join('\n');
    assert.throws(() => planifier(STAGING, prod), /Nom d'image inattendu/);
  });

  it('accepte les fins de ligne Windows', () => {
    assert.deepEqual(
      planifier(STAGING.replace(/\n/g, '\r\n'), PROD.replace(/\n/g, '\r\n')),
      planifier(STAGING, PROD),
    );
  });
});
