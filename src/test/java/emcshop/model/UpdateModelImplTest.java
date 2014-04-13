package emcshop.model;

import static emcshop.util.TestUtils.assertIntEquals;
import static emcshop.util.TestUtils.gte;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.LogManager;

import org.apache.http.client.HttpClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import emcshop.AppContext;
import emcshop.ReportSender;
import emcshop.db.DbDao;
import emcshop.scraper.BadSessionException;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.RawTransaction;
import emcshop.scraper.RupeeTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.scraper.TransactionPage;
import emcshop.scraper.TransactionPageScraper;
import emcshop.scraper.TransactionPullerFactory;
import emcshop.util.DateGenerator;

public class UpdateModelImplTest {
	private static UncaughtExceptionHandler origUncaughtExceptionHandler;
	private static EmcSession session;
	private static ReportSender reportSender;
	private UncaughtExceptionHandler uncaughtExceptionHandler;
	private DateGenerator dg;
	private DbDao dao;
	private InterceptTransactionsAnswer interceptTransactions;

	@BeforeClass
	public static void beforeClass() {
		LogManager.getLogManager().reset();
		session = mock(EmcSession.class);
		reportSender = mock(ReportSender.class);
		origUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
	}

	@AfterClass
	public static void afterClass() {
		Thread.setDefaultUncaughtExceptionHandler(origUncaughtExceptionHandler);
	}

	@SuppressWarnings("unchecked")
	@Before
	public void before() throws Throwable {
		uncaughtExceptionHandler = mock(UncaughtExceptionHandler.class);
		Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);

		dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		dao = mock(DbDao.class);
		interceptTransactions = new InterceptTransactionsAnswer();
		doAnswer(interceptTransactions).when(dao).insertTransaction(any(ShopTransaction.class), anyBoolean());
		doAnswer(interceptTransactions).when(dao).insertPaymentTransactions(anyCollection());
		doAnswer(interceptTransactions).when(dao).updateBonusesFees(anyList());

