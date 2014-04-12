package emcshop.scraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;

import org.apache.http.client.HttpClient;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.BeforeClass;
import org.junit.Test;

import emcshop.util.DateGenerator;

public class TransactionPullerTest {
	private static EmcSession session;

	@BeforeClass
	public static void beforeClass() {
		//disable log messages
		LogManager.getLogManager().reset();

		session = mock(EmcSession.class);
		when(session.createHttpClient()).thenReturn(mock(HttpClient.class));
	}

	@Test(expected = BadSessionException.class)
	public void not_logged_in() throws Throwable {
		TransactionPageScraper scraper = mock(TransactionPageScraper.class);

		TransactionPage page = new TransactionPage();
		page.setLoggedIn(false);
		when(scraper.download(eq(1), any(HttpClient.class))).thenReturn(page);

		create(scraper);
	}

	@Test
	public void no_rupee_balance() throws Throwable {
		TransactionPageScraper scraper = mock(TransactionPageScraper.class);

		TransactionPage page = new TransactionPage();
		page.setLoggedIn(true);
		page.setRupeeBalance(null);
		when(scraper.download(eq(1), any(HttpClient.class))).thenReturn(page);

		TransactionPuller puller = create(scraper);
		assertNull(puller.getRupeeBalance());
	}

	@Test
	public void nextPage() throws Throwable {
		DateGenerator dg = new DateGenerator(Calendar.MINUTE, -1);
		int pageCount = 1;

		TransactionPageScraper scraper = mock(TransactionPageScraper.class);

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage pageOne = new TransactionPage();
		pageOne.setLoggedIn(true);
		pageOne.setRupeeBalance(20123);
		pageOne.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(pageOne);

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		TransactionPage page = new TransactionPage();
		page.setLoggedIn(true);
		page.setRupeeBalance(40123);
		page.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page);

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		page = new TransactionPage();
		page.setLoggedIn(true);
		page.setRupeeBalance(40123);
		page.setTransactions(Arrays.asList(t7, t8, t9));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page);

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(pageOne);

		TransactionPuller puller = create(scraper);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t1, t2, t3, t4, t5, t6, t7, t8, t9);

	}

	//
	//	@Test
	//	public void nextPage_new_transactions_added_during_download() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		int page = 1;
	//		DateGenerator dg = new DateGenerator(Calendar.MINUTE, -1);
	//
	//		final RupeeTransaction t0 = shop(dg.next());
	//
	//		final RupeeTransaction t1 = shop(dg.next());
	//		final RupeeTransaction t2 = shop(dg.next());
	//		final RupeeTransaction t3 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));
	//
	//		RupeeTransaction t4 = shop(dg.next());
	//		RupeeTransaction t5 = shop(dg.next());
	//		RupeeTransaction t6 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));
	//
	//		RupeeTransaction t7 = shop(dg.next());
	//		RupeeTransaction t8 = shop(dg.next());
	//		RupeeTransaction t9 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));
	//
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller() {
	//			private volatile int page1Count = 0;
	//
	//			@Override
	//			synchronized TransactionPage getPage(int page, HttpClient client) throws IOException {
	//				//synchronize this method to ensure that the altered page 1 is returned
	//				if (page == 1) {
	//					page1Count++;
	//					if (page1Count >= 3) {
	//						//simulate a new transaction being added
	//						return new TransactionPage(true, 40123, Arrays.asList(t0, t1, t2, t3));
	//					}
	//				}
	//
	//				return super.getPage(page, client);
	//			}
	//		};
	//
	//		//the new transaction should be ignored
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
	//		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
	//		assertEquals(Arrays.asList(t7, t8, t9), puller.nextPage());
	//		assertNull(puller.nextPage());
	//	}
	//
	//	@Test
	//	public void nextPage_maxPaymentTransactionAge() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -10);
	//
	//		RupeeTransaction t1 = payment(dg.next());
	//		RupeeTransaction t2 = shop(dg.next());
	//		RupeeTransaction t3 = payment(dg.next());
	//		RupeeTransaction t4 = shop(dg.next());
	//
	//		pages.put(1, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3, t4)));
	//
	//		TransactionPullerFactory factory = new TransactionPullerFactory();
	//		factory.setMaxPaymentTransactionAge(1);
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller(factory);
	//
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t1, t2, t4), puller.nextPage());
	//		assertNull(puller.nextPage());
	//	}
	//
	//	@Test
	//	public void nextPage_startAtPage() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		int page = 1;
	//		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
	//
	//		RupeeTransaction t1 = shop(dg.next());
	//		RupeeTransaction t2 = shop(dg.next());
	//		RupeeTransaction t3 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));
	//
	//		RupeeTransaction t4 = shop(dg.next());
	//		RupeeTransaction t5 = shop(dg.next());
	//		RupeeTransaction t6 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));
	//
	//		RupeeTransaction t7 = shop(dg.next());
	//		RupeeTransaction t8 = shop(dg.next());
	//		RupeeTransaction t9 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));
	//
	//		TransactionPullerFactory factory = new TransactionPullerFactory();
	//		factory.setStartAtPage(2);
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller(factory);
	//
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
	//		assertEquals(Arrays.asList(t7, t8, t9), puller.nextPage());
	//		assertNull(puller.nextPage());
	//	}
	//
	//	@Test
	//	public void nextPage_stopAtDate() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		int page = 1;
	//		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
	//
	//		RupeeTransaction t1 = shop(dg.next());
	//		RupeeTransaction t2 = shop(dg.next());
	//		RupeeTransaction t3 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));
	//
	//		RupeeTransaction t4 = shop(dg.next());
	//		RupeeTransaction t5 = shop(dg.next());
	//		RupeeTransaction t6 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));
	//
	//		RupeeTransaction t7 = shop(dg.next());
	//		RupeeTransaction t8 = shop(dg.next());
	//		RupeeTransaction t9 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));
	//
	//		TransactionPullerFactory factory = new TransactionPullerFactory();
	//		factory.setStopAtDate(dg.getGenerated(7));
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller(factory);
	//
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
	//		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
	//		assertEquals(Arrays.asList(t7), puller.nextPage());
	//		assertNull(puller.nextPage());
	//	}
	//
	//	@Test
	//	public void nextPage_stopAtPage() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		int page = 1;
	//		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
	//
	//		RupeeTransaction t1 = shop(dg.next());
	//		RupeeTransaction t2 = shop(dg.next());
	//		RupeeTransaction t3 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));
	//
	//		RupeeTransaction t4 = shop(dg.next());
	//		RupeeTransaction t5 = shop(dg.next());
	//		RupeeTransaction t6 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));
	//
	//		RupeeTransaction t7 = shop(dg.next());
	//		RupeeTransaction t8 = shop(dg.next());
	//		RupeeTransaction t9 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));
	//
	//		TransactionPullerFactory factory = new TransactionPullerFactory();
	//		factory.setStopAtPage(2);
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller(factory);
	//
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
	//		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
	//		assertNull(puller.nextPage());
	//	}
	//
	//	@Test(expected = DownloadException.class)
	//	public void nextPage_bad_session_while_downloading() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		int page = 1;
	//		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
	//
	//		RupeeTransaction t1 = shop(dg.next());
	//		RupeeTransaction t2 = shop(dg.next());
	//		RupeeTransaction t3 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));
	//
	//		RupeeTransaction t4 = shop(dg.next());
	//		RupeeTransaction t5 = shop(dg.next());
	//		RupeeTransaction t6 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));
	//
	//		pages.put(page++, new TransactionPage(false, 40123, Arrays.<RupeeTransaction> asList()));
	//
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller();
	//
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
	//		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
	//		puller.nextPage();
	//	}
	//
	//	@Test(expected = DownloadException.class)
	//	public void nextPage_IOException_while_downloading() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		int page = 1;
	//		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
	//
	//		RupeeTransaction t1 = shop(dg.next());
	//		RupeeTransaction t2 = shop(dg.next());
	//		RupeeTransaction t3 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));
	//
	//		RupeeTransaction t4 = shop(dg.next());
	//		RupeeTransaction t5 = shop(dg.next());
	//		RupeeTransaction t6 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));
	//
	//		pages.put(page++, new TransactionPage(false, 40123, Arrays.<RupeeTransaction> asList()));
	//
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller() {
	//			@Override
	//			synchronized TransactionPage getPage(int page, HttpClient client) throws IOException {
	//				if (page == 2) {
	//					throw new IOException();
	//				}
	//				return super.getPage(page, client);
	//			}
	//		};
	//
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
	//		puller.nextPage();
	//	}
	//
	//	@Test
	//	public void nextPage_retry_on_connection_error_while_downloading() throws Throwable {
	//		final Map<Integer, TransactionPage> pages = new HashMap<Integer, TransactionPage>();
	//		int page = 1;
	//		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
	//
	//		RupeeTransaction t1 = shop(dg.next());
	//		RupeeTransaction t2 = shop(dg.next());
	//		RupeeTransaction t3 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 20123, Arrays.asList(t1, t2, t3)));
	//
	//		RupeeTransaction t4 = shop(dg.next());
	//		RupeeTransaction t5 = shop(dg.next());
	//		RupeeTransaction t6 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t4, t5, t6)));
	//
	//		RupeeTransaction t7 = shop(dg.next());
	//		RupeeTransaction t8 = shop(dg.next());
	//		RupeeTransaction t9 = shop(dg.next());
	//		pages.put(page++, new TransactionPage(true, 40123, Arrays.asList(t7, t8, t9)));
	//
	//		thePages = pages;
	//		TransactionPuller puller = new MockTransactionPuller() {
	//			private volatile boolean threwConnectException = false, threwSocketTimeoutException = false;
	//
	//			@Override
	//			synchronized TransactionPage getPage(int page, HttpClient client) throws IOException {
	//				if (page == 2 && !threwConnectException) {
	//					threwConnectException = true;
	//					throw new ConnectException();
	//				}
	//
	//				if (page == 3 && !threwSocketTimeoutException) {
	//					threwSocketTimeoutException = true;
	//					throw new SocketTimeoutException();
	//				}
	//
	//				return super.getPage(page, client);
	//			}
	//		};
	//
	//		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
	//		assertEquals(Arrays.asList(t1, t2, t3), puller.nextPage());
	//		assertEquals(Arrays.asList(t4, t5, t6), puller.nextPage());
	//		assertEquals(Arrays.asList(t7, t8, t9), puller.nextPage());
	//		assertNull(puller.nextPage());
	//	}

	private ShopTransaction shop(Date ts) {
		ShopTransaction transaction = new ShopTransaction() {
			@Override
			public String toString() {
				return ts.toString();
			}
		};
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

	private TransactionPuller create(TransactionPageScraper scraper) {
		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setTransactionPageScraper(scraper);
		try {
			return factory.create(session);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void assertPages(TransactionPuller puller, RupeeTransaction... pages) {
		Set<RupeeTransaction> actual = new HashSet<RupeeTransaction>();
		RupeeTransactions nextPage = null;
		while ((nextPage = puller.nextPage()) != null) {
			actual.addAll(nextPage);
		}

		Set<RupeeTransaction> expected = new HashSet<RupeeTransaction>(Arrays.asList(pages));
		assertEquals(expected, actual);
	}

	private static int gte(int value) {
		return intThat(new IntMatcher(value));
	}

	private static class IntMatcher extends BaseMatcher<Integer> {
		private final int greaterThan;

		public IntMatcher(int greaterThan) {
			this.greaterThan = greaterThan;
		}

		@Override
		public void describeTo(Description description) {
			//empty
		}

		@Override
		public boolean matches(Object value) {
			Integer i = (Integer) value;
			return i >= greaterThan;
		}
	}
}
