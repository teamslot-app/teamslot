# ADR-015 — Modules partagés et build Maven unique

Statut : proposé (Sprint 1, SCRUM-88)
Remplace : la copie du service modèle dans chaque service (SCRUM-66)

## Contexte
Chaque service est une copie du modèle. Les améliorations arrivent après la copie : match-service et payment-service n'ont pas le relais de l'outbox (SCRUM-70), venue n'a rien du modèle, et la vérification du JWT (SCRUM-87) devrait être copiée dans quatre services. Chaque correction est à refaire cinq fois, et les correctifs Trivy (`tomcat.version`, `jackson-bom.version`) sont répétés dans chaque `pom.xml`.

## Décision
Un seul build Maven depuis la racine du dépôt, avec des modules partagés. Rien n'est publié dans un registre.

```
pom.xml            parent teamslot-parent, hérite de spring-boot-starter-parent 4.1.1
mvnw, .mvn/        un seul Maven Wrapper
libs/events        outbox, relais, lecture, idempotence, .dlt, V2__outbox.sql (SCRUM-70)
libs/web           erreurs ProblemDetail et identifiant de corrélation (SCRUM-25)
libs/security      vérification du JWT pour les services servlet (ADR-013, SCRUM-87)
services/<service> dépend seulement des modules dont il a besoin
gateway            module du build, hérite du parent, garde sa sécurité réactive
```

### Règles
1. **Parent** : versions, correctifs Trivy et plugins communs (JaCoCo, Surefire) écrits une seule fois.
2. **Modules `libs/*`** : simples bibliothèques (pas de `spring-boot-maven-plugin`), configurées par **auto-configuration** Spring Boot (`META-INF/spring/...AutoConfiguration.imports`), sans scan de paquets. Paquets neutres : `ma.teamslot.events`, `ma.teamslot.web`, `ma.teamslot.security`. Un service obtient une fonction en ajoutant la dépendance ; une propriété la désactive (exemple : `teamslot.outbox.relay.enabled=false` en staging tant que Kafka n'y est pas).
3. **Flyway** : `V2__outbox.sql` passe dans `libs/events` avec **exactement le même nom et le même contenu** (déjà appliquée en staging). Un service supprime sa copie **dans le même commit** où il ajoute la dépendance. Les migrations métier d'un service commencent à `V3`. Une migration déjà appliquée n'est **jamais** modifiée : on en ajoute une nouvelle.
4. **Build** : `./mvnw -pl services/<service> -am verify` construit et teste le service et ses modules.
5. **CI** (Sara) : `java-service.yml` construit depuis la racine ; chaque appelant surveille aussi `libs/**` et `pom.xml` ; Sonar analyse seulement le module du service (un projet par service) ; un workflow `libs.yml` teste les modules et les analyse dans un projet Sonar `teamslot-app_libs`. Trivy (fichiers et images) inchangé.
6. **Docker** (Aya) : contexte de build à la racine, `.dockerignore` strict, build limité au service et à ses modules.

### Ordre
1. SCRUM-88 : parent, wrapper, `libs/events`, service-template comme pilote, CI et Dockerfile du modèle.
2. `libs/web`, puis `libs/security` (repris de la PR #74).
3. Migration des services un par un : match, payment, venue, identity ; la passerelle hérite du parent.

## Conséquences
+ Une correction ou un correctif de sécurité se fait une seule fois.
+ Tous les services ont le même socle (outbox, erreurs, sécurité), testé dans les modules.
+ Un service n'embarque que les modules dont il a besoin.
- Un changement dans `libs/**` reconstruit et redéploie tous les services qui en dépendent.
- Le contexte Docker devient la racine du dépôt : un `.dockerignore` strict est indispensable.
- La migration demande de la coordination : un service à la fois, avec relecture.

## Alternatives écartées
- **Copie du modèle dans chaque service** (situation actuelle) : duplication et oublis.
- **Publication des modules dans un registre** (GitHub Packages) : versions à publier et à gérer, jeton de lecture pour chaque build.
- **Sous-modules Git** : complexité de manipulation pour l'équipe, sans gain par rapport à un build unique.
