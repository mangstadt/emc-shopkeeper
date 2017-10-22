package emcshop.rupees.scribe;

import emcshop.rupees.dto.HorseSummonFee.Builder;

import java.util.regex.Matcher;

/**
 * @author Michael Angstadt
 */
public class HorseSummonFeeScribe extends RegexScribe<Builder> {
    public HorseSummonFeeScribe() {
        super("^Summoned stabled horse in the wild @ (.*?):(.*?):(.*?):(.*?)$");
    }

    @Override
    protected Builder builder(Matcher m) {
        //@formatter:off
        return new Builder()
                .world(m.group(1))
                .coords(
                        Double.parseDouble(m.group(2)),
                        Double.parseDouble(m.group(3)),
                        Double.parseDouble(m.group(4))
                );
        //@formatter:on
    }
}