package ma.teamslot.identity.application;

/** SCRUM-133 — limite des renvois de code de vérification, pour qu'on ne bombarde pas une boîte mail. */
public interface LimiteRenvoiCode {

    /** Vrai si un nouvel envoi est permis pour cet e-mail (et le compte) ; faux au-delà de la limite. */
    boolean autoriser(String email);
}
