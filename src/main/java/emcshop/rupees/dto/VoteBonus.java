package emcshop.rupees.dto;

/**
 * The bonus players get when they vote for EMC on a voting website.
 *
 * @author Michael Angstadt
 */
public class VoteBonus extends RupeeTransaction {
    private final String site;
    private final int day;

    private VoteBonus(Builder builder) {
        super(builder);
        site = builder.site;
        day = builder.day;
    }

    public String getSite() {
        return site;
    }

    public int getDay() {
        return day;
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        private String site;
        private int day;

        public Builder site(String site) {
            this.site = site;
            return this;
        }

        public Builder day(int day) {
            this.day = day;
            return this;
        }

        public VoteBonus build() {
            return new VoteBonus(this);
        }
    }
}