// Initialisation de MongoDB (SCRUM-115) : idempotent, on peut le relancer sans risque.
// Exécuté par le Job mongodb-init. Les mots de passe viennent du Secret mongodb-auth (variables d'environnement).

const admin = db.getSiblingDB("admin");
admin.auth("root", process.env.MONGO_ROOT_PASSWORD);

// 1. Replica set rs0 (nécessaire pour les transactions de l'outbox)
const hote = `mongodb-0.mongodb.${process.env.NAMESPACE}.svc.cluster.local:27017`;
try {
  rs.status();
  print("replica set deja initialise");
} catch (e) {
  rs.initiate({ _id: "rs0", members: [{ _id: 0, host: hote }] });
  print("replica set initialise : " + hote);
}

// 2. Attendre que le nœud soit PRIMARY (60 s au plus)
let essais = 0;
while (!admin.runCommand({ hello: 1 }).isWritablePrimary) {
  if (++essais > 60) throw new Error("le noeud n'est pas devenu PRIMARY");
  sleep(1000);
}
print("noeud PRIMARY");

// 3. Utilisateur de match-service : seulement readWrite sur la base match
const match = db.getSiblingDB("match");
const roles = [{ role: "readWrite", db: "match" }];
if (match.getUser("match_user")) {
  // Garde le mot de passe aligné sur le Secret (utile si on le change)
  match.updateUser("match_user", { pwd: process.env.MATCH_PASSWORD, roles: roles });
  print("match_user mis a jour");
} else {
  match.createUser({ user: "match_user", pwd: process.env.MATCH_PASSWORD, roles: roles });
  print("match_user cree");
}
