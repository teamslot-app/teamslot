package ma.teamslot.match.adapter.out.persistence;

import com.mongodb.MongoException;
import java.util.function.Supplier;
import ma.teamslot.match.application.Transactions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction MongoDB rejouée sur un conflit d'écriture (SCRUM-79). Dans une transaction, deux écritures sur le
 * même document en même temps : MongoDB fait échouer la seconde avec l'étiquette TransientTransactionError au lieu
 * d'attendre. On la rejoue : elle relit le match et répond alors 409 (plus de place) au lieu d'une erreur 500.
 */
@Component
public class TransactionsMongo implements Transactions {

    static final int ESSAIS = 3;
    private static final Logger LOG = LoggerFactory.getLogger(TransactionsMongo.class);

    private final TransactionTemplate transaction;

    public TransactionsMongo(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T executer(Supplier<T> travail) {
        for (int essai = 1; ; essai++) {
            try {
                return transaction.execute(status -> travail.get());
            } catch (RuntimeException e) {
                if (essai >= ESSAIS || !conflitTransitoire(e)) {
                    throw e;
                }
                LOG.info("Conflit d'écriture MongoDB, transaction rejouée (essai {})", essai + 1);
            }
        }
    }

    static boolean conflitTransitoire(Throwable erreur) {
        for (Throwable t = erreur; t != null; t = t.getCause()) {
            if (t instanceof MongoException m && m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }
}
