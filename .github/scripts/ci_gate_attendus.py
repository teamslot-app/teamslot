#!/usr/bin/env python3
"""SCRUM-130 : liste les workflows qu'une PR DOIT déclencher, d'après les fichiers qu'elle modifie.

Lit les fichiers modifiés (un par ligne) sur l'entrée standard et écrit, un par ligne, le chemin
de chaque workflow attendu (.github/workflows/xxx.yml). La règle vient des workflows eux-mêmes
(on.pull_request.paths / paths-ignore) : une seule source de vérité, rien à tenir à jour à la main.

Usage :
  python3 .github/scripts/ci_gate_attendus.py .github/workflows < fichiers.txt
  python3 .github/scripts/ci_gate_attendus.py --self-test
"""
import json
import pathlib
import re
import subprocess
import sys

# Le ci-gate ne s'attend pas lui-même.
IGNORES = {"ci-gate.yml"}


def motif_vers_regex(motif):
    """Filtre de chemins GitHub Actions -> regex : ** = tout (y compris /), * et ? = sans /."""
    regex = ""
    i = 0
    while i < len(motif):
        c = motif[i]
        if motif.startswith("**", i):
            i += 2
            if i < len(motif) and motif[i] == "/":
                regex += "(?:.*/)?"  # « a/**/b » accepte aussi « a/b »
                i += 1
            else:
                regex += ".*"
            continue
        if c == "*":
            regex += "[^/]*"
        elif c == "?":
            regex += "[^/]"
        else:
            regex += re.escape(c)
        i += 1
    return re.compile(regex + r"\Z")


def correspond(fichier, motifs):
    """Règle GitHub : les motifs sont lus dans l'ordre, un motif « !… » retire le fichier."""
    retenu = False
    for motif in motifs:
        if motif.startswith("!"):
            if motif_vers_regex(motif[1:]).match(fichier):
                retenu = False
        elif motif_vers_regex(motif).match(fichier):
            retenu = True
    return retenu


def declenche(config, fichiers):
    """config = valeur de on.pull_request (None = sans filtre)."""
    config = config or {}
    if "paths" in config:
        return any(correspond(f, config["paths"]) for f in fichiers)
    if "paths-ignore" in config:
        return not all(correspond(f, config["paths-ignore"]) for f in fichiers)
    return True


def lire_yaml(chemin):
    try:
        import yaml  # PyYAML, si présent

        donnees = yaml.safe_load(chemin.read_text(encoding="utf-8"))
        if isinstance(donnees, dict) and True in donnees:  # YAML 1.1 : la clé « on » devient True
            donnees["on"] = donnees.pop(True)
        return donnees
    except ImportError:
        sortie = subprocess.run(["yq", "-o=json", ".", str(chemin)], check=True, capture_output=True, text=True)
        return json.loads(sortie.stdout)


def config_pull_request(workflow):
    """(True, config) si le workflow réagit à pull_request, sinon (False, None)."""
    on = (workflow or {}).get("on")
    if on == "pull_request":
        return True, None
    if isinstance(on, list):
        return "pull_request" in on, None
    if isinstance(on, dict) and "pull_request" in on:
        return True, on["pull_request"]
    return False, None


def attendus(dossier, fichiers):
    resultat = []
    for chemin in sorted(pathlib.Path(dossier).glob("*.yml")):
        if chemin.name in IGNORES:
            continue
        reagit, config = config_pull_request(lire_yaml(chemin))
        if reagit and declenche(config, fichiers):
            resultat.append(f".github/workflows/{chemin.name}")
    return resultat


def self_test():
    service = {"paths": ["services/notification-service/**", "libs/**", "pom.xml", ".github/workflows/notification-service.yml"]}
    assert declenche(service, ["services/notification-service/src/A.java"])
    assert declenche(service, ["libs/events/pom.xml"])
    assert declenche(service, ["pom.xml"])
    assert not declenche(service, ["services/payment-service/pom.xml"])
    assert not declenche(service, ["docs/api/notification.yaml", "README.md"])
    assert not declenche(service, ["services/notification-service-bis/x"])
    assert declenche(None, ["README.md"])  # sans filtre : toujours attendu (gitleaks, trivy-fs…)
    assert declenche({"paths-ignore": ["docs/**"]}, ["docs/a.md", "pom.xml"])
    assert not declenche({"paths-ignore": ["docs/**"]}, ["docs/a.md"])
    assert declenche({"paths": ["docs/**", "!docs/brouillon/**"]}, ["docs/a.md"])
    assert not declenche({"paths": ["docs/**", "!docs/brouillon/**"]}, ["docs/brouillon/x.md"])
    assert correspond("frontend/apps/web/src/a.tsx", ["frontend/apps/web/**"])
    assert correspond("deploy/k8s/a.yaml", ["deploy/**/a.yaml"])
    assert not correspond("pom.xml.bak", ["pom.xml"])
    assert not correspond("a/b.yml", ["*.yml"])
    assert config_pull_request({"on": {"pull_request": None, "push": {}}}) == (True, None)
    assert config_pull_request({"on": ["push", "pull_request"]}) == (True, None)
    assert config_pull_request({"on": {"workflow_call": {}}}) == (False, None)
    print("ci_gate_attendus : auto-test OK")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 2:
        modifies = [ligne.strip() for ligne in sys.stdin if ligne.strip()]
        print("\n".join(attendus(sys.argv[1], modifies)))
    else:
        sys.exit(__doc__)
