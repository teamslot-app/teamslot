package ma.teamslot.servicetemplate;

public class QualityGateDemo {

    private static final String DB_PASSWORD = "admin123";

    public boolean memeNom(String a, String b) {
        return a == b;
    }

    public String motDePasse() {
        return DB_PASSWORD;
    }
}
