package emcshop.rupees.dto;

/**
 * The fee that players are charged when they summon horses from their stable
 * outside of town.
 *
 * @author Michael Angstadt
 */
public class HorseSummonFee extends RupeeTransaction {
    private final String world;
    private final double x, y, z;

    private HorseSummonFee(Builder builder) {
        super(builder);
        world = builder.world;
        x = builder.x;
        y = builder.y;
        z = builder.z;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        private String world;
        private double x, y, z;

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder coords(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public HorseSummonFee build() {
            return new HorseSummonFee(this);
        }
    }
}