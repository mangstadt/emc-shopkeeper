package emcshop.model;

import static emcshop.util.TestUtils.assertIntEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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
import java.util.Iterator;
import java.util.List;
import java.util.logging.LogManager;

import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import emcshop.db.DbDao;
import emcshop.scraper.BadSessionException;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.EmcSession;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.RawTransaction;
import emcshop.scraper.RupeeTransaction;
import emcshop.scraper.RupeeTransactions;
import emcshop.scraper.ShopTransaction;
import emcshop.scraper.TransactionPuller;
import emcshop.scraper.TransactionPullerFactory;
import emcshop.util.DateGenerator;

public class UpdateModelImplTest {
	static {
		//disable log messages
		LogManager.getLogManager().reset();
	}

	private static UncaughtExceptionHandler origUncaughtExceptionHandler;
	private UncaughtExceptionHandler uncaughtExceptionHandler;

	@BeforeClass
	public static void beforeClass() {
		origUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
	}

	@AfterClass
	public static void afterClass() {
		Thread.setDefaultUncaughtExceptionHandler(origUncaughtExceptionHandler);
	}

	@Before
	public void before() {
		uncaughtExceptionHandler = mock(UncaughtExceptionHandler.class);
		Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
	}

	@Test
	public void startDownload_bad_session() throws Throwable {
		//create mock DAO
		DbDao dao = mock(DbDao.class);

		//create the model
		EmcSession session = mock(EmcSession.class);
		TransactionPullerFactory factory = new TransactionPullerFactory();
		UpdateModelImpl model = new UpdateModelImpl(factory, session, dao) {
			@Override
			TransactionPuller createPuller() {
				throw new BadSessionException();
			}
		};

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

		verify(uncaughtExceptionHandler, never()).uncaughtException(Mockito.any(Thread.class), Mockito.any(Throwable.class));
	}

