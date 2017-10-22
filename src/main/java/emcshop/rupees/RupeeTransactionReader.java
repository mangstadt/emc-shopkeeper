package emcshop.rupees;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.client.CookieStore;
import org.jsoup.nodes.Document;

import emcshop.net.EmcWebsiteConnection;
import emcshop.net.EmcWebsiteConnectionImpl;
import emcshop.net.InvalidSessionException;
import emcshop.rupees.dto.RupeeTransaction;
import emcshop.rupees.dto.RupeeTransactionPage;
import emcshop.rupees.scribe.RupeeTransactionScribe;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * Downloads rupee transactions from the EMC website. Use its {@link Builder}
 * class to create new instances.
 *
 * @author Michael Angstadt
 * @see <a href=
 * "http://www.empireminecraft.com/rupees/transactions">http://www.empireminecraft.com/rupees/transactions</a>
 */
public class RupeeTransactionReader implements Closeable {
    private static final Logger logger = Logger.getLogger(RupeeTransactionReader.class.getName());

    private Iterator<RupeeTransaction> transactionsOnCurrentPage;
    private RupeeTransactionPage currentPage;
    private final BlockingQueue<RupeeTransactionPage> queue = new LinkedBlockingQueue<RupeeTransactionPage>();

    /**
     * When this object is added to the queue, it signals that there are no more
     * elements to process.
     */
    private final RupeeTransactionPage noMoreElements = new RupeeTransactionPage(null, null, null, Collections.<RupeeTransaction>emptyList());

    private final Map<Integer, RupeeTransactionPage> buffer = new HashMap<Integer, RupeeTransactionPage>();

    /**
     * Stores the hashes of each transaction that has been returned by the
     * next() method. This is to prevent duplicate transactions from being
     * returned if transactions are added to a user's rupee history while this
     * reader is reading it.
     * <p>
     * I probably could have stuck with a Set here (the hash is probably unique
     * enough), but I made this a Multimap to help prevent false positives from
     * happening (i.e. two non-duplicate transactions that have the same hash).
     * The key is the day that the transaction occurred on (the timestamp with
     * the time portion truncated), and the value is the hashes of the
     * transactions that occurred on that date.
     */
    private final Multimap<Date, Integer> hashesOfReturnedTransactions = HashMultimap.create();

    private final PageSource pageSource;
    private final Integer startAtPage, stopAtPage;
    private final Date startAtDate, stopAtDate;
    private final int threads;

    private final Date latestTransactionDate;
    private final AtomicInteger pageCounter;

    private int nextPageToPutInQueue;
    private IOException thrown = null;

    private int deadThreads = 0;
    private boolean cancel = false, endOfStream = false;
    private Integer rupeeBalance;

    private RupeeTransactionReader(Builder builder) throws IOException {
        pageSource = builder.pageSource;
        threads = builder.threads;
        stopAtPage = builder.stopPage;
        stopAtDate = builder.stopDate;

        EmcWebsiteConnection connection = pageSource.createSession();
        RupeeTransactionPage firstPage = pageSource.getPage(1, connection);

		/*
         * Get the date of the latest transaction so we know when we've reached
		 * the last transaction page (the first page is returned when you
		 * request a non-existent page number)
		 */
        latestTransactionDate = firstPage.getFirstTransactionDate();

        startAtDate = builder.startDate;
        if (startAtDate == null) {
            startAtPage = builder.startPage;
        } else {
            if (startAtDate.after(firstPage.getLastTransactionDate())) {
                startAtPage = 1;
            } else {
                startAtPage = findStartPage(startAtDate, firstPage.getTotalPages(), connection);
            }
        }

        //start the page download threads
        pageCounter = new AtomicInteger(startAtPage);
        nextPageToPutInQueue = startAtPage;
        for (int i = 0; i < threads; i++) {
            ScrapeThread thread = new ScrapeThread(connection);
            thread.setDaemon(true);
            thread.setName(getClass().getSimpleName() + "-" + i);
            thread.start();

			/*
             * Ensure that each thread has its own connection object. Re-use the
			 * connection object we created above for the first thread.
			 */
            if (i < threads - 1) {
                connection = pageSource.recreateConnection(connection);
            }
        }
    }

    /**
     * Uses binary search to find the rupee transaction page that contains a
     * transactions with the given date (or, as close as it can get to the given
     * date without exceeding it).
     *
     * @param startDate  the start date
     * @param totalPages the total number of rupee transaction pages
     * @param connection the website connection
     * @return the page number
     * @throws IOException
     */
    private int findStartPage(Date startDate, int totalPages, EmcWebsiteConnection connection) throws IOException {
        int curPage = totalPages / 2;
        int nextAmount = curPage / 2;
        while (nextAmount > 0) {
            int amount = nextAmount;
            nextAmount /= 2;

            RupeeTransactionPage page = pageSource.getPage(curPage, connection);
            if (startDate.after(page.getFirstTransactionDate())) {
                curPage -= amount;
                continue;
            }

            if (startDate.before(page.getLastTransactionDate())) {
                curPage += amount;
                continue;
            }

            break;
        }

        return curPage;
    }

