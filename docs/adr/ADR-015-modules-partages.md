# ADR-015 — Modules partagés et build Maven unique

Statut : proposé (Sprint 1, SCRUM-88), amendé après la relecture d'Aya (PR #79)
Remplace : la copie du service modèle dans chaque service (SCRUM-66)

## Contexte
Chaque service est une copie du modèle. Les améliorations arrivent après la copie : match-service a reçu une copie complète du relais avec SCRUM-59 (#89), payment-service ne l'a pas, venue n'a rien du modèle, et la vérification du JWT (SCRUM-87) devrait être copiée dans quatre services. Chaque correction est à refaire cinq fois, et les correctifs Trivy (`tomcat.version`, `jackson-bom.version`, `lz4-java`) sont répétés dans chaque `pom.xml`, parfois de deux façons différentes (`lz4-java` : `dependencyManagement` dans le modèle, dépendance directe dans match).

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
1. **Parent** : versions, correctifs Trivy (`tomcat.version`, `jackson-bom.version`, version forcée de `lz4-java`…) et plugins communs (JaCoCo, Surefire) écrits une seule fois. Chaque version forcée porte en commentaire la CVE qu'elle corrige, et elle est retirée dès que Spring Boot embarque une version au moins égale.
2. **Modules `libs/*`** : simples bibliothèques (pas de `spring-boot-maven-plugin`), configurées par **auto-configuration** Spring Boot (`META-INF/spring/...AutoConfiguration.imports`), sans scan de paquets. Paquets neutres : `ma.teamslot.events`, `ma.teamslot.web`, `ma.teamslot.security`. Un module ne dépend pas d'un autre module `libs/*` sans raison écrite (exemple : `events` définit sa propre clé MDC `correlationId` au lieu d'importer celle de `web`).
   - **API publique** dans le paquet racine du module (`Outbox`, `EventEnvelope`, `EventReader`, `IdempotentProcessor`) ; **classes internes** dans un sous-paquet `internal` (`JdbcOutbox`, `OutboxRelay`, configuration Kafka), que les services n'utilisent pas.
   - Chaque bean de l'auto-configuration porte `@ConditionalOnMissingBean` : un service peut le remplacer, et un service en cours de migration ne se retrouve pas avec deux beans du même type.
   - Un service obtient une fonction en ajoutant la dépendance ; une propriété la désactive (exemple : `teamslot.outbox.relay.enabled=false`).
3. **Flyway** :
   - `V1` = schéma initial du service (les `V1__init.sql` existants restent à leur place) ; `V2` = outbox, fournie par `libs/events` ; `V3` et suivantes = évolutions du service.
   - `V2__outbox.sql` passe dans `libs/events` avec **exactement le même nom et le même contenu** (déjà appliquée en staging ; aujourd'hui identique dans match, payment, venue et le modèle). Un service supprime sa copie **dans le même commit** où il ajoute la dépendance.
   - **Garde-fou** : un test de `libs/events` compare l'empreinte SHA-256 de `V2__outbox.sql` à une valeur fixe. L'empreinte est calculée **après suppression des `\r`**, et `.gitattributes` force `*.sql text eol=lf`, pour que le résultat soit le même sous Windows et en CI. Si le test échoue, on ne change pas la valeur : on ajoute une migration `V3`.
   - Une migration déjà appliquée n'est **jamais** modifiée : on en ajoute une nouvelle.
4. **Build** : `./mvnw -pl services/<service> -am verify` construit et teste le service et ses modules.
5. **CI** (Sara) : `java-service.yml` construit depuis la racine ; chaque appelant surveille aussi `libs/**` et `pom.xml` ; Sonar analyse seulement le module du service (un projet par service) ; un workflow `libs.yml` teste les modules et les analyse dans un projet Sonar `teamslot-app_libs`. Trivy inchangé : scan des fichiers, scan de l'image dès la PR (`image-scan`, SCRUM-90) et sur `main`.
6. **Docker** (Aya) : contexte de build à la racine, `.dockerignore` strict. Le réacteur Maven lit tous les modules listés par le parent : le Dockerfile copie **tous les `pom.xml`** (sinon Maven répond « Child module does not exist »), mais **seulement les sources** du service et des modules dont il dépend.

### Ordre
1. SCRUM-88 : parent, wrapper, `libs/events`, service-template comme pilote, CI et Dockerfile du modèle.
2. match-service et payment-service adoptent `libs/events` (ils ont besoin du relais pour SCRUM-59, SCRUM-60 et SCRUM-62).
3. `libs/web`, puis `libs/security` (repris de la PR #74).
4. venue-service et identity-service, puis `libs/web` et `libs/security` dans tous les services ; la passerelle hérite du parent.

Chaque migration de service est une PR séparée, relue.

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
