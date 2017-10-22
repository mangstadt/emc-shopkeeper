package emcshop.rupees.dto;

/**
 * A lock fee/refund. Players are charged a fee for locking a container in the
 * wilderness. Players also receive a refund when they remove the lock.
 *
 * @author Michael Angstadt
 */
public class LockTransaction extends RupeeTransaction {
    private final String world;
    private final int x, y, z;

    private LockTransaction(Builder builder) {
        super(builder);
        world = builder.world;
        x = builder.x;
        y = builder.y;
        z = builder.z;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        private String world;
        private int x, y, z;

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder coords(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public LockTransaction build() {
            return new LockTransaction(this);
        }
    }
}