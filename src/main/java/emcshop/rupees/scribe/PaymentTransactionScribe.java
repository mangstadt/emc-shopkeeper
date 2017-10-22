package emcshop.rupees.scribe;

import emcshop.rupees.dto.PaymentTransaction.Builder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Michael Angstadt
 */
public class PaymentTransactionScribe extends RupeeTransactionScribe<Builder> {
    private final Pattern paymentFromRegex = Pattern.compile("^Payment from (.*?)(:\\s*(.*))?$", Pattern.CASE_INSENSITIVE);
    private final Pattern paymentToRegex = Pattern.compile("^Payment to (.*?)(:\\s*(.*))?$", Pattern.CASE_INSENSITIVE);

    @Override
    public Builder parse(String description) {
        Matcher m = paymentFromRegex.matcher(description);
        if (!m.find()) {
            m = paymentToRegex.matcher(description);
            if (!m.find()) {
                //not a payment transaction
                return null;
            }
        }

        String player = m.group(1);
        String reason = m.group(3);
        if (reason != null && reason.trim().isEmpty()) {
            reason = null;
        }

        //@formatter:off
        return new Builder()
                .player(player)
                .reason(reason);
        //@formatter:on
    }
}