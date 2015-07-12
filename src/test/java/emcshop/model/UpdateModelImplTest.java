package emcshop.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atMost;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import com.github.mangstadt.emc.net.InvalidCredentialsException;
import com.github.mangstadt.emc.rupees.RupeeTransactionReader;
import com.github.mangstadt.emc.rupees.dto.DailySigninBonus;
import com.github.mangstadt.emc.rupees.dto.HorseSummonFee;
import com.github.mangstadt.emc.rupees.dto.PaymentTransaction;
import com.github.mangstadt.emc.rupees.dto.RupeeTransaction;
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

	@Before
	public void before() throws Throwable {
		uncaughtExceptionHandler = mock(UncaughtExceptionHandler.class);
		Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);

		dg = new DateGenerator(Calendar.HOUR_OF_DAY, -1);

		dao = mock(DbDao.class);
		when(dao.isBonusFeeTransaction(any(RupeeTransaction.class))).thenAnswer(new Answer<Boolean>() {
			@Override
			public Boolean answer(InvocationOnMock invocation) throws Throwable {
				Object arg = invocation.getArguments()[0];
				return arg instanceof DailySigninBonus || arg instanceof HorseSummonFee;
			}
		});

		AppContext.init(reportSender, dao, session);
	}

	@Test
	public void startDownload_bad_session() throws Throwable {
		UpdateModelImpl model;
		{
			RupeeTransactionReader.Builder builder = new MockBuilder(new InvalidCredentialsException("username", "password"));
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

		//verify listeners
		verify(badSessionListener).actionPerformed(null);
		verify(pageDownloadedListener, never()).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		verfyUncaughtExceptionHandlerCalled(false);
	}

	@Test
	public void startDownload_IOException_on_build_reader() throws Throwable {
		UpdateModelImpl model;
		{
			RupeeTransactionReader.Builder builder = new MockBuilder(new IOException());
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

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, never()).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		verfyUncaughtExceptionHandlerCalled(true);
	}

	@Test
	public void startDownload_error_during_first_update() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		IOException thrown = new IOException();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3)
				.exception(thrown)
			.build();
			//@formatter:on

			/*
			 * The Builder has no stop-at date, which means that this is the
			 * first time the user is running an update. Therefore, the database
			 * should *not* be rolled-back if an error occurs (the user is given
			 * the chance to either accept the transactions were parsed
			 * successfully, or to discard all of them).
			 */
			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

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
		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).insertPaymentTransaction(trans(t3));
		verify(dao).isBonusFeeTransaction(t2);
		verify(dao, atMost(3)).isBonusFeeTransaction(any(RupeeTransaction.class));
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
		assertSame(thrown, model.getDownloadError());

		verfyUncaughtExceptionHandlerCalled(false);
	}

	@Test
	public void startDownload_error() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		IOException thrown = new IOException();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3)
				.exception(thrown)
			.build();
			//@formatter:on

			/*
			 * The Builder has a stop-at date, which means that this is *not*
			 * the first time the user is running an update. Therefore, the
			 * database *should* be rolled-back if an error occurs.
			 */
			RupeeTransactionReader.Builder builder = new MockBuilder(reader);
			builder.stop(dg.next());

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
		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).insertPaymentTransaction(trans(t3));
		verify(dao).isBonusFeeTransaction(t2);
		verify(dao, atMost(3)).isBonusFeeTransaction(any(RupeeTransaction.class));
		verify(dao).rollback();
		verifyNoMoreInteractions(dao);

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, never()).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		verfyUncaughtExceptionHandlerCalled(true);

		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(0, model.getPagesDownloaded());
		assertEquals(t3.getTs(), model.getOldestParsedTransactionDate());
		assertNull(model.getDownloadError());
	}

	@Test
	public void startDownload_completed() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		DailySigninBonus t4 = signinBonus();
		ShopTransaction t5 = shop();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3, t4, t5)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

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
		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).insertTransaction(trans(t5), eq(true));
		verify(dao).insertPaymentTransaction(trans(t3));

		verify(dao).isBonusFeeTransaction(t2);
		verify(dao).isBonusFeeTransaction(t4);
		verify(dao, atMost(5)).isBonusFeeTransaction(any(RupeeTransaction.class));

		verifyNoMoreInteractions(dao);

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener).actionPerformed(null);
		verify(downloadCompleteListener).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		assertEquals(2, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(1, model.getBonusFeeTransactionsDownloaded());
		assertEquals(1, model.getPagesDownloaded());
		assertEquals(t5.getTs(), model.getOldestParsedTransactionDate());
		assertNull(model.getDownloadError());
		verfyUncaughtExceptionHandlerCalled(false);
	}

	@Test
	public void startDownload_multiple_pages() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		DailySigninBonus t4 = signinBonus();
		ShopTransaction t5 = shop();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2)
				.page(t3, t4, t5)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

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
		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).insertTransaction(trans(t5), eq(true));
		verify(dao).insertPaymentTransaction(trans(t3));

		verify(dao).isBonusFeeTransaction(t2);
		verify(dao).isBonusFeeTransaction(t4);
		verify(dao, atMost(5)).isBonusFeeTransaction(any(RupeeTransaction.class));

		verifyNoMoreInteractions(dao);

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, times(2)).actionPerformed(null);
		verify(downloadCompleteListener).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		assertEquals(2, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(1, model.getBonusFeeTransactionsDownloaded());
		assertEquals(2, model.getPagesDownloaded());
		assertEquals(t5.getTs(), model.getOldestParsedTransactionDate());
		assertNull(model.getDownloadError());
		verfyUncaughtExceptionHandlerCalled(false);
	}

	@Test
	public void startDownload_ignore_old_payment_transactions() throws Throwable {
		PaymentTransaction t1 = payment();
		PaymentTransaction t2 = payment();
		PaymentTransaction t3 = payment();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

			model = new UpdateModelImpl(builder, 90 * 60 * 1000);
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
		verify(dao).insertPaymentTransaction(trans(t1));
		verify(dao, atMost(3)).isBonusFeeTransaction(any(RupeeTransaction.class));
		verifyNoMoreInteractions(dao);

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener).actionPerformed(null);
		verify(downloadCompleteListener).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		assertEquals(0, model.getShopTransactionsDownloaded());
		assertEquals(1, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(1, model.getPagesDownloaded());
		assertEquals(t3.getTs(), model.getOldestParsedTransactionDate());
		assertNull(model.getDownloadError());
		verfyUncaughtExceptionHandlerCalled(false);
	}

	@Test
	public void stopDownload() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		DailySigninBonus t4 = signinBonus();
		ShopTransaction t5 = shop();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2)
				.pause(500)
				.page(t3, t4, t5)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

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
		Thread.sleep(100); //give enough time to start processing the first page
		model.stopDownload();
		thread.join();

		//verify all the transactions were inserted into the DAO
		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).isBonusFeeTransaction(t2);
		verify(dao, atMost(2)).isBonusFeeTransaction(any(RupeeTransaction.class));
		verifyNoMoreInteractions(dao);

		//verify listeners
		verify(badSessionListener, never()).actionPerformed(null);
		verify(pageDownloadedListener, never()).actionPerformed(null);
		verify(downloadCompleteListener, never()).actionPerformed(null);
		verify(downloadErrorListener, never()).actionPerformed(null);

		assertEquals(1, model.getShopTransactionsDownloaded());
		assertEquals(0, model.getPaymentTransactionsDownloaded());
		assertEquals(0, model.getBonusFeeTransactionsDownloaded());
		assertEquals(0, model.getPagesDownloaded());
		assertEquals(t2.getTs(), model.getOldestParsedTransactionDate());
		assertNull(model.getDownloadError());
		verfyUncaughtExceptionHandlerCalled(false);
	}

	@Test
	public void saveTransactions_no_bonus_fees() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

			model = new UpdateModelImpl(builder, null);
		}

		model.saveTransactions(); //no transactions to save
		verifyNoMoreInteractions(dao);

		model.startDownload().join();

		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).isBonusFeeTransaction(t2);
		verify(dao).insertPaymentTransaction(trans(t3));
		verify(dao, atMost(3)).isBonusFeeTransaction(any(RupeeTransaction.class));
		verifyNoMoreInteractions(dao);

		model.saveTransactions();

		verify(dao).updateBonusesFeesSince(t3.getTs());
		verify(dao).insertUpdateLog(any(Date.class), eq(123), eq(1), eq(1), eq(0), anyLong());
		verify(dao).commit();
		verifyNoMoreInteractions(dao);
	}

	@Test
	public void saveTransactions_one_bonus_fee() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		DailySigninBonus t4 = signinBonus();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3, t4)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

			model = new UpdateModelImpl(builder, null);
		}

		model.saveTransactions(); //no transactions to save
		verifyNoMoreInteractions(dao);

		model.startDownload().join();

		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).isBonusFeeTransaction(t2);
		verify(dao).insertPaymentTransaction(trans(t3));
		verify(dao).isBonusFeeTransaction(t4);
		verify(dao, atMost(4)).isBonusFeeTransaction(any(RupeeTransaction.class));
		verifyNoMoreInteractions(dao);

		model.saveTransactions();

		verify(dao).updateBonusesFeesSince(t4.getTs());
		verify(dao).updateBonusesFeesLatestTransactionDate(t4.getTs());

		Map<Class<? extends RupeeTransaction>, MutableInt> totals = new HashMap<Class<? extends RupeeTransaction>, MutableInt>();
		totals.put(DailySigninBonus.class, new MutableInt(100));
		verify(dao).updateBonusFeeTotals(totals);

		verify(dao).insertUpdateLog(any(Date.class), eq(123), eq(1), eq(1), eq(1), anyLong());
		verify(dao).commit();
		verifyNoMoreInteractions(dao);
	}

	@Test
	public void saveTransactions_multiple_bonus_fees() throws Throwable {
		ShopTransaction t1 = shop();
		RupeeTransaction t2 = raw();
		PaymentTransaction t3 = payment();
		HorseSummonFee t4 = horseFee();
		DailySigninBonus t5 = signinBonus();
		HorseSummonFee t6 = horseFee();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3, t4, t5, t6)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

			model = new UpdateModelImpl(builder, null);
		}

		model.saveTransactions(); //no transactions to save
		verifyNoMoreInteractions(dao);

		model.startDownload().join();

		verify(dao).insertTransaction(trans(t1), eq(true));
		verify(dao).isBonusFeeTransaction(t2);
		verify(dao).insertPaymentTransaction(trans(t3));
		verify(dao).isBonusFeeTransaction(t4);
		verify(dao).isBonusFeeTransaction(t5);
		verify(dao).isBonusFeeTransaction(t6);
		verify(dao, atMost(5)).isBonusFeeTransaction(any(RupeeTransaction.class));
		verifyNoMoreInteractions(dao);

		model.saveTransactions();

		verify(dao).updateBonusesFeesSince(t6.getTs());
		verify(dao).updateBonusesFeesLatestTransactionDate(t4.getTs());

		Map<Class<? extends RupeeTransaction>, MutableInt> totals = new HashMap<Class<? extends RupeeTransaction>, MutableInt>();
		totals.put(DailySigninBonus.class, new MutableInt(100));
		totals.put(HorseSummonFee.class, new MutableInt(200));
		verify(dao).updateBonusFeeTotals(totals);

		verify(dao).insertUpdateLog(any(Date.class), eq(123), eq(1), eq(1), eq(3), anyLong());
		verify(dao).commit();
		verifyNoMoreInteractions(dao);
	}

	@Test
	public void item_name_translation() throws Throwable {
		final ShopTransaction t1 = new ShopTransaction.Builder().ts(dg.next()).item("Apple").build();
		final ShopTransaction t2 = new ShopTransaction.Builder().ts(dg.next()).item("Black Stn Glass").build();
		final ShopTransaction t3 = new ShopTransaction.Builder().ts(dg.next()).item("FooBar").build();

		UpdateModelImpl model;
		{
			//@formatter:off
			RupeeTransactionReader reader = new MockReaderBuilder()
				.page(t1, t2, t3)
			.build();
			//@formatter:on

			RupeeTransactionReader.Builder builder = new MockBuilder(reader);

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

		verify(dao).insertTransaction(argThat(new ArgumentMatcher<ShopTransactionDb>() {
			@Override
			public boolean matches(Object argument) {
				ShopTransactionDb arg = (ShopTransactionDb) argument;
				return arg.getTs().equals(t1.getTs()) && arg.getItem().equals("Apple");
			}
		}), eq(true));

		verify(dao).insertTransaction(argThat(new ArgumentMatcher<ShopTransactionDb>() {
			@Override
			public boolean matches(Object argument) {
				ShopTransactionDb arg = (ShopTransactionDb) argument;
				return arg.getTs().equals(t2.getTs()) && arg.getItem().equals("Black Glass");
			}
		}), eq(true));

		verify(dao).insertTransaction(argThat(new ArgumentMatcher<ShopTransactionDb>() {
			@Override
			public boolean matches(Object argument) {
				ShopTransactionDb arg = (ShopTransactionDb) argument;
				return arg.getTs().equals(t3.getTs()) && arg.getItem().equals("FooBar");
			}
		}), eq(true));

		verifyNoMoreInteractions(dao);
	}

	private void verfyUncaughtExceptionHandlerCalled(boolean called) {
		VerificationMode mode = called ? times(1) : never();
		verify(uncaughtExceptionHandler, mode).uncaughtException(any(Thread.class), any(Throwable.class));
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

	private DailySigninBonus signinBonus() {
		return new DailySigninBonus.Builder().amount(100).ts(dg.next()).build();
	}

	private HorseSummonFee horseFee() {
		return new HorseSummonFee.Builder().amount(100).ts(dg.next()).build();
	}

	private static ShopTransactionDb trans(final ShopTransaction transaction) {
		return argThat(new ArgumentMatcher<ShopTransactionDb>() {
			@Override
			public boolean matches(Object argument) {
				ShopTransactionDb arg = (ShopTransactionDb) argument;
				return arg.getTs().equals(transaction.getTs());
			}
		});
	}

	private static PaymentTransactionDb trans(final PaymentTransaction transaction) {
		return argThat(new ArgumentMatcher<PaymentTransactionDb>() {
			@Override
			public boolean matches(Object argument) {
				PaymentTransactionDb arg = (PaymentTransactionDb) argument;
				return arg.getTs().equals(transaction.getTs());
			}
		});
	}

	private class MockReaderBuilder {
		private final List<List<RupeeTransaction>> pages = new ArrayList<List<RupeeTransaction>>();
		private final Map<Integer, IOException> exceptions = new HashMap<Integer, IOException>();
		private final Map<Integer, Integer> pauses = new HashMap<Integer, Integer>();
		private int curPage = 0;

		public MockReaderBuilder exception(IOException t) {
			exceptions.put(pages.size(), t);
			return this;
		}

		public MockReaderBuilder pause(int millis) {
			pauses.put(pages.size(), millis);
			return this;
		}

		public MockReaderBuilder page(RupeeTransaction... transactions) {
			pages.add(Arrays.asList(transactions));
			return this;
		}

		public RupeeTransactionReader build() throws IOException {
			RupeeTransactionReader reader = mock(RupeeTransactionReader.class);

			when(reader.next()).thenAnswer(new Answer<RupeeTransaction>() {
				private Iterator<RupeeTransaction> it = pages.get(curPage).iterator();

				@Override
				public RupeeTransaction answer(InvocationOnMock invocation) throws IOException {
					if (!it.hasNext()) {
						curPage++;
						it = (curPage < pages.size()) ? pages.get(curPage).iterator() : null;
					}

					IOException t = exceptions.remove(curPage);
					if (t != null) {
						throw t;
					}

					Integer pause = pauses.remove(curPage);
					if (pause != null) {
						try {
							Thread.sleep(pause);
						} catch (InterruptedException e) {
							//empty
						}
					}

					return (it == null) ? null : it.next();
				}
			});

			when(reader.getCurrentPageNumber()).thenAnswer(new Answer<Integer>() {
				@Override
				public Integer answer(InvocationOnMock invocation) throws Throwable {
					return curPage + 1;
				}
			});

			when(reader.getRupeeBalance()).thenReturn(123);

			return reader;
		}
	}

	private class MockBuilder extends RupeeTransactionReader.Builder {
		private final RupeeTransactionReader mockReader;
		private final Exception exception;

		public MockBuilder(Exception exception) {
			super(null);
			this.mockReader = null;
			this.exception = exception;
		}

		public MockBuilder(RupeeTransactionReader mockReader) {
			super(null);
			this.mockReader = mockReader;
			this.exception = null;
		}

		@Override
		public RupeeTransactionReader build() throws IOException {
			if (exception instanceof IOException) {
				throw (IOException) exception;
			}
			if (exception instanceof RuntimeException) {
				throw (RuntimeException) exception;
			}
			return mockReader;
		}
	}
}
