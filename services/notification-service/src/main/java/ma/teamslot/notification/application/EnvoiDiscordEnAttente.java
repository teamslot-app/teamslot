package ma.teamslot.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publie dans Discord les messages en attente (table discord_message), comme le relais de l'outbox.
 * Discord en panne : le message reste en attente et est réessayé avec un délai croissant ; il n'est jamais
 * perdu, et une fois accepté il n'est plus renvoyé. Sans URL de webhook (local, tests) : rien n'est envoyé.
 */
@Component
public class EnvoiDiscordEnAttente {

    static final int MESSAGES_PAR_PASSAGE = 10;
    private static final Logger LOG = LoggerFactory.getLogger(EnvoiDiscordEnAttente.class);

    private final NotificationStore store;
    private final EnvoiDiscord discord;
    private final TransactionTemplate transaction;

    public EnvoiDiscordEnAttente(NotificationStore store, EnvoiDiscord discord, TransactionTemplate transaction) {
        this.store = store;
        this.discord = discord;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${teamslot.notification.discord.intervalle-ms:10000}")
    public void envoyer() {
        if (!discord.actif()) {
            return;
        }
        transaction.executeWithoutResult(statut -> store.messagesAEnvoyer(MESSAGES_PAR_PASSAGE).forEach(message -> {
            if (discord.envoyer(message.contenu())) {
                store.marquerEnvoye(message.id());
            } else {
                store.noterEchec(message.id(), message.essais());
                LOG.warn("Message Discord {} non envoyé (essai {}), nouvel essai plus tard", message.id(),
                        message.essais() + 1);
            }
        }));
    }
}
