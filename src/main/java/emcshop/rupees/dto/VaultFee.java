package emcshop.rupees.dto;


/**
 * The fee that players are charged for opening their cross-server vault.
 *
 * @author Michael Angstadt
 */
public class VaultFee extends RupeeTransaction {
    private VaultFee(Builder builder) {
        super(builder);
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        public VaultFee build() {
            return new VaultFee(this);
        }
    }
}