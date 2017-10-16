package emcshop.rupees.dto;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Contains information from a scraped rupee transaction history page.
 *
 * @author Michael Angstadt
 */
public class RupeeTransactionPage {
    private final Integer rupeeBalance, page, totalPages;
    private final List<RupeeTransaction> transactions;

    /**
     * @param rupeeBalance the logged-in player's rupee balance
     * @param page         the page number
     * @param totalPages   the total number of pages
     * @param transactions the rupee transactions
     */
    public RupeeTransactionPage(Integer rupeeBalance, Integer page, Integer totalPages, List<RupeeTransaction> transactions) {
        this.rupeeBalance = rupeeBalance;
        this.page = page;
        this.totalPages = totalPages;
        this.transactions = Collections.unmodifiableList(transactions);
    }

    public Integer getRupeeBalance() {
        return rupeeBalance;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public List<RupeeTransaction> getTransactions() {
        return transactions;
    }

    /**
     * <p>
     * Gets the date of the first transaction that is listed on the page. Note
     * that, because transactions are listed in descending order, the following
     * statement is true:
     * <p>
     * <p>
     * <code>{@link #getFirstTransactionDate()}.after(
     * {@link #getLastTransactionDate()}) == true</code>
     * </p>
     *
     * @return the transaction date
     */
    public Date getFirstTransactionDate() {
        return transactions.isEmpty() ? null : transactions.get(0).getTs();
    }

    /**
     * <p>
     * Gets the date of the last transaction that is listed on the page. Note
     * that, because transactions are listed in descending order, the following
     * statement is true:
     * <p>
     * <p>
     * <code>{@link #getFirstTransactionDate()}.after(
     * {@link #getLastTransactionDate()}) == true</code>
     * </p>
     *
     * @return the transaction date
     */
    public Date getLastTransactionDate() {
        return transactions.isEmpty() ? null : transactions.get(transactions.size() - 1).getTs();
    }
}