package emcshop.rupees.scribe;

import emcshop.rupees.dto.DailySigninBonus.Builder;

/**
 * @author Michael Angstadt
 */
public class DailySigninBonusScribe extends SimpleScribe<Builder> {
    public DailySigninBonusScribe() {
        super("Daily sign-in bonus");
    }

    @Override
    protected Builder builder() {
        return new Builder();
    }
}