#!/usr/bin/env python3
"""SCRUM-129 : connecte le compte de test DÉDIÉ au scan ZAP (le crée au premier passage).

Numéro et mot de passe : secrets GitHub ZAP_TEST_PHONE et ZAP_TEST_PASSWORD (variables
TELEPHONE et MOT_DE_PASSE). Le jeton obtenu est :
  - masqué dans les logs GitHub (::add-mask::) avant toute autre sortie ;
  - écrit dans <dossier>/zap-entete (en-tête Authorization, droits 600), lu par curl avec -H @fichier.
N'affiche que des codes HTTP : ni numéro, ni mot de passe, ni jeton.

Usage : python3 .zap/compte_test.py <URL du staging> <dossier temporaire>
"""
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request


def appeler(url, corps):
    requete = urllib.request.Request(url, data=json.dumps(corps).encode("utf-8"), method="POST",
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(requete, timeout=30) as reponse:
            return reponse.status, reponse.read()
    except urllib.error.HTTPError as erreur:
        return erreur.code, b""


def main(staging, dossier):
    telephone = os.environ.get("TELEPHONE", "")
    mot_de_passe = os.environ.get("MOT_DE_PASSE", "")
    if not telephone or not mot_de_passe:
        sys.exit("::error::Secrets ZAP_TEST_PHONE et ZAP_TEST_PASSWORD absents : scan authentifié impossible.")
    # Le mot de passe ne doit jamais apparaître, même par erreur : masqué avant toute sortie.
    print(f"::add-mask::{mot_de_passe}")
    print(f"::add-mask::{telephone}")
    api = staging.rstrip("/") + "/api/v1/auth"

    code, _ = appeler(f"{api}/register",
                      {"phone": telephone, "password": mot_de_passe, "displayName": "Robot DAST"})
    if code == 201:
        print("Compte de test créé (premier scan)")
    elif code == 409:
        print("Compte de test déjà existant")
    else:
        sys.exit(f"::error::Création du compte de test : HTTP {code}")

    code, reponse = appeler(f"{api}/login", {"phone": telephone, "password": mot_de_passe})
    if code != 200:
        sys.exit(f"::error::Connexion du compte de test : HTTP {code}")
    jeton = json.loads(reponse).get("accessToken", "")
    if not jeton:
        sys.exit("::error::Connexion du compte de test : pas de jeton dans la réponse")
    print(f"::add-mask::{jeton}")

    entete = pathlib.Path(dossier) / "zap-entete"
    entete.touch(mode=0o600)
    entete.write_text(f"Authorization: Bearer {jeton}\n", encoding="utf-8")
    print("Compte de test connecté (jeton masqué, gardé dans un fichier temporaire)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
