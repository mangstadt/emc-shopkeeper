package emcshop.rupees.scribe;

import emcshop.rupees.dto.EggifyFee.Builder;

import java.util.regex.Matcher;

/**
 * @author Michael Angstadt
 */
public class EggifyFeeScribe extends RegexScribe<Builder> {
    public EggifyFeeScribe() {
        super("^Eggified a (.*)$");
    }

    @Override
    protected Builder builder(Matcher m) {
        return new Builder().mob(m.group(1));
    }
}