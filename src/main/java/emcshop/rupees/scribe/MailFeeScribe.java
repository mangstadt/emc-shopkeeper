package emcshop.rupees.scribe;

import emcshop.rupees.dto.MailFee.Builder;

import java.util.regex.Matcher;

/**
 * @author Michael Angstadt
 */
public class MailFeeScribe extends RegexScribe<Builder> {
    public MailFeeScribe() {
        super("^Sent mail to (.*?): (.*)$");
    }

    @Override
    protected Builder builder(Matcher m) {
        //@formatter:off
        return new Builder()
                .player(m.group(1))
                .subject(m.group(2));
        //@formatter:on
    }
}