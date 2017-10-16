package emcshop.rupees.scribe;

import emcshop.rupees.dto.VoteBonus.Builder;

import java.util.regex.Matcher;

/**
 * @author Michael Angstadt
 */
public class VoteBonusScribe extends RegexScribe<Builder> {
    public VoteBonusScribe() {
        super("^Voted for Empire Minecraft on (.*?) - day bonus: (\\d+)$");
    }

    @Override
    protected Builder builder(Matcher m) {
        //@formatter:off
        return new Builder()
                .site(m.group(1))
                .day(parseNumber(m.group(2)));
        //@formatter:on
    }
}