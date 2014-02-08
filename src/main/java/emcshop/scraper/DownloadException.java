package emcshop.scraper;

/**
 * Thrown if a problem occurs during a transaction download.
 */
@SuppressWarnings("serial")
public class DownloadException extends RuntimeException {
	private final int page;

	/**
	 * @param page the page the error occurred on
	 * @param thrown the error
	 */
	public DownloadException(int page, Throwable thrown) {
		super("An error occurred while downloading or parsing transaction page " + page + ".", thrown);
		this.page = page;
	}

	/**
	 * Gets the transaction page that the error occurred on.
	 * @return the transaction page
	 */
	public int getPage() {
		return page;
	}
}
