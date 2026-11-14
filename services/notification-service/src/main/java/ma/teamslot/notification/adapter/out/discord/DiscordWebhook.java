package ma.teamslot.notification.adapter.out.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import ma.teamslot.notification.application.EnvoiDiscord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envoi d'un message au salon Discord par webhook (SCRUM-81, décision de planification du 10/11).
 * L'URL du webhook est un secret : elle n'est jamais écrite dans un log, même en cas d'erreur
 * (une exception HTTP contient l'URL dans son message : on ne journalise que le statut ou le type d'erreur).
 * allowed_mentions vide : le message ne peut notifier personne (@everyone, @here, rôles), même par erreur.
 */
@Component
class DiscordWebhook implements EnvoiDiscord {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordWebhook.class);

    private final URI url;
    private final RestClient rest;

    DiscordWebhook(@Value("${teamslot.notification.discord.webhook-url:}") String url,
                   @Value("${teamslot.notification.discord.autoriser-localhost:false}") boolean autoriserLocal) {
        this.url = UrlWebhook.valider(url, autoriserLocal);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().requestFactory(factory).build();
        if (this.url == null) {
            LOG.info("Envoi Discord désactivé : aucune URL de webhook");
        } else {
            LOG.info("Envoi Discord activé");
        }
    }

    @Override
    public boolean actif() {
        return url != null;
    }

    @Override
    public boolean envoyer(String contenu) {
        if (url == null) {
            return false;
        }
        try {
            int statut = rest.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", contenu, "allowed_mentions", Map.of("parse", List.of())))
                    .exchange((requete, reponse) -> reponse.getStatusCode().value());
            if (statut >= 200 && statut < 300) {
                return true;
            }
            LOG.warn("Discord a répondu {}", statut);
            return false;
        } catch (RuntimeException e) {
            LOG.warn("Appel à Discord impossible : {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
