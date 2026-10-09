package ma.teamslot.servicetemplate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** Cas d'erreur du relais, avec un faux Kafka : on ne marque jamais publié ce qui n'est pas confirmé. */
class OutboxRelayErreursTest {

    private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    private final OutboxRelay relais = new OutboxRelay(jdbc, kafka);

    private static OutboxRelay.LigneOutbox ligne(String type) {
        return new OutboxRelay.LigneOutbox(UUID.randomUUID(), UUID.randomUUID(), type, "{}");
    }

    private void lignesEnAttente(OutboxRelay.LigneOutbox... lignes) {
        when(jdbc.sql(startsWith("SELECT")).param(anyString(), any())
                .query(OutboxRelay.LigneOutbox.class).list())
                .thenReturn(List.of(lignes));
    }

    private static CompletableFuture<SendResult<String, String>> echec() {
        return CompletableFuture.failedFuture(new IllegalStateException("broker indisponible"));
    }

    private static CompletableFuture<SendResult<String, String>> succes() {
        return CompletableFuture.completedFuture(null);
    }

    @Test
    void un_echec_d_envoi_arrete_le_lot_et_ne_marque_rien() {
        lignesEnAttente(ligne("match.created"), ligne("match.confirmed"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(echec());

        relais.publierEnAttente();

        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        verify(jdbc, never()).sql(startsWith("UPDATE"));
    }

    @Test
    void apres_un_echec_la_suite_n_est_pas_envoyee_pour_garder_l_ordre() {
        lignesEnAttente(ligne("match.created"), ligne("match.confirmed"), ligne("match.cancelled"));
        when(kafka.send(eq("match.created"), anyString(), anyString())).thenReturn(succes());
        when(kafka.send(eq("match.confirmed"), anyString(), anyString())).thenReturn(echec());

        relais.publierEnAttente();

        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
        verify(kafka, never()).send(eq("match.cancelled"), anyString(), anyString());
        verify(jdbc, times(1)).sql(startsWith("UPDATE"));
    }

    @Test
    void une_interruption_ne_marque_rien_et_garde_le_signal() throws Exception {
        lignesEnAttente(ligne("match.created"));
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> envoiInterrompu = mock(CompletableFuture.class);
        when(envoiInterrompu.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(envoiInterrompu);

        relais.publierEnAttente();

        verify(jdbc, never()).sql(startsWith("UPDATE"));
        // Thread.interrupted() vérifie ET efface le signal, pour ne pas gêner les autres tests.
        assertThat(Thread.interrupted()).isTrue();
    }
}
