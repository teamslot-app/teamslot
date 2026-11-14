package ma.teamslot.notification;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Faux serveur Discord pour les tests : garde les corps reçus et répond avec le statut choisi. */
final class FauxDiscord {

    /** Chemin au format d'un vrai webhook ; le « jeton » sert à vérifier qu'il n'apparaît jamais dans les logs. */
    static final String CHEMIN = "/api/webhooks/123456/jeton-secret-de-test";

    private final HttpServer serveur;
    private final List<String> recus = new CopyOnWriteArrayList<>();
    private final AtomicInteger statut = new AtomicInteger(204);

    FauxDiscord() {
        try {
            serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serveur.createContext(CHEMIN, echange -> {
            String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int code = statut.get();
            if (code >= 200 && code < 300) {
                recus.add(corps);
            }
            echange.sendResponseHeaders(code, -1);
            echange.close();
        });
        serveur.start();
    }

    String url() {
        return "http://localhost:" + serveur.getAddress().getPort() + CHEMIN;
    }

    List<String> recus() {
        return recus;
    }

    void repondre(int code) {
        statut.set(code);
    }

    void vider() {
        recus.clear();
        statut.set(204);
    }
}
