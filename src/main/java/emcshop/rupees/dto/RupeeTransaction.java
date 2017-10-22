package emcshop.rupees.dto;

import java.util.Date;

/**
 * Represents a rupee transaction on the transaction history page.
 *
 * @author Michael Angstadt
 */
public class RupeeTransaction {
    private final Date ts;
    private final String description;
    private final int amount, balance;

    protected RupeeTransaction(Builder<?> builder) {
        ts = builder.ts;
        description = builder.description;
        amount = builder.amount;
        balance = builder.balance;
    }

    /**
     * Gets the transaction's timestamp.
     *
     * @return the timestamp
     */
    public Date getTs() {
        return ts;
    }

    /**
     * Gets the transaction's description.
     *
     * @return the description (e.g. "Daily sign-in bonus")
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the amount that the transaction added or deducted from the player's
     * account.
     *
     * @return the amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Gets the player's total rupee balance after the transaction was applied.
     *
     * @return the player's balance
     */
    public int getBalance() {
        return balance;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + amount;
        result = prime * result + balance;
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((ts == null) ? 0 : ts.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RupeeTransaction other = (RupeeTransaction) obj;
        if (amount != other.amount) {
            return false;
        }
        if (balance != other.balance) {
            return false;
        }
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (ts == null) {
            if (other.ts != null) {
                return false;
            }
        } else if (!ts.equals(other.ts)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [ts=" + ts + ", description=" + description + ", amount=" + amount + ", balance=" + balance + "]";
    }

    /**
     * Creates new instances of the {@link RupeeTransaction} class.
     *
     * @author Michael Angstadt
     */
    public static class Builder<T extends Builder<?>> {
        private Date ts;
        private String description;
        private int amount, balance;

        @SuppressWarnings("unchecked")
        private T this_ = (T) this;

        public Builder() {
            //empty
        }

        public Builder(RupeeTransaction orig) {
            ts = orig.ts;
            description = orig.description;
            amount = orig.amount;
            balance = orig.balance;
        }

        public T ts(Date ts) {
            this.ts = ts;
            return this_;
        }

        public T description(String description) {
            this.description = description;
            return this_;
        }

        public T amount(int amount) {
            this.amount = amount;
            return this_;
        }

        public T balance(int balance) {
            this.balance = balance;
            return this_;
        }

        public RupeeTransaction build() {
            return new RupeeTransaction(this);
        }
    }
}