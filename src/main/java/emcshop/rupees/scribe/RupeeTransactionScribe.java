package emcshop.rupees.scribe;

import emcshop.rupees.dto.RupeeTransaction;

/**
 * Parses a rupee transaction into a specific class, based on the transaction's
 * description.
 *
 * @param <T> the builder class of the rupee transaction class
 * @author Michael Angstadt
 */
public abstract class RupeeTransactionScribe<T extends RupeeTransaction.Builder<?>> {
    /**
     * Parses a transaction's description, returning a builder object for that
     * transaction type.
     *
     * @param description the transaction description
     * @return the builder or null if the description doesn't match the
     * transaction type
     */
    public abstract T parse(String description);

    /**
     * Parses a number that may or may not have commas in it.
     *
     * @param value the string value (e.g. "12,560")
     * @return the parsed number
     * @throws NumberFormatException if the string value can't be parsed
     */
    protected static int parseNumber(String value) {
        value = value.replace(",", "");
        return Integer.parseInt(value);
    }
}