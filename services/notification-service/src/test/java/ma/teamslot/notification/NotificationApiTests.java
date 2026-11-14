package ma.teamslot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * SCRUM-81 : la liste « Mes notifications » et le marquage comme lue (docs/api/notification.yaml).
 * Sécurité : chacun ne voit et ne modifie que SES notifications ; identité lue dans le jeton uniquement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiTests {

    private static final String LISTE = "/api/v1/notifications";
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 20, 18, 0, 0, 0, ZoneOffset.UTC);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    private UUID moi;
    private UUID autre;

    @BeforeEach
    void repartirDeZero() {
        jdbc.sql("DELETE FROM notification").update();
        moi = UUID.randomUUID();
        autre = UUID.randomUUID();
    }

    @Test
    void sans_jeton_401() throws Exception {
        mockMvc.perform(get(LISTE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(LISTE + "/" + UUID.randomUUID() + "/read")).andExpect(status().isUnauthorized());
    }

    @Test
    void sub_qui_n_est_pas_un_uuid_401() throws Exception {
        mockMvc.perform(get(LISTE).with(jwt().jwt(j -> j.subject("pas-un-uuid"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void je_vois_seulement_mes_notifications_les_plus_recentes_d_abord_jamais_en_cache() throws Exception {
        UUID ancienne = inserer(moi, "MATCH_PARTICIPANT_ADDED", T0);
        UUID recente = inserer(moi, "MATCH_CONFIRMED", T0.plusHours(1));
        inserer(autre, "MATCH_CONFIRMED", T0.plusHours(2));

        mockMvc.perform(get(LISTE).with(en(moi)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(recente.toString()))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_CONFIRMED"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-20T19:00:00Z"))
                .andExpect(jsonPath("$.items[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$.items[1].id").value(ancienne.toString()))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void le_curseur_parcourt_tout_sans_doublon_ni_trou_meme_avec_des_dates_identiques() throws Exception {
        List<UUID> attendues = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            attendues.add(inserer(moi, "MATCH_CONFIRMED", T0)); // même instant : match.confirmed en crée plusieurs
        }
        attendues.add(inserer(moi, "MATCH_PARTICIPANT_ADDED", T0.minusMinutes(1)));
        attendues.add(inserer(moi, "MATCH_PARTICIPANT_ADDED", T0.minusMinutes(2)));

        List<String> vues = new ArrayList<>();
        String curseur = null;
        int tranches = 0;
        do {
            var requete = get(LISTE).param("limit", "2").with(en(moi));
            if (curseur != null) {
                requete = requete.param("cursor", curseur);
            }
            String corps = mockMvc.perform(requete).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            vues.addAll(JsonPath.read(corps, "$.items[*].id"));
            curseur = JsonPath.read(corps, "$.nextCursor");
            tranches++;
        } while (curseur != null && tranches < 10);

        assertThat(tranches).isEqualTo(3);
        assertThat(vues).hasSize(5).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(attendues.stream().map(UUID::toString).toList());
    }

    @Test
    void limit_hors_bornes_ou_curseur_illisible_400() throws Exception {
        mockMvc.perform(get(LISTE).param("limit", "0").with(en(moi))).andExpect(status().isBadRequest());
        mockMvc.perform(get(LISTE).param("limit", "51").with(en(moi))).andExpect(status().isBadRequest());
        mockMvc.perform(get(LISTE).param("limit", "abc").with(en(moi))).andExpect(status().isBadRequest());
        mockMvc.perform(get(LISTE).param("cursor", "n-importe-quoi").with(en(moi)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/invalid-cursor"));
    }

    @Test
    void marquer_lue_204_et_deux_fois_garde_la_premiere_date() throws Exception {
        UUID id = inserer(moi, "MATCH_PARTICIPANT_ADDED", T0);

        mockMvc.perform(post(LISTE + "/" + id + "/read").with(en(moi))).andExpect(status().isNoContent());
        OffsetDateTime premiere = lueLe(id);
        mockMvc.perform(post(LISTE + "/" + id + "/read").with(en(moi))).andExpect(status().isNoContent());

        assertThat(premiere).isNotNull();
        assertThat(lueLe(id)).isEqualTo(premiere);
        mockMvc.perform(get(LISTE).with(en(moi))).andExpect(jsonPath("$.items[0].readAt").isNotEmpty());
    }

    @Test
    void la_notification_d_un_autre_404_et_reste_non_lue() throws Exception {
        UUID sienne = inserer(autre, "MATCH_PARTICIPANT_ADDED", T0);

        mockMvc.perform(post(LISTE + "/" + sienne + "/read").with(en(moi)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://teamslot.app/problems/notification-not-found"));
        mockMvc.perform(post(LISTE + "/" + UUID.randomUUID() + "/read").with(en(moi)))
                .andExpect(status().isNotFound());

        assertThat(lueLe(sienne)).isNull();
    }

    private static RequestPostProcessor en(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    private UUID inserer(UUID userId, String type, OffsetDateTime creeLe) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO notification (id, user_id, type, match_id, created_at) VALUES (:id, :u, :t, :m, :c)")
                .param("id", id).param("u", userId).param("t", type).param("m", UUID.randomUUID())
                .param("c", creeLe).update();
        return id;
    }

    /** null tant que la notification n'est pas lue (single() refuse une valeur null, d'où list()). */
    private OffsetDateTime lueLe(UUID id) {
        List<OffsetDateTime> dates = jdbc.sql("SELECT read_at FROM notification WHERE id = :id").param("id", id)
                .query((ligne, n) -> ligne.getObject("read_at", OffsetDateTime.class)).list();
        assertThat(dates).hasSize(1);
        return dates.getFirst();
    }
}
