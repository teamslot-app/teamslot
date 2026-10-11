package ma.teamslot.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SCRUM-61 : la répartition au hasard, sans base ni Spring. */
class RandomTeamSplitStrategyTest {

    private static final UUID MATCH = UUID.randomUUID();

    private static List<Participant> joueurs(int n) {
        List<Participant> liste = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            liste.add(i % 2 == 0
                    ? Participant.compte(MATCH, UUID.randomUUID())
                    : Participant.invite(MATCH, "Joueur" + i));
        }
        return liste;
    }

    private static Set<UUID> ids(List<Participant> liste) {
        Set<UUID> ids = new HashSet<>();
        liste.forEach(p -> ids.add(p.id()));
        return ids;
    }

    @Test
    void de_2_a_20_joueurs_chacun_dans_un_seul_cote_et_effectifs_a_un_pres() {
        for (int n = 2; n <= 20; n++) {
            List<Participant> entree = joueurs(n);
            TeamSplit split = new RandomTeamSplitStrategy(new Random(n)).split(entree);

            Set<UUID> a = ids(split.sideA());
            Set<UUID> b = ids(split.sideB());
            assertThat(a).as("n=%d : personne des deux côtés", n).doesNotContainAnyElementsOf(b);
            Set<UUID> tous = new HashSet<>(a);
            tous.addAll(b);
            assertThat(tous).as("n=%d : personne d'oublié", n).isEqualTo(ids(entree));
            assertThat(split.sideA().size() - split.sideB().size()).as("n=%d : équilibre", n).isBetween(0, 1);
        }
    }

    @Test
    void meme_graine_meme_tirage() {
        List<Participant> entree = joueurs(10);
        TeamSplit un = new RandomTeamSplitStrategy(new Random(7)).split(entree);
        TeamSplit deux = new RandomTeamSplitStrategy(new Random(7)).split(entree);
        assertThat(un).isEqualTo(deux);
    }

    @Test
    void le_tirage_depend_de_la_graine() {
        List<Participant> entree = joueurs(20);
        Set<Set<UUID>> cotesA = new HashSet<>();
        for (long graine = 0; graine < 10; graine++) {
            cotesA.add(ids(new RandomTeamSplitStrategy(new Random(graine)).split(entree).sideA()));
        }
        assertThat(cotesA.size()).isGreaterThan(1);
    }

    @Test
    void un_seul_joueur_est_refuse() {
        assertThatThrownBy(() -> new RandomTeamSplitStrategy(new Random(1)).split(joueurs(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void la_liste_d_entree_n_est_pas_modifiee() {
        List<Participant> entree = joueurs(6);
        List<Participant> copie = List.copyOf(entree);
        new RandomTeamSplitStrategy(new Random(3)).split(entree);
        assertThat(entree).isEqualTo(copie);
    }
}