		AppContext.init(reportSender, dao);
	}

	@Test
	public void startDownload_bad_session() throws Throwable {
		UpdateModelImpl model;
		{
			TransactionPullerFactory factory = mock(TransactionPullerFactory.class);
			when(factory.create(session)).thenThrow(new BadSessionException());
			model = new UpdateModelImpl(factory, session);
		}

		//register listeners
		ActionListener badSessionListener = mock(ActionListener.class);
		model.addBadSessionListener(badSessionListener);
		ActionListener pageDownloadedListener = mock(ActionListener.class);
		model.addPageDownloadedListener(pageDownloadedListener);
		ActionListener downloadCompleteListener = mock(ActionListener.class);
		model.addDownloadCompleteListener(downloadCompleteListener);
		ActionListener downloadErrorListener = mock(ActionListener.class);
		model.addDownloadErrorListener(downloadErrorListener);

		Thread thread = model.startDownload();
		thread.join();

		//verify listeners
		verify(badSessionListener).actionPerformed(null);
		verify(pageDownloadedListener, never()).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		verify(uncaughtExceptionHandler, never()).uncaughtException(any(Thread.class), any(Throwable.class));
	}

	@Test
	public void startDownload_IOException_on_create_puller() throws Throwable {
		UpdateModelImpl model;
		{
			TransactionPullerFactory factory = mock(TransactionPullerFactory.class);
			when(factory.create(session)).thenThrow(new IOException());
			model = new UpdateModelImpl(factory, session);
		}

		//register listeners
		ActionListener badSessionListener = mock(ActionListener.class);
		model.addBadSessionListener(badSessionListener);
		ActionListener pageDownloadedListener = mock(ActionListener.class);
		model.addPageDownloadedListener(pageDownloadedListener);
		ActionListener downloadCompleteListener = mock(ActionListener.class);
		model.addDownloadCompleteListener(downloadCompleteListener);
		ActionListener downloadErrorListener = mock(ActionListener.class);
		model.addDownloadErrorListener(downloadErrorListener);

		Thread thread = model.startDownload();
		thread.join();

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, never()).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		verify(uncaughtExceptionHandler).uncaughtException(any(Thread.class), any(IOException.class));
	}

	@Test
	public void startDownload_error_during_first_update() throws Throwable {
		ShopTransaction t1 = shop();
		RawTransaction t2 = raw();
		PaymentTransaction t3 = payment();

		UpdateModelImpl model;
		{
			TransactionPageScraper scraper = scraper().page(t1, t2).page(t3).page(new IOException()).done();

			TransactionPullerFactory factory = new TransactionPullerFactory();
			factory.setTransactionPageScraper(scraper);
			factory.setThreadCount(1);
			factory.setStopAtDate(null);

			model = new UpdateModelImpl(factory, session);
		}

		//register listeners
		ActionListener badSessionListener = mock(ActionListener.class);
		model.addBadSessionListener(badSessionListener);
		ActionListener pageDownloadedListener = mock(ActionListener.class);
		model.addPageDownloadedListener(pageDownloadedListener);
		ActionListener downloadCompleteListener = mock(ActionListener.class);
		model.addDownloadCompleteListener(downloadCompleteListener);
		ActionListener downloadErrorListener = mock(ActionListener.class);
		model.addDownloadErrorListener(downloadErrorListener);

		Thread thread = model.startDownload();
		thread.join();

		//verify all the transactions were inserted into the DAO
		List<RupeeTransaction> savedTransactions = interceptTransactions.savedTransactions;
		assertEquals(2, savedTransactions.size());
		assertTrue(savedTransactions.contains(t1));
		assertTrue(savedTransactions.contains(t3));

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, times(2)).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener).actionPerformed(null);

		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(2, model.getPagesDownloaded());
		assertEquals(dg.getGenerated(2), model.getOldestParsedTransactionDate());
		assertIntEquals(123, model.getRupeeBalance());
		assertNotNull(model.getDownloadError());
		verify(uncaughtExceptionHandler, never()).uncaughtException(any(Thread.class), any(Throwable.class));
	}

	@Test
	public void startDownload_error() throws Throwable {
		ShopTransaction t1 = shop();
		RawTransaction t2 = raw();
		PaymentTransaction t3 = payment();

		UpdateModelImpl model;
		{
			TransactionPageScraper scraper = scraper().page(t1, t2).page(t3).page(new IOException()).done();

			TransactionPullerFactory factory = new TransactionPullerFactory();
			factory.setTransactionPageScraper(scraper);
			factory.setThreadCount(1);
			factory.setStopAtDate(dg.next());
			model = new UpdateModelImpl(factory, session);
		}

		//register listeners
		ActionListener badSessionListener = mock(ActionListener.class);
		model.addBadSessionListener(badSessionListener);
		ActionListener pageDownloadedListener = mock(ActionListener.class);
		model.addPageDownloadedListener(pageDownloadedListener);
		ActionListener downloadCompleteListener = mock(ActionListener.class);
		model.addDownloadCompleteListener(downloadCompleteListener);
		ActionListener downloadErrorListener = mock(ActionListener.class);
		model.addDownloadErrorListener(downloadErrorListener);

		Thread thread = model.startDownload();
		thread.join();

		//verify all the transactions were inserted into the DAO
		List<RupeeTransaction> savedTransactions = interceptTransactions.savedTransactions;
		assertEquals(2, savedTransactions.size());
		assertTrue(savedTransactions.contains(t1));
		assertTrue(savedTransactions.contains(t3));

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, times(2)).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		verify(dao).rollback();
		verify(uncaughtExceptionHandler).uncaughtException(any(Thread.class), any(Throwable.class));

		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(2, model.getPagesDownloaded());
		assertEquals(dg.getGenerated(2), model.getOldestParsedTransactionDate());
		assertIntEquals(123, model.getRupeeBalance());
		assertNull(model.getDownloadError());
	}

	@Test
	public void startDownload_completed() throws Throwable {
		ShopTransaction t1 = shop();
		RawTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		BonusFeeTransaction t4 = bonusFee();
		ShopTransaction t5 = shop();

		UpdateModelImpl model;
		{
			TransactionPageScraper scraper = scraper().page(t1, t2).page(t3).page(t4, t5).done();

			TransactionPullerFactory factory = new TransactionPullerFactory();
			factory.setTransactionPageScraper(scraper);
			factory.setThreadCount(1);
			model = new UpdateModelImpl(factory, session);
		}

		//register listeners
		ActionListener badSessionListener = mock(ActionListener.class);
		model.addBadSessionListener(badSessionListener);
		ActionListener pageDownloadedListener = mock(ActionListener.class);
		model.addPageDownloadedListener(pageDownloadedListener);
		ActionListener downloadCompleteListener = mock(ActionListener.class);
		model.addDownloadCompleteListener(downloadCompleteListener);
		ActionListener downloadErrorListener = mock(ActionListener.class);
		model.addDownloadErrorListener(downloadErrorListener);

		Thread thread = model.startDownload();
		thread.join();

		//verify all the transactions were inserted into the DAO
		List<RupeeTransaction> savedTransactions = interceptTransactions.savedTransactions;
		assertEquals(4, savedTransactions.size());
		assertTrue(savedTransactions.contains(t1));
		assertTrue(savedTransactions.contains(t3));
		assertTrue(savedTransactions.contains(t4));
		assertTrue(savedTransactions.contains(t5));

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, times(3)).actionPerformed(null);
		verify(downloadCompleteListener).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		assertEquals(2, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(1, model.getBonusFeeTransactionsDownloaded());
		assertEquals(3, model.getPagesDownloaded());
		assertEquals(dg.getGenerated(4), model.getOldestParsedTransactionDate());
		assertIntEquals(123, model.getRupeeBalance());
		assertNull(model.getDownloadError());
		verify(uncaughtExceptionHandler, never()).uncaughtException(any(Thread.class), any(Throwable.class));
	}

	@Test
	public void stopDownload() throws Throwable {
		ShopTransaction t1 = shop();
		RawTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		BonusFeeTransaction t4 = bonusFee();
		ShopTransaction t5 = shop();

		UpdateModelImpl model;
		{
			TransactionPageScraper scraper = scraper().page(t1, t2).pause(500).page(t3).page(t4, t5).done();

			TransactionPullerFactory factory = new TransactionPullerFactory();
			factory.setTransactionPageScraper(scraper);
			factory.setThreadCount(1);
			model = new UpdateModelImpl(factory, session);
		}

		//register listeners
		ActionListener badSessionListener = mock(ActionListener.class);
		model.addBadSessionListener(badSessionListener);
		ActionListener pageDownloadedListener = mock(ActionListener.class);
		model.addPageDownloadedListener(pageDownloadedListener);
		ActionListener downloadCompleteListener = mock(ActionListener.class);
		model.addDownloadCompleteListener(downloadCompleteListener);
		ActionListener downloadErrorListener = mock(ActionListener.class);
		model.addDownloadErrorListener(downloadErrorListener);

		Thread thread = model.startDownload();
		Thread.sleep(100); //give enough time to start processing the first page
		model.stopDownload();
		thread.join();

		//verify all the transactions were inserted into the DAO
		List<RupeeTransaction> savedTransactions = interceptTransactions.savedTransactions;
		assertEquals(1, savedTransactions.size());
		assertTrue(savedTransactions.contains(t1));

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, times(1)).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(0, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(1, model.getPagesDownloaded());
		assertEquals(dg.getGenerated(1), model.getOldestParsedTransactionDate());
		assertIntEquals(123, model.getRupeeBalance());
		assertNull(model.getDownloadError());
		verify(uncaughtExceptionHandler, never()).uncaughtException(any(Thread.class), any(Throwable.class));
	}

	@Test
	public void saveTransactions() throws Throwable {
		ShopTransaction t1 = shop();
		RawTransaction t2 = raw();
		PaymentTransaction t3 = payment();

		UpdateModelImpl model;
		{
			TransactionPageScraper scraper = scraper().page(t1, t2).page(t3).done();

			TransactionPullerFactory factory = new TransactionPullerFactory();
			factory.setTransactionPageScraper(scraper);
			factory.setThreadCount(1);
			model = new UpdateModelImpl(factory, session);
		}

		model.saveTransactions(); //no transactions to save
		verify(dao, never()).commit();

		Thread thread = model.startDownload();
		thread.join();
		model.saveTransactions();

		verify(dao).updateBonusesFeesSince(dg.getGenerated(2));
		verify(dao).insertUpdateLog(any(Date.class), eq(123), eq(1), eq(1), eq(0), anyLong());
		verify(dao).commit();
	}

	private ShopTransaction shop() {
		ShopTransaction t = new ShopTransaction();
		t.setTs(dg.next());
		return t;
	}

	private RawTransaction raw() {
		RawTransaction t = new RawTransaction();
		t.setTs(dg.next());
		return t;
	}

	private PaymentTransaction payment() {
		PaymentTransaction t = new PaymentTransaction();
		t.setTs(dg.next());
		return t;
	}

	private BonusFeeTransaction bonusFee() {
		BonusFeeTransaction t = new BonusFeeTransaction();
		t.setTs(dg.next());
		return t;
	}

	private static ScraperBuilder scraper() {
		return new ScraperBuilder();
	}

	@SuppressWarnings("unchecked")
	private static class InterceptTransactionsAnswer implements Answer<Object> {
		private final List<RupeeTransaction> savedTransactions = new ArrayList<RupeeTransaction>();

		@Override
		public Object answer(InvocationOnMock invocation) throws Throwable {
			Object arg = invocation.getArguments()[0];

			if (arg instanceof Collection) {
				Collection<? extends RupeeTransaction> transactions = (Collection<? extends RupeeTransaction>) arg;
				savedTransactions.addAll(transactions);
				return null;
			}

			if (arg instanceof RupeeTransaction) {
				RupeeTransaction transaction = (RupeeTransaction) arg;
				savedTransactions.add(transaction);
				return null;
			}

			fail("Unexpected arguments found in test.");
			return null;
		}
	}

	private static class ScraperBuilder {
		private final TransactionPageScraper scraper = mock(TransactionPageScraper.class);
		private int pageCount = 1;
		private TransactionPage page1;
		private int pause = 0;

		public ScraperBuilder page(RupeeTransaction... transactions) throws IOException {
			final TransactionPage page = new TransactionPage();
			page.setTransactions(Arrays.asList(transactions));
			page.setRupeeBalance(123);

			if (pageCount == 1) {
				page1 = page;
			}

			if (pause > 0) {
				when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenAnswer(new Answer<TransactionPage>() {
					private long sleep = pause; //copy the current value of "pause"

					@Override
					public TransactionPage answer(InvocationOnMock invocation) throws Throwable {
						Thread.sleep(sleep);
						return page;
					}
				});
				pause = 0;
			} else {
				when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenReturn(page);
			}

			return this;
		}

		public ScraperBuilder page(IOException e) throws IOException {
			when(scraper.download(eq(pageCount++), any(HttpClient.class))).thenThrow(e);
			return this;
		}

		public ScraperBuilder pause(int ms) throws IOException {
			pause = ms;
			return this;
		}

		public TransactionPageScraper done() throws IOException {
			when(scraper.download(gte(pageCount), any(HttpClient.class))).thenReturn(page1);
			return scraper;
		}
	}
}
