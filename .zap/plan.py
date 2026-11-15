#!/usr/bin/env python3
"""SCRUM-129 : plan du scan ZAP authentifié du staging (ZAP Automation Framework).

À partir des contrats docs/api/*.yaml, garde UNIQUEMENT les routes GET (le scan lit, il ne crée,
ne modifie ni ne supprime rien) des services déployés, et remplit les paramètres obligatoires.
Écrit dans le dossier de sortie :
  - routes.tsv : URL <tab> protegee|publique (pour vérifier les codes HTTP avant le scan) ;
  - plan.json  : le plan ZAP (JSON = YAML valide), sans aucun secret : le jeton est passé à ZAP
                 par la variable d'environnement ZAP_AUTH_HEADER_VALUE, jamais écrit dans un fichier.

Usage : python3 .zap/plan.py <URL du staging> <dossier de sortie>
        python3 .zap/plan.py --lister        (affiche les routes retenues, sans rien écrire)
"""
import datetime
import json
import pathlib
import re
import subprocess
import sys
import urllib.parse

CONTRATS = pathlib.Path(__file__).resolve().parent.parent / "docs" / "api"
# Services déployés en staging (les autres contrats n'ont pas encore de service : 404 inutiles).
DEPLOYES = ["identity", "venue", "match", "payment", "notification"]
# Valeurs connues du staging (données de démo, jamais personnelles) : (service, paramètre) -> valeur.
CONNUS = {
    ("venue", "id"): "a1000000-0000-4000-8000-000000000001",  # Terrain Démo Guéliz
    ("venue", "lat"): "31.63",  # Marrakech
    ("venue", "lon"): "-8.0",
}


def lire_yaml(chemin):
    try:
        import yaml

        return yaml.safe_load(chemin.read_text(encoding="utf-8"))
    except ImportError:
        sortie = subprocess.run(["yq", "-o=json", ".", str(chemin)], check=True, capture_output=True, text=True)
        return json.loads(sortie.stdout)


def resoudre(contrat, objet):
    """Suit une référence locale « #/components/... »."""
    while isinstance(objet, dict) and "$ref" in objet:
        noeud = contrat
        for morceau in objet["$ref"].lstrip("#/").split("/"):
            noeud = noeud[morceau]
        objet = noeud
    return objet


def valeur(parametre):
    schema = parametre.get("schema") or {}
    if schema.get("format") == "date":
        return (datetime.date.today() + datetime.timedelta(days=1)).isoformat()
    for cle in ("example", "default"):
        if cle in schema:
            return str(schema[cle])
    if "enum" in schema:
        return str(schema["enum"][0])
    if schema.get("format") == "uuid":
        return "00000000-0000-4000-8000-000000000000"
    if schema.get("type") in ("number", "integer"):
        return str(schema.get("minimum", 1))
    return "a"


def routes():
    """Liste de (chemin avec requête, protegee) pour les GET des services déployés."""
    resultat = []
    for nom in DEPLOYES:
        contrat = lire_yaml(CONTRATS / f"{nom}.yaml")
        securite_globale = contrat.get("security", [])
        for chemin, operations in (contrat.get("paths") or {}).items():
            get = (operations or {}).get("get")
            # Seule /api/ passe par la passerelle (Ingress) : /.well-known/… reste interne au cluster.
            if not get or not chemin.startswith("/api/"):
                continue
            parametres = [resoudre(contrat, p) for p in operations.get("parameters", []) + get.get("parameters", [])]
            url = chemin
            requete = {}
            complet = True
            for p in parametres:
                if p.get("in") == "path":
                    connu = CONNUS.get((nom, p["name"]))
                    if connu is None:
                        complet = False  # identifiant inconnu : la route ne serait qu'un 404
                        break
                    url = url.replace("{" + p["name"] + "}", connu)
                elif p.get("in") == "query" and p.get("required"):
                    requete[p["name"]] = CONNUS.get((nom, p["name"])) or valeur(p)
            if not complet or re.search(r"\{[^}]+\}", url):
                continue
            if requete:
                url += "?" + urllib.parse.urlencode(requete)
            protegee = get.get("security", securite_globale) != []
            resultat.append((url, protegee))
    return resultat


def plan(staging, liste):
    hote = re.escape(staging.rstrip("/"))
    return {
        "env": {
            "contexts": [{"name": "staging", "urls": [staging.rstrip("/") + "/"], "includePaths": [hote + "/.*"]}],
            "parameters": {"failOnError": True, "failOnWarning": False, "progressToStdout": True},
        },
        "jobs": [
            {"type": "passiveScan-config", "parameters": {"maxAlertsPerRule": 10, "scanOnlyInScope": True}},
            # Routes GET des contrats, avec le jeton du compte de test : réponses 200 des routes protégées.
            {"type": "requestor", "requests": [{"url": staging.rstrip("/") + u, "method": "GET"} for u, _ in liste]},
            # Robot classique : pages et liens HTML, sans envoyer de formulaire.
            {"type": "spider", "parameters": {"context": "staging", "maxDuration": 2, "postForm": False}},
            # AJAX spider : vrai navigateur (le front React). Il suit les liens seulement : aucun bouton
            # cliqué, aucun champ rempli, donc aucun formulaire envoyé (inscription, match, paiement…).
            {"type": "spiderAjax", "parameters": {
                "context": "staging", "browserId": "firefox-headless", "maxDuration": 3, "maxCrawlDepth": 5,
                "numberOfBrowsers": 1, "clickDefaultElems": False, "elements": ["a"], "randomInputs": False,
                "inScopeOnly": True}},
            {"type": "passiveScan-wait", "parameters": {"maxDuration": 5}},
        ] + [
            {"type": "report", "parameters": {"template": modele, "reportDir": "/zap/wrk", "reportFile": "rapport"}}
            for modele in ("traditional-html", "traditional-json", "traditional-md")
        ],
    }


if __name__ == "__main__":
    if sys.argv[1:] == ["--lister"]:
        for u, protegee in routes():
            print(f"{'protegee' if protegee else 'publique '}  GET {u}")
    elif len(sys.argv) == 3:
        staging, dossier = sys.argv[1], pathlib.Path(sys.argv[2])
        liste = routes()
        (dossier / "routes.tsv").write_text(
            "".join(f"{staging.rstrip('/')}{u}\t{'protegee' if p else 'publique'}\n" for u, p in liste), encoding="utf-8")
        (dossier / "plan.json").write_text(json.dumps(plan(staging, liste), indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"{len(liste)} route(s) GET retenue(s), dont {sum(p for _, p in liste)} protégée(s)")
    else:
        sys.exit(__doc__)
