package emcshop.scraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;

import org.apache.http.client.HttpClient;
import org.junit.Test;

import emcshop.util.DateGenerator;

public class TransactionPullerTest {
	private static final EmcSession session = new EmcSession("username", "token", new Date());

	static {
		//disable log messages
		LogManager.getLogManager().reset();
	}

	@Test(expected = BadSessionException.class)
	public void not_logged_in() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		pages.put(1, new TransactionPage(false, null, Arrays.<RupeeTransaction> asList()));

		thePages = pages;
		new MockTransactionPuller();
	}

	@Test
	public void no_rupee_balance() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		pages.put(1, new TransactionPage(true, null, Arrays.<RupeeTransaction> asList()));

		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller();

		assertNull(puller.getRupeeBalance());
	}

	@Test
	public void nextPage() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.MINUTE, -1);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));

		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller();

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
		assertEquals(Arrays.asList(t7, t8, t9), puller.nextPage());
		assertNull(puller.nextPage());
	}

	@Test
	public void nextPage_new_transactions_added_during_download() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.MINUTE, -1);

		final RupeeTransaction t0 = shop(dg.next());

		final RupeeTransaction t1 = shop(dg.next());
		final RupeeTransaction t2 = shop(dg.next());
		final RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));

		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller() {
			private volatile int page1Count = 0;

			@Override
			synchronized TransactionPage getPage(int page, HttpClient client) throws IOException {
				//synchronize this method to ensure that the altered page 1 is returned
				if (page == 1) {
					page1Count++;
					if (page1Count >= 3) {
						//simulate a new transaction being added
						return new TransactionPage(true, 40123, Arrays.asList(t0, t1, t2, t3));
					}
				}

				return super.getPage(page, client);
			}
		};

		//the new transaction should be ignored
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
		assertEquals(Arrays.asList(t7, t8, t9), puller.nextPage());
		assertNull(puller.nextPage());
	}

	@Test
	public void nextPage_maxPaymentTransactionAge() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -10);

		RupeeTransaction t1 = payment(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = payment(dg.next());
		RupeeTransaction t4 = shop(dg.next());

		pages.put(1, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3, t4)));

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setMaxPaymentTransactionAge(1);
		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller(factory);

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t4), puller.nextPage());
		assertNull(puller.nextPage());
	}

	@Test
	public void nextPage_startAtPage() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStartAtPage(2);
		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller(factory);

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
		assertEquals(Arrays.asList(t7, t8, t9), puller.nextPage());
		assertNull(puller.nextPage());
	}

	@Test
	public void nextPage_stopAtDate() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStopAtDate(dg.getGenerated(7));
		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller(factory);

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
		assertEquals(Arrays.asList(t7), puller.nextPage());
		assertNull(puller.nextPage());
	}

	@Test
	public void nextPage_stopAtPage() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStopAtPage(2);
		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller(factory);

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
		assertNull(puller.nextPage());
	}

	@Test(expected = DownloadException.class)
	public void nextPage_bad_session_while_downloading() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		pages.put(page++, new TransactionPage(false, 40123, Arrays.<RupeeTransaction> asList()));

		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller();

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
		puller.nextPage();
	}

	@Test(expected = DownloadException.class)
	public void nextPage_IOException_while_downloading() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		pages.put(page++, new TransactionPage(false, 40123, Arrays.<RupeeTransaction> asList()));

		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller() {
			@Override
			synchronized TransactionPage getPage(int page, HttpClient client) throws IOException {
				if (page == 2) {
					throw new IOException();
				}
				return super.getPage(page, client);
			}
		};

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
		puller.nextPage();
	}

	@Test
	public void nextPage_retry_on_connection_error_while_downloading() throws Throwable {
		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
		int page = 1;
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));

		thePages = pages;
		TransactionPuller puller = new MockTransactionPuller() {
			private volatile boolean threwConnectException = false, threwSocketTimeoutException = false;

			@Override
			synchronized TransactionPage getPage(int page, HttpClient client) throws IOException {
				if (page == 2 && !threwConnectException) {
					threwConnectException = true;
					throw new ConnectException();
				}

				if (page == 3 && !threwSocketTimeoutException) {
					threwSocketTimeoutException = true;
					throw new SocketTimeoutException();
				}

				return super.getPage(page, client);
			}
		};

		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
		assertEquals(Arrays.asList(t7, t8, t9), puller.nextPage());
		assertNull(puller.nextPage());
	}

	private ShopTransaction shop(Date ts) {
		ShopTransaction transaction = new ShopTransaction();
		transaction.setTs(ts);
		transaction.setItem("Apple");
		transaction.setPlayer("Notch");
		transaction.setQuantity(1);
		transaction.setAmount(1);
		transaction.setBalance(1);
		return transaction;
	}

	private PaymentTransaction payment(Date ts) {
		PaymentTransaction transaction = new PaymentTransaction();
		transaction.setTs(ts);
		transaction.setPlayer("Notch");
		transaction.setAmount(1);
		transaction.setBalance(1);
		return transaction;
	}

	private static Map<Integer, TransactionPage> thePages;

	private static class MockTransactionPuller extends TransactionPuller {
		public MockTransactionPuller() throws BadSessionException, IOException {
			this(new TransactionPullerFactory());
		}

		public MockTransactionPuller(TransactionPullerFactory factory) throws BadSessionException, IOException {
			super(session, factory);
		}

		@Override
		TransactionPage getPage(int page, HttpClient client) throws IOException {
			return thePages.containsKey(page) ? thePages.get(page) : thePages.get(1);
		}
	};
}
