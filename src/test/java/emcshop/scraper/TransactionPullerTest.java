package emcshop.scraper;

import static emcshop.util.TestUtils.gte;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.LogManager;

import org.apache.http.client.HttpClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import emcshop.util.DateGenerator;

public class TransactionPullerTest {
	private static EmcSession session;

	private DateGenerator dg;
	private int pageCount;
	private TransactionPageScraper scraper;

	@BeforeClass
	public static void beforeClass() {
		//disable log messages
		LogManager.getLogManager().reset();

		session = mock(EmcSession.class);
	}

	@Before
	public void before() {
		dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
		pageCount = 1;
		scraper = mock(TransactionPageScraper.class);
	}

	@Test(expected = BadSessionException.class)
	public void not_logged_in() throws Throwable {
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(false);
		when(scraper.download(eq(1), any(HttpClient.class))).thenReturn(page1);

		create(scraper);
	}

	@Test
	public void no_rupee_balance() throws Throwable {
		TransactionPage pageOne = new TransactionPage();
		pageOne.setLoggedIn(true);
		pageOne.setRupeeBalance(null);
		when(scraper.download(eq(1), any(HttpClient.class))).thenReturn(pageOne);

		TransactionPuller puller = create(scraper);
		assertNull(puller.getRupeeBalance());
	}

