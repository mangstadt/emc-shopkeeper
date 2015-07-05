package emcshop.model;

import static emcshop.util.TestUtils.assertIntEquals;
import static emcshop.util.TestUtils.gte;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

import org.apache.derby.iapi.store.raw.xact.RawTransaction;
import org.apache.http.client.HttpClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.rupees.RupeeTransactionPageScraper;
import com.github.mangstadt.emc.rupees.RupeeTransactionReader;
import com.github.mangstadt.emc.rupees.dto.DailySigninBonus;
import com.github.mangstadt.emc.rupees.dto.PaymentTransaction;
import com.github.mangstadt.emc.rupees.dto.RupeeTransaction;
import com.github.mangstadt.emc.rupees.dto.RupeeTransactionPage;
import com.github.mangstadt.emc.rupees.dto.ShopTransaction;

import emcshop.AppContext;
import emcshop.ReportSender;
import emcshop.db.DbDao;
import emcshop.db.PaymentTransactionDb;
import emcshop.db.ShopTransactionDb;
import emcshop.scraper.EmcSession;
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
		doAnswer(interceptTransactions).when(dao).insertTransaction(any(ShopTransactionDb.class), anyBoolean());
		doAnswer(interceptTransactions).when(dao).insertPaymentTransaction(any(PaymentTransactionDb.class));
		doAnswer(interceptTransactions).when(dao).updateBonusFeeTotals(Mockito.anyMap());

		AppContext.init(reportSender, dao, session);
	}

	@Test
	public void startDownload_bad_session() throws Throwable {
		UpdateModelImpl model;
		{
			RupeeTransactionReader.Builder builder = mock(RupeeTransactionReader.Builder.class);
			when(builder.build()).thenThrow(new InvalidCredentialsException("username", "password"));
			model = new UpdateModelImpl(builder, null);
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
	public void startDownload_IOException_on_build_reader() throws Throwable {
		UpdateModelImpl model;
		{
			RupeeTransactionReader.Builder builder = mock(RupeeTransactionReader.Builder.class);
			when(builder.build()).thenThrow(new IOException());
			model = new UpdateModelImpl(builder, null);
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
		final ShopTransaction t1 = shop();
		final RupeeTransaction t2 = raw();
		final PaymentTransaction t3 = payment();
		final Exception thrown = new IOException();

		UpdateModelImpl model;
		{
			RupeeTransactionReader reader = mock(RupeeTransactionReader.class);
			when(reader.next()).thenAnswer(new Answer<RupeeTransaction>() {
				private int count = 1;

				@Override
				public RupeeTransaction answer(InvocationOnMock invocation) throws Throwable {
					switch (count++) {
					case 1:
						return t1;
					case 2:
						return t2;
					case 3:
						return t3;
					case 4:
						throw thrown;
					}

					fail("next() called too many times.");
					return null;
				}
			});
			when(reader.getCurrentPageNumber()).thenReturn(1);

			RupeeTransactionReader.Builder builder = mock(RupeeTransactionReader.Builder.class);
			when(builder.build()).thenReturn(reader);

			model = new UpdateModelImpl(builder, null);
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

		model.startDownload().join();

		//verify all the transactions were inserted into the DAO
		verify(dao).insertTransaction(any(ShopTransactionDb.class), eq(true));
		verify(dao).insertPaymentTransaction(any(PaymentTransactionDb.class));
		verifyNoMoreInteractions(dao);

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, never()).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener).actionPerformed(null);

		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(0, model.getPagesDownloaded()); //it never finished downloading the first page
		assertEquals(t3.getTs(), model.getOldestParsedTransactionDate());
		assertIntEquals(123, model.getRupeeBalance());
		assertSame(thrown, model.getDownloadError());

		verify(uncaughtExceptionHandler, never()).uncaughtException(any(Thread.class), any(Throwable.class));
	}

	@Test
	public void startDownload_error() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();

		UpdateModelImpl model;
		{
			TransactionPageScraper scraper = scraper().page(t1, t2).page(t3).page(new IOException()).done();

			TransactionPullerFactory factory = new TransactionPullerFactory();
			factory.setTransactionPageScraper(scraper);
			factory.setThreadCount(1);
			factory.setStopAtDate(dg.next());
			model = new UpdateModelImpl(factory);
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
			model = new UpdateModelImpl(factory);
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
			model = new UpdateModelImpl(factory);
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
			model = new UpdateModelImpl(factory);
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
		return new ShopTransaction.Builder().ts(dg.next()).build();
	}

	private RupeeTransaction raw() {
		return new RupeeTransaction.Builder<RupeeTransaction.Builder<?>>().ts(dg.next()).build();
	}

	private PaymentTransaction payment() {
		return new PaymentTransaction.Builder().ts(dg.next()).build();
	}

	private DailySigninBonus bonusFee() {
		return new DailySigninBonus.Builder().ts(dg.next()).build();
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
		private final RupeeTransactionPageScraper scraper = mock(RupeeTransactionPageScraper.class);
		private int pageCount = 1;
		private RupeeTransactionPage page1;
		private int pause = 0;

		public ScraperBuilder page(RupeeTransaction... transactions) throws IOException {
			final RupeeTransactionPage page = new RupeeTransactionPage(123, pageCount, null, Arrays.asList(transactions));

			if (pageCount == 1) {
				page1 = page;
			}

			if (pause > 0) {
				when(scraper.download(eq(pageCount), any(HttpClient.class))).thenAnswer(new Answer<TransactionPage>() {
					private long sleep = pause; //copy the current value of "pause"

					@Override
					public TransactionPage answer(InvocationOnMock invocation) throws Throwable {
						Thread.sleep(sleep);
						return page;
					}
				});
				pause = 0;
			} else {
				when(scraper.download(eq(pageCount), any(HttpClient.class))).thenReturn(page);
			}

			pageCount++;
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
