# Vérifier le JWT dans un service — SCRUM-87 (ADR-013)

Chaque service revérifie le jeton (défense en profondeur). Code commun : package `security` du service modèle.

## Lire l'appelant dans un contrôleur

```java
@PostMapping("/api/v1/matches/{id}/participants")
public ResponseEntity<Participant> ajouter(@AuthenticationPrincipal Jwt jwt, ...) {
    UUID appelant = UUID.fromString(jwt.getSubject());   // claim "sub"
    ...
}
```

L'identité vient **uniquement** du jeton : jamais d'un en-tête (la passerelle supprime `X-User-Id`).

## Règles appliquées

- Signature RS256 vérifiée avec la clé publique du JWKS d'identity ; `alg: none` refusé.
- Émetteur `teamslot-identity` et expiration vérifiés.
- Publics : `/actuator/health/**` (sondes Kubernetes) et `/actuator/prometheus` (non routé par la passerelle). Tout le reste exige un jeton, sinon **401**.
- Variables : `JWT_JWK_SET_URI`, `JWT_ISSUER` (les mêmes que la passerelle).

## Dans les tests

```java
mockMvc.perform(post(url).with(jwt().jwt(j -> j.subject(organisateur.toString()))) ...)
```

Import : `SecurityMockMvcRequestPostProcessors.jwt`. Exemples : `SecuriteTests` du modèle.

## Ajouter la vérification à un service copié avant SCRUM-87

Copier le package `security` (adapter la ligne `package`), les deux dépendances du `pom.xml` (`spring-boot-starter-security-oauth2-resource-server`, `spring-security-test`), les lignes `teamslot.jwt.*` de `application.properties`, puis ajouter `.with(jwt())` aux tests qui appellent l'API.