	@Test
	public void nextPage() throws Throwable {
		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(true);
		page1.setRupeeBalance(20123);
		page1.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page1);

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		TransactionPage page2 = new TransactionPage();
		page2.setLoggedIn(true);
		page2.setRupeeBalance(40123);
		page2.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page2);

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		TransactionPage page3 = new TransactionPage();
		page3.setLoggedIn(true);
		page3.setRupeeBalance(40123);
		page3.setTransactions(Arrays.asList(t7, t8, t9));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page3);

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(page1);

		TransactionPuller puller = create(scraper);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t1, t2, t3, t4, t5, t6, t7, t8, t9);

	}

	@Test
	public void nextPage_new_transactions_added_during_download() throws Throwable {
		RupeeTransaction t0 = shop(dg.next());

		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(true);
		page1.setRupeeBalance(20123);
		page1.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page1);

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		TransactionPage page2 = new TransactionPage();
		page2.setLoggedIn(true);
		page2.setRupeeBalance(40123);
		page2.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page2);

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		TransactionPage page3 = new TransactionPage();
		page3.setLoggedIn(true);
		page3.setRupeeBalance(40123);
		page3.setTransactions(Arrays.asList(t7, t8, t9));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page3);

		TransactionPage updatedPage1 = new TransactionPage();
		updatedPage1.setLoggedIn(true);
		updatedPage1.setRupeeBalance(40123);
		updatedPage1.setTransactions(Arrays.asList(t0, t1, t2));
		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(updatedPage1);

		TransactionPuller puller = create(scraper);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t1, t2, t3, t4, t5, t6, t7, t8, t9);
	}

	@Test
	public void nextPage_maxPaymentTransactionAge() throws Throwable {
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -10);

		RupeeTransaction t1 = payment(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = payment(dg.next());
		RupeeTransaction t4 = shop(dg.next());
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(true);
		page1.setRupeeBalance(20123);
		page1.setTransactions(Arrays.asList(t1, t2, t3, t4));
		when(scraper.download(anyInt(), any(HttpClient.class))).thenReturn(page1);

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setMaxPaymentTransactionAge(1);
		factory.setTransactionPageScraper(scraper);
		TransactionPuller puller = create(factory);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t1, t2, t4);
	}

	@Test
	public void nextPage_startAtPage() throws Throwable {
		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(true);
		page1.setRupeeBalance(20123);
		page1.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page1);

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		TransactionPage page2 = new TransactionPage();
		page2.setLoggedIn(true);
		page2.setRupeeBalance(40123);
		page2.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page2);

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		TransactionPage page3 = new TransactionPage();
		page3.setLoggedIn(true);
		page3.setRupeeBalance(40123);
		page3.setTransactions(Arrays.asList(t7, t8, t9));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page3);

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(page1);

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStartAtPage(2);
		factory.setTransactionPageScraper(scraper);
		TransactionPuller puller = create(factory);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t4, t5, t6, t7, t8, t9);
	}

	@Test
	public void nextPage_stopAtDate() throws Throwable {
		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(true);
		page1.setRupeeBalance(20123);
		page1.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page1);

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		TransactionPage page2 = new TransactionPage();
		page2.setLoggedIn(true);
		page2.setRupeeBalance(40123);
		page2.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page2);

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		TransactionPage page3 = new TransactionPage();
		page3.setLoggedIn(true);
		page3.setRupeeBalance(40123);
		page3.setTransactions(Arrays.asList(t7, t8, t9));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page3);

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(page1);

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStopAtDate(dg.getGenerated(7));
		factory.setTransactionPageScraper(scraper);
		TransactionPuller puller = create(factory);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t1, t2, t3, t4, t5, t6, t7);
	}

	@Test
	public void nextPage_stopAtPage() throws Throwable {
		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(true);
		page1.setRupeeBalance(20123);
		page1.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page1);

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		TransactionPage page2 = new TransactionPage();
		page2.setLoggedIn(true);
		page2.setRupeeBalance(40123);
		page2.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page2);

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		TransactionPage page3 = new TransactionPage();
		page3.setLoggedIn(true);
		page3.setRupeeBalance(40123);
		page3.setTransactions(Arrays.asList(t7, t8, t9));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page3);

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(page1);

		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStopAtPage(2);
		factory.setTransactionPageScraper(scraper);
		TransactionPuller puller = create(factory);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t1, t2, t3, t4, t5, t6);
	}

	@Test(expected = DownloadException.class)
	public void nextPage_bad_session_while_downloading() throws Throwable {
		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage page1 = new TransactionPage();
		page1.setLoggedIn(true);
		page1.setRupeeBalance(20123);
		page1.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page1);

		RupeeTransaction t4 = shop(dg.next());
		RupeeTransaction t5 = shop(dg.next());
		RupeeTransaction t6 = shop(dg.next());
		TransactionPage page2 = new TransactionPage();
		page2.setLoggedIn(true);
		page2.setRupeeBalance(40123);
		page2.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page2);

		TransactionPage page = new TransactionPage();
		page.setLoggedIn(false);
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page);

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(page1);

		TransactionPuller puller = create(scraper);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());

		int i = 0;
		while (puller.nextPage() != null && i < pageCount) {
			i++;
		}
		fail();
	}

	@Test(expected = DownloadException.class)
	public void nextPage_IOException_while_downloading() throws Throwable {
		RupeeTransaction t1 = shop(dg.next());
		RupeeTransaction t2 = shop(dg.next());
		RupeeTransaction t3 = shop(dg.next());
		TransactionPage pageOne = new TransactionPage();
		pageOne.setLoggedIn(true);
		pageOne.setRupeeBalance(20123);
		pageOne.setTransactions(Arrays.asList(t1, t2, t3));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(pageOne);

		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenThrow(new IOException());

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(pageOne);

		TransactionPuller puller = create(scraper);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());

		int i = 0;
		while (puller.nextPage() != null && i < pageCount) {
			i++;
		}
		fail();
	}

	@Test
	public void nextPage_retry_on_connection_error_while_downloading() throws Throwable {
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
		final TransactionPage page2 = new TransactionPage();
		page2.setLoggedIn(true);
		page2.setRupeeBalance(40123);
		page2.setTransactions(Arrays.asList(t4, t5, t6));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenAnswer(new Answer<TransactionPage>() {
			private boolean threwException = false;

			@Override
			public TransactionPage answer(InvocationOnMock invocation) throws Throwable {
				if (!threwException) {
					threwException = true;
					throw new ConnectException();
				}
				return page2;
			}
		});

		RupeeTransaction t7 = shop(dg.next());
		RupeeTransaction t8 = shop(dg.next());
		RupeeTransaction t9 = shop(dg.next());
		final TransactionPage page3 = new TransactionPage();
		page3.setLoggedIn(true);
		page3.setRupeeBalance(40123);
		page3.setTransactions(Arrays.asList(t7, t8, t9));
		when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenAnswer(new Answer<TransactionPage>() {
			private boolean threwException = false;

			@Override
			public TransactionPage answer(InvocationOnMock invocation) throws Throwable {
				if (!threwException) {
					threwException = true;
					throw new SocketTimeoutException();
				}
				return page3;
			}
		});

		when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(pageOne);

		TransactionPuller puller = create(scraper);
		assertEquals(Integer.valueOf(20123), puller.getRupeeBalance());
		assertPages(puller, t1, t2, t3, t4, t5, t6, t7, t8, t9);
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

	private TransactionPuller create(TransactionPageScraper scraper) {
		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setTransactionPageScraper(scraper);
		return create(factory);
	}

	private TransactionPuller create(TransactionPullerFactory factory) {
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
}
