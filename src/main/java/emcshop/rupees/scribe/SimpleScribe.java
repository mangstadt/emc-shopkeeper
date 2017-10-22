package emcshop.rupees.scribe;

import emcshop.rupees.dto.RupeeTransaction;

/**
 * A scribe that uses a case-insensitive string comparison to parse the transaction.
 *
 * @param <T> the builder class of this scribe's associated rupee transaction
 *            class
 * @author Michael Angstadt
 */
public abstract class SimpleScribe<T extends RupeeTransaction.Builder<?>> extends RupeeTransactionScribe<T> {
    private final String expectedDescription;

    /**
     * @param expectedDescription the expected description
     */
    public SimpleScribe(String expectedDescription) {
        this.expectedDescription = expectedDescription;
    }

    @Override
    public T parse(String description) {
        return expectedDescription.equalsIgnoreCase(description) ? builder() : null;
    }

    /**
     * Generates a new instance of the builder class of this scribe's associated
     * rupee transaction class.
     *
     * @return the builder
     */
    protected abstract T builder();
}