    /**
     * Gets the next rupee transaction. Transactions are returned in descending
     * order.
     *
     * @return the next transaction or null if there are no more transactions
     * @throws IOException
     */
    public RupeeTransaction next() throws IOException {
        if (endOfStream) {
            return null;
        }

        RupeeTransaction transaction;
        while (true) {
			/*
			 * Check to see if we are done processing the transactions of the
			 * current page. If so, pop the next transaction page from the
			 * queue.
			 */
            while (transactionsOnCurrentPage == null || !transactionsOnCurrentPage.hasNext()) {
                try {
                    currentPage = queue.take();
                } catch (InterruptedException e) {
                    close();
                    endOfStream = true;
                    synchronized (this) {
                        if (thrown != null) {
                            throw thrown;
                        }
                    }
                    return null;
                }

                if (currentPage == noMoreElements) {
                    endOfStream = true;
                    synchronized (this) {
                        if (thrown != null) {
                            throw thrown;
                        }
                    }
                    return null;
                }

                transactionsOnCurrentPage = currentPage.getTransactions().iterator();
                rupeeBalance = currentPage.getRupeeBalance();
            }

            transaction = transactionsOnCurrentPage.next();

			/*
			 * If a start date was specified, then skip any transactions that
			 * come after the start date. This is to account for the case when a
			 * transaction page contains transactions that come after the start
			 * date. For example, the transaction with the start date might be
			 * in the middle of the page, so we want to ignore all transactions
			 * that come *before* it on the page (since transactions are listed
			 * in descending order).
			 */
            if (startAtDate != null && transaction.getTs().after(startAtDate)) {
                continue;
            }

			/*
			 * If a stop date was specified, and the transaction's date is the
			 * same as, or comes before, the stop date, then we're reached the
			 * "end of stream". The download threads will terminate in time.
			 */
            if (stopAtDate != null && transaction.getTs().getTime() <= stopAtDate.getTime()) {
                endOfStream = true;
                return null;
            }

			/*
			 * Check to see if the transaction was already returned. If this
			 * happens, then it means that new transactions were added while
			 * this class was downloading transaction pages. When a new
			 * transaction is logged, the new transaction "bumps" all other
			 * transactions "down" one. This causes duplicate transactions to be
			 * read.
			 */
            Date date = DateUtils.truncate(transaction.getTs(), Calendar.DATE);
            if (!hashesOfReturnedTransactions.put(date, transaction.hashCode())) {
                continue;
            }

            return transaction;
        }
    }

    /**
     * Gets the player's total rupee balance. This value is updated every time a
     * new transaction page is read. It should not change unless more
     * transactions are added while the reader is downloading pages.
     *
     * @return the rupee balance or null if no transaction pages have been read
     * yet
     */
    public Integer getRupeeBalance() {
        return rupeeBalance;
    }

    /**
     * Gets the current page number.
     *
     * @return the page number
     */
    public int getCurrentPageNumber() {
        return (currentPage == null) ? startAtPage : currentPage.getPage();
    }

    private class ScrapeThread extends Thread {
        private EmcWebsiteConnection connection;

