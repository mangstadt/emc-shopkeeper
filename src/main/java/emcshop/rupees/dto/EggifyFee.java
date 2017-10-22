package emcshop.rupees.dto;

/**
 * The fee that players are charged when they eggify animals outside of town.
 *
 * @author Michael Angstadt
 */
public class EggifyFee extends RupeeTransaction {
    private final String mob;

    private EggifyFee(Builder builder) {
        super(builder);
        mob = builder.mob;
    }

    public String getMob() {
        return mob;
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        private String mob;

        public Builder mob(String mob) {
            this.mob = mob;
            return this;
        }

        public EggifyFee build() {
            return new EggifyFee(this);
        }
    }
}