	@Test
	public void startDownload_IOException_on_create_puller() throws Throwable {
		//create mock DAO
		DbDao dao = mock(DbDao.class);

		//create the model
		EmcSession session = mock(EmcSession.class);
		TransactionPullerFactory factory = new TransactionPullerFactory();
		UpdateModelImpl model = new UpdateModelImpl(factory, session, dao) {
			@Override
			TransactionPuller createPuller() throws IOException {
				throw new IOException();
			}
		};

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

		verify(uncaughtExceptionHandler).uncaughtException(Mockito.any(Thread.class), Mockito.any(IOException.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void startDownload_error_during_first_update() throws Throwable {
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
		final ShopTransaction t1 = new ShopTransaction();
		t1.setTs(dg.next());
		final RawTransaction t2 = new RawTransaction();
		t2.setTs(dg.next());
		final PaymentTransaction t3 = new PaymentTransaction();
		t3.setTs(dg.next());

		//@formatter:off
		List<?> pages = Arrays.asList(
			new RupeeTransactions(Arrays.asList(t1, t2)),
			new RupeeTransactions(Arrays.asList(t3)),
			new IOException()
		);
		//@formatter:on

		//create mock DAO
		DbDao dao = mock(DbDao.class);
		InterceptTransactionsAnswer interceptTransactions = new InterceptTransactionsAnswer();
		Mockito.doAnswer(interceptTransactions).when(dao).insertTransaction(Mockito.any(ShopTransaction.class), Mockito.anyBoolean());
		Mockito.doAnswer(interceptTransactions).when(dao).insertPaymentTransactions(Mockito.anyCollection());
		Mockito.doAnswer(interceptTransactions).when(dao).updateBonusesFees(Mockito.anyList());

		final TransactionPuller puller = mockTransactionPuller(pages);
		EmcSession session = mock(EmcSession.class);
		when(session.createHttpClient()).thenReturn(new DefaultHttpClient());
		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStopAtDate(null);
		UpdateModelImpl model = new UpdateModelImpl(factory, session, dao) {
			@Override
			TransactionPuller createPuller() {
				return puller;
			}
		};

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
		verify(uncaughtExceptionHandler, never()).uncaughtException(Mockito.any(Thread.class), Mockito.any(Throwable.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void startDownload_error() throws Throwable {
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
		final ShopTransaction t1 = new ShopTransaction();
		t1.setTs(dg.next());
		final RawTransaction t2 = new RawTransaction();
		t2.setTs(dg.next());
		final PaymentTransaction t3 = new PaymentTransaction();
		t3.setTs(dg.next());

		//@formatter:off
		List<?> pages = Arrays.asList(
			new RupeeTransactions(Arrays.asList(t1, t2)),
			new RupeeTransactions(Arrays.asList(t3)),
			new Throwable()
		);
		//@formatter:on

		//create mock DAO
		DbDao dao = mock(DbDao.class);
		InterceptTransactionsAnswer interceptTransactions = new InterceptTransactionsAnswer();
		Mockito.doAnswer(interceptTransactions).when(dao).insertTransaction(Mockito.any(ShopTransaction.class), Mockito.anyBoolean());
		Mockito.doAnswer(interceptTransactions).when(dao).insertPaymentTransactions(Mockito.anyCollection());
		Mockito.doAnswer(interceptTransactions).when(dao).updateBonusesFees(Mockito.anyList());

		final TransactionPuller puller = mockTransactionPuller(pages);
		EmcSession session = mock(EmcSession.class);
		when(session.createHttpClient()).thenReturn(new DefaultHttpClient());
		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStopAtDate(new Date());
		UpdateModelImpl model = new UpdateModelImpl(factory, session, dao) {
			@Override
			TransactionPuller createPuller() {
				return puller;
			}
		};

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
		verify(uncaughtExceptionHandler).uncaughtException(Mockito.any(Thread.class), Mockito.any(Throwable.class));

		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(2, model.getPagesDownloaded());
		assertEquals(dg.getGenerated(2), model.getOldestParsedTransactionDate());
		assertIntEquals(123, model.getRupeeBalance());
		assertNull(model.getDownloadError());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void startDownload_completed() throws Throwable {
		//create fake transactions
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
		final ShopTransaction t1 = new ShopTransaction();
		t1.setTs(dg.next());
		final RawTransaction t2 = new RawTransaction();
		t2.setTs(dg.next());
		final PaymentTransaction t3 = new PaymentTransaction();
		t3.setTs(dg.next());
		final BonusFeeTransaction t4 = new BonusFeeTransaction();
		t4.setTs(dg.next());
		final ShopTransaction t5 = new ShopTransaction();
		t5.setTs(dg.next());

		//@formatter:off
		List<?> pages = Arrays.asList(
			new RupeeTransactions(Arrays.asList(t1, t2)),
			new RupeeTransactions(Arrays.asList(t3)),
			new RupeeTransactions(Arrays.asList(t4, t5))
		);
		//@formatter:on

		//create mock DAO
		InterceptTransactionsAnswer interceptTransactions = new InterceptTransactionsAnswer();
		DbDao dao = mock(DbDao.class);
		Mockito.doAnswer(interceptTransactions).when(dao).insertTransaction(Mockito.any(ShopTransaction.class), Mockito.anyBoolean());
		Mockito.doAnswer(interceptTransactions).when(dao).insertPaymentTransactions(Mockito.anyCollection());
		Mockito.doAnswer(interceptTransactions).when(dao).updateBonusesFees(Mockito.anyList());

		//create the model
		final TransactionPuller puller = mockTransactionPuller(pages);
		EmcSession session = mock(EmcSession.class);
		when(session.createHttpClient()).thenReturn(new DefaultHttpClient());
		TransactionPullerFactory factory = new TransactionPullerFactory();
		UpdateModelImpl model = new UpdateModelImpl(factory, session, dao) {
			@Override
			TransactionPuller createPuller() {
				return puller;
			}
		};

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
		verify(uncaughtExceptionHandler, never()).uncaughtException(Mockito.any(Thread.class), Mockito.any(Throwable.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void stopDownload() throws Throwable {
		//create fake transactions
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
		final ShopTransaction t1 = new ShopTransaction();
		t1.setTs(dg.next());
		final RawTransaction t2 = new RawTransaction();
		t2.setTs(dg.next());
		final PaymentTransaction t3 = new PaymentTransaction();
		t3.setTs(dg.next());
		final BonusFeeTransaction t4 = new BonusFeeTransaction();
		t4.setTs(dg.next());
		final ShopTransaction t5 = new ShopTransaction();
		t5.setTs(dg.next());

		//@formatter:off
		List<?> pages = Arrays.asList(
			new RupeeTransactions(Arrays.asList(t1, t2)),
			50,
			new RupeeTransactions(Arrays.asList(t3)),
			new RupeeTransactions(Arrays.asList(t4, t5))
		);
		//@formatter:on

		//create mock DAO
		InterceptTransactionsAnswer interceptTransactions = new InterceptTransactionsAnswer();
		DbDao dao = mock(DbDao.class);
		Mockito.doAnswer(interceptTransactions).when(dao).insertTransaction(Mockito.any(ShopTransaction.class), Mockito.anyBoolean());
		Mockito.doAnswer(interceptTransactions).when(dao).insertPaymentTransactions(Mockito.anyCollection());
		Mockito.doAnswer(interceptTransactions).when(dao).updateBonusesFees(Mockito.anyList());

		//create the model
		final TransactionPuller puller = mockTransactionPuller(pages);
		EmcSession session = mock(EmcSession.class);
		when(session.createHttpClient()).thenReturn(new DefaultHttpClient());
		TransactionPullerFactory factory = new TransactionPullerFactory();
		UpdateModelImpl model = new UpdateModelImpl(factory, session, dao) {
			@Override
			TransactionPuller createPuller() {
				return puller;
			}
		};

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
		Thread.sleep(10); //give enough time to start processing the first page
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

		verify(puller).cancel();
		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(0, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(1, model.getPagesDownloaded());
		assertEquals(dg.getGenerated(1), model.getOldestParsedTransactionDate());
		assertIntEquals(123, model.getRupeeBalance());
		assertNull(model.getDownloadError());
		verify(uncaughtExceptionHandler, never()).uncaughtException(Mockito.any(Thread.class), Mockito.any(Throwable.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void saveTransactions() throws Throwable {
		DateGenerator dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);
		final ShopTransaction t1 = new ShopTransaction();
		t1.setTs(dg.next());
		final RawTransaction t2 = new RawTransaction();
		t2.setTs(dg.next());
		final PaymentTransaction t3 = new PaymentTransaction();
		t3.setTs(dg.next());

		//@formatter:off
		List<?> pages = Arrays.asList(
			new RupeeTransactions(Arrays.asList(t1, t2)),
			new RupeeTransactions(Arrays.asList(t3))
		);
		//@formatter:on

		//create mock DAO
		DbDao dao = mock(DbDao.class);
		InterceptTransactionsAnswer interceptTransactions = new InterceptTransactionsAnswer();
		Mockito.doAnswer(interceptTransactions).when(dao).insertTransaction(Mockito.any(ShopTransaction.class), Mockito.anyBoolean());
		Mockito.doAnswer(interceptTransactions).when(dao).insertPaymentTransactions(Mockito.anyCollection());
		Mockito.doAnswer(interceptTransactions).when(dao).updateBonusesFees(Mockito.anyList());

		final TransactionPuller puller = mockTransactionPuller(pages);
		EmcSession session = mock(EmcSession.class);
		when(session.createHttpClient()).thenReturn(new DefaultHttpClient());
		TransactionPullerFactory factory = new TransactionPullerFactory();
		factory.setStopAtDate(new Date());
		UpdateModelImpl model = new UpdateModelImpl(factory, session, dao) {
			@Override
			TransactionPuller createPuller() {
				return puller;
			}
		};

		model.saveTransactions(); //no transactions to save
		verify(dao, never()).commit();

		Thread thread = model.startDownload();
		thread.join();
		model.saveTransactions();

		verify(dao).updateBonusesFeesSince(dg.getGenerated(2));
		verify(dao).insertUpdateLog(Mockito.any(Date.class), Mockito.eq(123), Mockito.eq(1), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
		verify(dao).commit();
	}

	private static TransactionPuller mockTransactionPuller(List<?> pages) {
		TransactionPuller puller = mock(TransactionPuller.class);
		final Iterator<?> it = pages.iterator();
		when(puller.nextPage()).then(new Answer<RupeeTransactions>() {
			@Override
			public synchronized RupeeTransactions answer(InvocationOnMock invocation) throws Throwable {
				do {
					if (!it.hasNext()) {
						return null;
					}

					Object next = it.next();
					if (next instanceof Throwable) {
						throw (Throwable) next;
					}
					if (next instanceof Integer) {
						Thread.sleep((Integer) next);
						continue;
					}
					return (RupeeTransactions) next;
				} while (true);
			}
		});
		when(puller.getRupeeBalance()).thenReturn(123);

		return puller;
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
				savedTransactions.add((RupeeTransaction) arg);
				return null;
			}

			fail("Unexpected arguments found in test.");
			return null;
		}
	}
}
