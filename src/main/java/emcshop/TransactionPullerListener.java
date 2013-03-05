package emcshop;

import java.util.List;

public interface TransactionPullerListener {
	/**
	 * Called when a page has been scraped.
	 * @param page the page number
	 * @param transactions the scraped shop transactions (may be empty)
	 */
	void onPageScraped(int page, List<ShopTransaction> transactions);

	/**
	 * Called when the puller has been canceled.
	 */
	void onCancel();

	/**
	 * Called if an error occurs.
	 * @param thrown the error
	 */
	void onError(Throwable thrown);

	/**
	 * Called when the download is complete.
	 */
	void onSuccess();
}