        public ScrapeThread(EmcWebsiteConnection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            int pageNumber = 0;
            try {
                while (true) {
                    synchronized (RupeeTransactionReader.this) {
                        if (cancel) {
                            break;
                        }
                    }

                    pageNumber = pageCounter.getAndIncrement();
                    if (stopAtPage != null && pageNumber > stopAtPage) {
                        break;
                    }

                    RupeeTransactionPage transactionPage = null;
                    try {
                        transactionPage = pageSource.getPage(pageNumber, connection);
                    } catch (ConnectException e) {
                        transactionPage = reconnectAndRedownload(pageNumber, e);
                    } catch (SocketTimeoutException e) {
                        transactionPage = reconnectAndRedownload(pageNumber, e);
                    }

					/*
					 * The session shouldn't expire while a download is in
					 * progress, but run a check in case the sky falls.
					 */
                    if (transactionPage == null) {
                        logger.warning("A transaction page couldn't be downloaded due to an invalid session token.  Re-creating the connection.");
                        connection = pageSource.createSession();
                        transactionPage = pageSource.getPage(pageNumber, connection);
                        if (transactionPage == null) {
                            throw new InvalidSessionException();
                        }
                    }

					/*
					 * EMC will load the first page if an invalid page number is
					 * given (in our case, if we're trying to download past the
					 * last page).
					 */
                    boolean lastPageReached = pageNumber > 1 && transactionPage.getFirstTransactionDate().getTime() >= latestTransactionDate.getTime();
                    if (lastPageReached) {
                        break;
                    }

                    if (stopAtDate != null && transactionPage.getFirstTransactionDate().getTime() <= stopAtDate.getTime()) {
						/*
						 * If the FIRST transaction in the list comes before the
						 * stop date, then the entire page should be ignored (it
						 * should *not* be added to the queue), and the thread
						 * should terminate (because there are no more
						 * transaction pages to parse).
						 */
                        break;
                    }

                    synchronized (RupeeTransactionReader.this) {
                        if (nextPageToPutInQueue == pageNumber) {
                            queue.add(transactionPage);

                            for (int i = pageNumber + 1; true; i++) {
                                RupeeTransactionPage page = buffer.remove(i);
                                if (page == null) {
                                    nextPageToPutInQueue = i;
                                    break;
                                }
                                queue.add(page);
                            }
                        } else {
                            buffer.put(pageNumber, transactionPage);
                        }
                    }

                    if (stopAtDate != null && transactionPage.getLastTransactionDate().getTime() <= stopAtDate.getTime()) {
						/*
						 * At this point, we know the FIRST transaction in the
						 * list does *not* come before the stop date (see if
						 * statement above), but the LAST transaction *does*
						 * come before the stop date (this if statement).
						 *
						 * This means a sub-set of the transactions on this page
						 * come before the stop date, so we still need to add
						 * this page to the queue (as was done above).
						 *
						 * However, the thread can terminate because we know
						 * there are no more transaction pages to parse.
						 */
                        break;
                    }
                }
            } catch (Throwable t) {
                synchronized (RupeeTransactionReader.this) {
                    if (cancel) {
                        return;
                    }

                    thrown = new IOException("A problem occurred downloading page " + pageNumber + ".", t);
                    cancel = true;
                }
            } finally {
                synchronized (RupeeTransactionReader.this) {
                    deadThreads++;
                    closeQuietly(connection);
                    if (deadThreads == threads) {
                        //this is the last thread to terminate
                        queue.add(noMoreElements);
                    }
                }
            }
        }

        /**
         * <p>
         * Recreates the HTTP connection and then re-downloads the transaction
         * page. This is done in response to a connection failure.
         * </p>
         * <p>
         * <a href=
         * "http://empireminecraft.com/threads/shop-statistics.22507/page-14#post-684085">One
         * user reported</a> getting connection errors at various points while
         * trying to download 12k pages. If there's a connection problem, try
         * re-creating the connection.
         * </p>
         *
         * @param pageNumber the transaction page number
         * @param thrown     the exception that was thrown
         * @return the re-downloaded transaction page
         * @throws IOException if there's still a problem downloading the page
         */
        private RupeeTransactionPage reconnectAndRedownload(int pageNumber, Throwable thrown) throws IOException {
            logger.log(Level.WARNING, "A connection error occurred while downloading transactions.  Re-creating the connection.", thrown);
            connection = pageSource.recreateConnection(connection);
            return pageSource.getPage(pageNumber, connection);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        cancel = true;
        queue.add(noMoreElements);
    }

    /**
     * Creates new instances of {@link RupeeTransactionReader}.
     *
     * @author Michael Angstadt
     */
    public static class Builder {
        private PageSource pageSource;
        private List<RupeeTransactionScribe<?>> scribes = new ArrayList<RupeeTransactionScribe<?>>();
        private RupeeTransactionPageScraper pageScraper;
        private Integer startPage = 1, stopPage;
        private Date startDate, stopDate;
        private int threads = 4;

        /**
         * This constructor is meant for unit testing. The {@link PageSource}
         * object allows the unit test to directly inject
         * {@link RupeeTransactionPage} instances into the reader.
         *
         * @param pageSource produces {@link RupeeTransactionPage} instances
         */
        Builder(PageSource pageSource) {
            this.pageSource = pageSource;
        }

        /**
         * Use a previously authenticated session.
         *
         * @param cookieStore the session's cookies
         */
        public Builder(final CookieStore cookieStore) {
            pageSource = new PageSourceImpl() {
                @Override
                public EmcWebsiteConnection createSession() throws IOException {
                    return new EmcWebsiteConnectionImpl(cookieStore);
                }
            };
        }

        /**
         * Authenticate using the player's username and password.
         *
         * @param username the player's username
         * @param password the player's password
         */
        public Builder(final String username, final String password) {
            pageSource = new PageSourceImpl() {
                @Override
                public EmcWebsiteConnection createSession() throws IOException {
                    return new EmcWebsiteConnectionImpl(username, password);
                }
            };
        }

