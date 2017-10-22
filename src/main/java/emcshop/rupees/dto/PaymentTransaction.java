package emcshop.rupees.dto;

/**
 * Represents a player-to-player payment transaction.
 *
 * @author Michael Angstadt
 */
public class PaymentTransaction extends RupeeTransaction {
    private final String player, reason;

    public PaymentTransaction(Builder builder) {
        super(builder);
        player = builder.player;
        reason = builder.reason;
    }

    public String getPlayer() {
        return player;
    }

    public String getReason() {
        return reason;
    }

    public static class Builder extends RupeeTransaction.Builder<Builder> {
        private String player, reason;

        public String player() {
            return player;
        }

        public Builder player(String player) {
            this.player = player;
            return this;
        }

        public String reason() {
            return reason;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        @Override
        public PaymentTransaction build() {
            return new PaymentTransaction(this);
        }
    }
}