        /**
         * Adds one or more custom transaction scribes to the reader.
         *
         * @param scribes the scribes to add
         * @return this
         */
        public Builder scribes(RupeeTransactionScribe<?>... scribes) {
            this.scribes.addAll(Arrays.asList(scribes));
            return this;
        }

        /**
         * Sets the page number that the reader will start parsing on. By
         * default, the reader will start parsing on page 1.
         *
         * @param page the start page
         * @return this
         */
        public Builder start(Integer page) {
            startPage = page;
            startDate = null;
            return this;
        }

        /**
         * Gets the page number that the reader will start parsing on. By
         * default, the reader will start parsing on page 1.
         *
         * @return the start page or null if a start date is defined
         */
        public Integer startPage() {
            return startPage;
        }

        /**
         * Sets the transaction date to start parsing on. By default, the reader
         * will start parsing at the very beginning.
         *
         * @param date the start date
         * @return this
         */
        public Builder start(Date date) {
            startDate = date;
            startPage = null;
            return this;
        }

        /**
         * Gets the date that the reader will start parsing on. By default, the
         * reader will start parsing at the very beginning.
         *
         * @return the start date or null if not set
         */
        public Date startDate() {
            return startDate;
        }

        /**
         * Sets the page number to stop parsing on. By default, the reader will
         * continue parsing until the last page has been reached.
         *
         * @param page the page number to stop parsing on (inclusive) or null to
         *             keep parsing until the end
         * @return this
         */
        public Builder stop(Integer page) {
            stopPage = page;
            stopDate = null;
            return this;
        }

        /**
         * Gets the page number to stop parsing on. By default, the reader will
         * continue parsing until the last page has been reached.
         *
         * @return the page number to stop parsing on (inclusive) or null to
         * keep parsing until the end
         */
        public Integer stopPage() {
            return stopPage;
        }

        /**
         * Sets the transaction date to stop parsing on. By default, the reader
         * will continue parsing until the last page has been reached.
         *
         * @param date the stop date (exclusive) or null to keep parsing until
         *             the end
         * @return this
         */
        public Builder stop(Date date) {
            stopDate = date;
            stopPage = null;
            return this;
        }

        /**
         * Gets the transaction date to stop parsing on. By default, the reader
         * will continue parsing until the last page has been reached.
         *
         * @return date the stop date (exclusive) or null to keep parsing until
         * the end
         */
        public Date stopDate() {
            return stopDate;
        }

        /**
         * <p>
         * Sets the number of background threads to use for downloading and
         * parsing rupee transaction pages from the website. In other words,
         * this method sets the number of transaction pages that can be
         * downloaded at once. By default, the reader has 4 threads.
         * </p>
         * <p>
         * Having multiple threads significantly improves the speed of the
         * reader, due to the inherent network latency involved when downloading
         * data from the Internet.
         * </p>
         *
         * @param threads the number of threads
         * @return this
         */
        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        /**
         * Constructs the {@link RupeeTransactionReader} object.
         *
         * @return the object
         * @throws IOException if there's a problem initializing the connection
         *                     to the EMC website
         */
        public RupeeTransactionReader build() throws IOException {
            if (threads <= 0) {
                threads = 1;
            }

            pageScraper = new RupeeTransactionPageScraper(scribes);

            if (stopPage != null && stopPage < 1) {
                stopPage = 1;
            }

            return new RupeeTransactionReader(this);
        }

        private abstract class PageSourceImpl implements PageSource {
            @Override
            public RupeeTransactionPage getPage(int pageNumber, EmcWebsiteConnection connection) throws IOException {
                Document document = connection.getRupeeTransactionPage(pageNumber);
                return pageScraper.scrape(document);
            }

            @Override
            public EmcWebsiteConnection recreateConnection(EmcWebsiteConnection connection) throws IOException {
                return new EmcWebsiteConnectionImpl(connection.getCookieStore());
            }
        }
    }

    /**
     * Produces {@link RupeeTransactionPage} objects. This interface is here so
     * the unit tests can inject transaction pages into the workflow.
     *
     * @author Michael Angstadt
     */
    interface PageSource {
        /**
         * Retrieves a transaction page.
         *
         * @param pageNumber the page number
         * @param connection the connection to the EMC website
         * @return the transaction page
         * @throws IOException if there is a problem getting the page
         */
        RupeeTransactionPage getPage(int pageNumber, EmcWebsiteConnection connection) throws IOException;

        /**
         * Recreates the connection to an existing, authenticated session.
         *
         * @param connection the old connection
         * @return the new connection
         * @throws IOException if there's a problem recreating the connection
         */
        EmcWebsiteConnection recreateConnection(EmcWebsiteConnection connection) throws IOException;

        /**
         * Creates a new, authenticated session on the EMC website.
         *
         * @return the connection to the session
         * @throws IOException if there's a problem creating the connection
         */
        EmcWebsiteConnection createSession() throws IOException;
    }
}