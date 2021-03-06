package emcshop.db;

import static emcshop.util.TestUtils.assertIntEquals;
import static emcshop.util.TestUtils.timestamp;
import static emcshop.util.TimeUtils.toLocalDateTime;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.mangstadt.emc.rupees.dto.DailySigninBonus;
import com.github.mangstadt.emc.rupees.dto.EggifyFee;
import com.github.mangstadt.emc.rupees.dto.HorseSummonFee;
import com.github.mangstadt.emc.rupees.dto.LockTransaction;
import com.github.mangstadt.emc.rupees.dto.RupeeTransaction;
import com.github.mangstadt.emc.rupees.dto.VaultFee;
import com.github.mangstadt.emc.rupees.dto.VoteBonus;

import emcshop.ItemIndex;
import emcshop.util.DateGenerator;

public class DirbyDbDaoTest {
	private static DirbyDbDao dao;
	private static Connection conn;
	private static int appleId, diamondId, notchId;

	@BeforeClass
	public static void beforeClass() {
		//disable log messages
		LogManager.getLogManager().reset();

		try {
			dao = new DirbyMemoryDbDao("test");
			conn = dao.getConnection();

			notchId = players().name("Notch").firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).insert();
			dao.commit();

			appleId = items().name("Apple").id();
			diamondId = items().name("Diamond").id();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Before
	public void before() {
		MigrationSprocs.populateItemsTableCalled = false;
		MigrationSprocs.updateItemNamesCalled = false;
	}

	@After
	public void after() {
		conn = dao.getConnection(); //some tests use their own DAO instance
		dao.rollback();
	}

	@AfterClass
	public static void afterClass() throws Exception {
		dao.close();
	}

	@Test
	public void selectDbVersion() throws Exception {
		assertEquals(dao.getAppDbVersion(), dao.selectDbVersion());
	}

	@Test
	public void upsertDbVersion() throws Exception {
		dao.upsertDbVersion(100);
		assertIntEquals(100, meta().dbSchemaVersion());
	}

	@Test
	public void selectRupeeBalance() throws Exception {
		assertNull(dao.selectRupeeBalance());

		DateGenerator dg = new DateGenerator();
		updateLog().ts(dg.next()).rupeeBalance(4321).insert();
		updateLog().ts(dg.next()).rupeeBalance(1234).insert();
		assertIntEquals(1234, dao.selectRupeeBalance());
	}

	@Test
	public void insertUpdateLog() throws Exception {
		DateGenerator dg = new DateGenerator();
		dao.insertUpdateLog(dg.next(), 123, 1, 2, 3, Duration.ofSeconds(1));
		updateLog().ts(dg.getGenerated(0)).rupeeBalance(123).shopTransactionCount(1).paymentTransactionCount(2).bonusFeeTransactionCount(3).timeTaken(1000).test();
	}

	@Test
	public void getLatestUpdateDate() throws Exception {
		assertNull(dao.getLatestUpdateDate());

		DateGenerator dg = new DateGenerator();
		updateLog().ts(dg.next()).insert();
		assertTimestampEquals(dg.getGenerated(0), dao.getLatestUpdateDate());

		updateLog().ts(dg.next()).insert();
		assertTimestampEquals(dg.getGenerated(1), dao.getLatestUpdateDate());
	}

	@Test
	public void getSecondLatestUpdateDate() throws Exception {
		assertNull(dao.getSecondLatestUpdateDate());

		DateGenerator dg = new DateGenerator();
		updateLog().ts(dg.next()).insert();
		assertNull(dao.getSecondLatestUpdateDate());

		updateLog().ts(dg.next()).insert();
		assertTimestampEquals(dg.getGenerated(0), dao.getSecondLatestUpdateDate());
	}

	@Test
	public void listener_onCreate() throws Exception {
		DbListenerImpl listener = new DbListenerImpl();
		new DirbyMemoryDbDao("listener_onCreate", listener);
		assertEquals(1, listener.onCreate);
	}

	@Test
	public void selsertPlayer() throws Exception {
		Player player = dao.selsertPlayer("Notch");
		players().id(notchId).name("Notch").firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).test(player);

		player = dao.selsertPlayer("notch");
		players().id(notchId).name("Notch").firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).test(player);

		player = dao.selsertPlayer("Jeb");
		players().name("Jeb").firstSeen(null).lastSeen(null).test(player);
	}

	@Test
	public void calculatePlayersFirstLastSeenDates() throws Exception {
		int jeb = players().name("Jeb").insert();
		int dinnerbone = players().name("Dinnerbone").insert();

		transactions().ts(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).player(notchId).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).player(notchId).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 3, 0, 0, 0)).player(notchId).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 4, 0, 0, 0)).player(jeb).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 5, 0, 0, 0)).player(jeb).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 6, 0, 0, 0)).player(dinnerbone).insert();

		dao.calculatePlayersFirstLastSeenDates();

		ResultSet rs = players().all();

		rs.next();
		assertEquals("Notch", rs.getString("name"));
		assertEquals(LocalDateTime.of(2014, 1, 1, 0, 0, 0), toLocalDateTime(rs.getTimestamp("first_seen")));
		assertEquals(LocalDateTime.of(2014, 1, 3, 0, 0, 0), toLocalDateTime(rs.getTimestamp("last_seen")));

		rs.next();
		assertEquals("Jeb", rs.getString("name"));
		assertEquals(LocalDateTime.of(2014, 1, 4, 0, 0, 0), toLocalDateTime(rs.getTimestamp("first_seen")));
		assertEquals(LocalDateTime.of(2014, 1, 5, 0, 0, 0), toLocalDateTime(rs.getTimestamp("last_seen")));

		rs.next();
		assertEquals("Dinnerbone", rs.getString("name"));
		assertEquals(LocalDateTime.of(2014, 1, 6, 0, 0, 0), toLocalDateTime(rs.getTimestamp("first_seen")));
		assertEquals(LocalDateTime.of(2014, 1, 6, 0, 0, 0), toLocalDateTime(rs.getTimestamp("last_seen")));

		assertFalse(rs.next());
	}

	@Test
	public void getEarliestTransactionDate() throws Exception {
		assertNull(dao.getEarliestTransactionDate());

		transactions().ts(LocalDateTime.of(2014, 1, 5, 0, 0, 0)).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 10, 0, 0, 0)).insert();

		LocalDateTime actual = dao.getEarliestTransactionDate();
		LocalDateTime expected = LocalDateTime.of(2014, 1, 1, 0, 0, 0);
		assertEquals(expected, actual);
	}

	@Test
	public void getLatestTransactionDate() throws Exception {
		assertNull(dao.getLatestTransactionDate());

		transactions().ts(LocalDateTime.of(2014, 1, 5, 0, 0, 0)).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 10, 0, 0, 0)).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).insert();

		LocalDateTime actual = dao.getLatestTransactionDate();
		LocalDateTime expected = LocalDateTime.of(2014, 1, 10, 0, 0, 0);
		assertEquals(expected, actual);

		paymentTransactions().ts(LocalDateTime.of(2014, 2, 1, 0, 0, 0)).insert();

		actual = dao.getLatestTransactionDate();
		expected = LocalDateTime.of(2014, 2, 1, 0, 0, 0);
		assertEquals(expected, actual);

		transactions().ts(LocalDateTime.of(2014, 3, 1, 0, 0, 0)).insert();

		actual = dao.getLatestTransactionDate();
		expected = LocalDateTime.of(2014, 3, 1, 0, 0, 0);
		assertEquals(expected, actual);
	}

	@Test
	public void getItemId() throws Exception {
		int itemId = items().name("Item").insert();
		assertIntEquals(itemId, dao.getItemId("Item"));
		assertIntEquals(itemId, dao.getItemId("item"));
		assertNull(dao.getItemId("does-not-exist"));
	}

	@Test
	public void selsertItem() throws Exception {
		assertEquals(appleId, dao.selsertItem("Apple"));

		assertNull(items().name("Item").id());
		int id = dao.selsertItem("Item");
		assertIntEquals(id, items().name("Item").id());
	}

	@Test
	public void getItemNames() throws Exception {
		List<String> expected = new ArrayList<>(ItemIndex.instance().getItemNames());
		expected.add("item");
		expected.sort(String.CASE_INSENSITIVE_ORDER);

		items().name("item").insert();
		List<String> actual = dao.getItemNames();
		assertEquals(expected, actual);
	}

	@Test
	public void updateItemNamesAndAliases() throws Exception {
		/*
		 * Transaction that uses standard item name should not be touched.
		 */
		transactions().item("Diamond").insert();
		inventory().item("Diamond").quantity(4).insert();

		/*
		 * Rows for standard item names in the "items" table should never be deleted,
		 * but in the event that they are, this item name should be re-inserted
		 * because a transaction exists that uses one of its aliases.
		 */
		items().name("Splash Potion of Harming II").delete();
		transactions().item("Potion:16428").insert(); //alias for above

		/*
		 * Transactions that use a mix of standard names and aliases.
		 */
		transactions().item("Splash Potion of Water Breathing").insert();
		transactions().item("Potion:16397").insert(); //alias for above
		transactions().item("Potion:16429").insert(); //alias for above
		inventory().item("Splash Potion of Water Breathing").quantity(1).insert();
		inventory().item("Potion:16397").quantity(2).insert(); //alias for above
		inventory().item("Potion:16429").quantity(3).insert(); //alias for above

		/*
		 * Time-bound aliases.
		 */
		transactions().item("Acacia Wood").ts(LocalDateTime.of(2019, 3, 1, 0, 0, 0)).insert();
		transactions().item("Acacia Wood").ts(LocalDateTime.of(2019, 4, 1, 0, 0, 0)).insert();

		/*
		 * Inventory items are checked for aliases that are timestamped to the current time.
		 * Because "Acacia Wood" is only considered to be an alias before March 15, 2019, the item name in this inventory record will not change.
		 */
		inventory().item("Acacia Wood").insert();

		dao.updateItemNamesAndAliases();

		assertNotNull(items().name("Splash Potion of Water Breathing").id());
		assertNull(items().name("Potion:16397").id()); //deleted because it was an alias and it's no longer being referenced by any transactions
		assertNull(items().name("Potion:16429").id()); //ditto

		assertNotNull(items().name("Splash Potion of Harming II").id());
		assertNull(items().name("Potion:16428").id()); //deleted because it was an alias and it's no longer being referenced by any transactions

		ResultSet rs = transactions().all();
		rs.next();
		assertIntEquals(items().name("Diamond").id(), rs.getInt("item"));
		rs.next();
		assertIntEquals(items().name("Splash Potion of Harming II").id(), rs.getInt("item"));
		rs.next();
		assertIntEquals(items().name("Splash Potion of Water Breathing").id(), rs.getInt("item"));
		rs.next();
		assertIntEquals(items().name("Splash Potion of Water Breathing").id(), rs.getInt("item"));
		rs.next();
		assertIntEquals(items().name("Splash Potion of Water Breathing").id(), rs.getInt("item"));
		rs.next();
		assertIntEquals(items().name("Acacia Log").id(), rs.getInt("item"));
		rs.next();
		assertIntEquals(items().name("Acacia Wood").id(), rs.getInt("item"));
		assertFalse(rs.next());

		Map<Integer, Integer> invActual = inventory().all();
		Map<Integer, Integer> invExpected = new ImmutableMap.Builder<Integer, Integer>() //@formatter:off
			.put(items().name("Diamond").id(), 4)
			.put(items().name("Splash Potion of Water Breathing").id(), 6)
			.put(items().name("Acacia Wood").id(), 0)
		.build(); //@formatter:on
		assertEquals(invExpected, invActual);
	}

	@Test
	public void populateItemsTable() throws Exception {
		//table shouldn't be touched if no items are missing
		Map<Integer, String> before = items().all();
		dao.populateItemsTable();
		Map<Integer, String> after = items().all();
		assertEquals(before, after);

		items().name("Apple").delete();
		dao.populateItemsTable();
		assertNotNull(items().name("Apple").id());
		assertNotEquals(appleId, (int) items().name("Apple").id());
	}

	@Test
	public void removeDuplicateItems() throws Exception {
		int a = appleId;
		int b = items().name("Apple").insert();
		int c = items().name("apple").insert();
		int d = diamondId;

		transactions().item(a).insert();
		transactions().item(b).insert();
		transactions().item(c).insert();
		transactions().item(d).insert();

		inventory().item(a).quantity(1).insert();
		inventory().item(b).quantity(2).insert();
		inventory().item(c).quantity(3).insert();
		inventory().item(d).quantity(4).insert();

		dao.removeDuplicateItems();

		ResultSet rs = query("SELECT Count(*) FROM items WHERE Lower(name) = 'apple'");
		rs.next();
		assertEquals(1, rs.getInt(1));

		rs = query("SELECT Count(*) FROM inventory INNER JOIN items ON inventory.item = items.id WHERE Lower(items.name) = 'apple'");
		rs.next();
		assertEquals(1, rs.getInt(1));

		rs = query("SELECT inventory.quantity FROM inventory INNER JOIN items ON inventory.item = items.id WHERE Lower(items.name) = 'apple'");
		rs.next();
		assertEquals(6, rs.getInt(1));
	}

	@Test
	public void insertTransaction() throws Exception {
		DateGenerator dg = new DateGenerator();

		ShopTransactionDb transaction = transactions().ts(dg.next()).item("apple").player("notch").balance(1000).amount(-10).quantity(5).dto();
		dao.insertTransaction(transaction, false);

		transaction = transactions().ts(dg.next()).item("Item").player("Jeb").balance(1200).amount(200).quantity(-7).dto();
		dao.insertTransaction(transaction, false);

		assertNotNull(items().name("Item").id());

		List<String> actualNames = players().names();
		assertEquals(2, actualNames.size());
		assertTrue(actualNames.contains("Notch"));
		assertTrue(actualNames.contains("Jeb"));

		ResultSet rs = transactions().all();

		rs.next();
		transactions().ts(dg.getGenerated(0)).item(appleId).player(notchId).balance(1000).amount(-10).quantity(5).test(rs);

		rs.next();
		transactions().ts(dg.getGenerated(1)).item(items().name("Item").id()).player(players().name("Jeb").id()).balance(1200).amount(200).quantity(-7).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void insertTransaction_update_inventory() throws Exception {
		DateGenerator dg = new DateGenerator();
		inventory().item(appleId).quantity(100).insert();
		inventory().item(diamondId).quantity(20).insert();

		ShopTransactionDb transaction = transactions().ts(dg.next()).item("apple").player("notch").balance(1000).amount(10).dto();

		transaction.setQuantity(5);
		dao.insertTransaction(transaction, false);

		transaction.setQuantity(5);
		dao.insertTransaction(transaction, true);

		transaction.setQuantity(-15);
		dao.insertTransaction(transaction, true);

		Map<Integer, Integer> actual = inventory().all();
		Map<Integer, Integer> expected = new HashMap<>();
		expected.put(appleId, 90);
		expected.put(diamondId, 20);
		assertEquals(expected, actual);
	}

	@Test
	public void insertTransaction_update_first_last_seen_dates() throws Exception {
		DirbyDbDao dao = new DirbyMemoryDbDao("insertTransaction_update_first_last_seen_dates");
		conn = dao.getConnection();

		players().name("Notch").firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 0, 0, 0)).insert();
		players().name("Dinnerbone").firstSeen(null).lastSeen(null).insert();

		ShopTransactionDb t = transactions().ts(LocalDateTime.of(2014, 1, 3, 0, 0, 0)).player("Notch").dto();
		dao.insertTransaction(t, false);
		t = transactions().ts(LocalDateTime.of(2014, 1, 3, 1, 0, 0)).player("Jeb").dto();
		dao.insertTransaction(t, false);
		t = transactions().ts(LocalDateTime.of(2014, 1, 4, 0, 0, 0)).player("Notch").dto();
		dao.insertTransaction(t, false);
		t = transactions().ts(LocalDateTime.of(2014, 1, 4, 1, 0, 0)).player("Dinnerbone").dto();
		dao.insertTransaction(t, false);
		dao.commit(); //the update occurs when a commit happens

		ResultSet rs = players().all();

		rs.next();
		assertEquals("Notch", rs.getString("name"));
		assertEquals(LocalDateTime.of(2014, 1, 1, 0, 0, 0), toLocalDateTime(rs.getTimestamp("first_seen")));
		assertEquals(LocalDateTime.of(2014, 1, 4, 0, 0, 0), toLocalDateTime(rs.getTimestamp("last_seen")));

		rs.next();
		assertEquals("Dinnerbone", rs.getString("name"));
		assertEquals(LocalDateTime.of(2014, 1, 4, 1, 0, 0), toLocalDateTime(rs.getTimestamp("first_seen")));
		assertEquals(LocalDateTime.of(2014, 1, 4, 1, 0, 0), toLocalDateTime(rs.getTimestamp("last_seen")));

		rs.next();
		assertEquals("Jeb", rs.getString("name"));
		assertEquals(LocalDateTime.of(2014, 1, 3, 1, 0, 0), toLocalDateTime(rs.getTimestamp("first_seen")));
		assertEquals(LocalDateTime.of(2014, 1, 3, 1, 0, 0), toLocalDateTime(rs.getTimestamp("last_seen")));

		assertFalse(rs.next());
	}

	@Test
	public void insertPaymentTransaction() throws Exception {
		DateGenerator dg = new DateGenerator();
		PaymentTransactionDb t = paymentTransactions().amount(1000).balance(20000).player("Notch").ts(dg.next()).dto();

		dao.insertPaymentTransaction(t);
		assertNotNull(t.getId());

		ResultSet rs = paymentTransactions().all();

		rs.next();
		paymentTransactions().ts(dg.getGenerated(0)).player(notchId).balance(20000).amount(1000).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void upsertPaymentTransactionDb() throws Exception {
		DateGenerator dg = new DateGenerator();
		int jeb = players().name("Jeb").insert();
		int t1 = paymentTransactions().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		paymentTransactions().ts(dg.next()).player(jeb).amount(-5000).balance(15000).ignore(false).transaction(null).insert();

		PaymentTransactionDb t3 = paymentTransactions().amount(100).balance(15100).player("Notch").ts(dg.next()).dto();
		PaymentTransactionDb t4 = paymentTransactions().id(t1).amount(200).balance(20100).dto();

		dao.upsertPaymentTransaction(t3);
		assertNotNull(t3.getId());

		dao.upsertPaymentTransaction(t4);

		ResultSet rs = paymentTransactions().all();

		rs.next();
		paymentTransactions().ts(dg.getGenerated(0)).player(notchId).balance(20100).amount(200).transaction(null).ignore(false).test(rs);

		rs.next();
		paymentTransactions().ts(dg.getGenerated(1)).player(jeb).balance(15000).amount(-5000).transaction(null).ignore(false).test(rs);

		rs.next();
		paymentTransactions().ts(dg.getGenerated(2)).player(notchId).balance(15100).amount(100).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void getPendingPaymentTransactions() throws Exception {
		DateGenerator dg = new DateGenerator();
		int transId = transactions().ts(dg.next()).item(appleId).player(notchId).amount(500).quantity(-10).insert();
		int t1 = paymentTransactions().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		int t2 = paymentTransactions().ts(dg.next()).player(notchId).amount(200).balance(30000).ignore(false).transaction(null).insert();
		paymentTransactions().ts(dg.next()).player(notchId).amount(300).balance(40000).ignore(true).transaction(null).insert();
		paymentTransactions().ts(dg.next()).player(notchId).amount(400).balance(50000).ignore(false).transaction(transId).insert();
		paymentTransactions().ts(dg.next()).player(notchId).amount(500).balance(60000).ignore(true).transaction(transId).insert();

		Iterator<PaymentTransactionDb> it = dao.getPendingPaymentTransactions().iterator();

		PaymentTransactionDb t = it.next();
		paymentTransactions().ts(dg.getGenerated(2)).player("Notch").balance(30000).amount(200).id(t2).test(t);

		t = it.next();
		paymentTransactions().ts(dg.getGenerated(1)).player("Notch").balance(20000).amount(100).id(t1).test(t);

		assertFalse(it.hasNext());
	}

	@Test
	public void countPendingPaymentTransactions() throws Exception {
		int transId = transactions().insert();
		paymentTransactions().ignore(false).transaction(null).insert();
		paymentTransactions().ignore(false).transaction(null).insert();
		paymentTransactions().ignore(true).transaction(null).insert();
		paymentTransactions().ignore(false).transaction(transId).insert();
		paymentTransactions().ignore(true).transaction(transId).insert();

		int actual = dao.countPendingPaymentTransactions();
		int expected = 2;
		assertEquals(expected, actual);
	}

	@Test
	public void ignorePaymentTransactionDb() throws Exception {
		DateGenerator dg = new DateGenerator();
		int t1 = paymentTransactions().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		int t2 = paymentTransactions().ts(dg.next()).player(notchId).amount(200).balance(30000).ignore(true).transaction(null).insert();

		dao.ignorePaymentTransaction(t1);
		dao.ignorePaymentTransaction(t2);

		ResultSet rs = paymentTransactions().all();

		rs.next();
		paymentTransactions().ts(dg.getGenerated(0)).player(notchId).balance(20000).amount(100).transaction(null).ignore(true).test(rs);

		rs.next();
		paymentTransactions().ts(dg.getGenerated(1)).player(notchId).balance(30000).amount(200).transaction(null).ignore(true).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void assignPaymentTransactionDb() throws Exception {
		DateGenerator dg = new DateGenerator();
		int transId = transactions().ts(dg.next()).item(appleId).player(notchId).amount(500).quantity(-10).insert();
		int t1 = paymentTransactions().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		paymentTransactions().ts(dg.next()).player(notchId).amount(200).balance(30000).ignore(false).transaction(null).insert();

		dao.assignPaymentTransaction(t1, transId);

		ResultSet rs = paymentTransactions().all();

		rs.next();
		paymentTransactions().ts(dg.getGenerated(1)).player(notchId).balance(20000).amount(100).transaction(transId).ignore(false).test(rs);

		rs.next();
		paymentTransactions().ts(dg.getGenerated(2)).player(notchId).balance(30000).amount(200).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void getItemGroups() throws Exception {
		assertTrue(dao.getItemGroups(null, null, ShopTransactionType.MY_SHOP).isEmpty());

		int jeb = players().name("Jeb").insert();
		DateGenerator dg = new DateGenerator();
		transactions().ts(dg.next()).item(appleId).player(notchId).amount(-5).quantity(100).insert();
		transactions().ts(dg.next()).item(appleId).player(notchId).amount(-20).quantity(1000).insert();
		transactions().ts(dg.next()).item(appleId).player(notchId).amount(10).quantity(-200).insert();
		transactions().ts(dg.next()).item(appleId).player(notchId).amount(1).quantity(-20).insert();
		transactions().ts(dg.next()).item(appleId).player(jeb).amount(1).quantity(-20).insert();
		transactions().ts(dg.next()).item(diamondId).player(notchId).amount(-1).quantity(50).insert();

		//no date range
		{
			Map<String, ItemGroup> groups = itemMap(dao.getItemGroups(null, null, ShopTransactionType.MY_SHOP));
			assertEquals(2, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			itemGroup().item("Apple").boughtAmt(-25).boughtQty(1100).soldAmt(12).soldQty(-240).test(itemGroup);

			itemGroup = groups.get("Diamond");
			itemGroup().item("Diamond").boughtAmt(-1).boughtQty(50).soldAmt(0).soldQty(0).test(itemGroup);
		}

		//start date (first transaction is not included)
		{
			Map<String, ItemGroup> groups = itemMap(dao.getItemGroups(dg.getGenerated(1), null, ShopTransactionType.MY_SHOP));
			assertEquals(2, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			itemGroup().item("Apple").boughtAmt(-20).boughtQty(1000).soldAmt(12).soldQty(-240).test(itemGroup);

			itemGroup = groups.get("Diamond");
			itemGroup().item("Diamond").boughtAmt(-1).boughtQty(50).soldAmt(0).soldQty(0).test(itemGroup);
		}

		//end date (last transaction is not included)
		{
			Map<String, ItemGroup> groups = itemMap(dao.getItemGroups(null, dg.getGenerated(5), ShopTransactionType.MY_SHOP));
			assertEquals(1, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			itemGroup().item("Apple").boughtAmt(-25).boughtQty(1100).soldAmt(12).soldQty(-240).test(itemGroup);
		}

		//start and end date (first and last transactions are not included)
		{
			Map<String, ItemGroup> groups = itemMap(dao.getItemGroups(dg.getGenerated(1), dg.getGenerated(5), ShopTransactionType.MY_SHOP));
			assertEquals(1, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			itemGroup().item("Apple").boughtAmt(-20).boughtQty(1000).soldAmt(12).soldQty(-240).test(itemGroup);
		}

		//start and end date (no transactions included)
		{
			Map<String, ItemGroup> groups = itemMap(dao.getItemGroups(dg.next(), dg.next(), ShopTransactionType.MY_SHOP));
			assertEquals(0, groups.size());
		}
	}

	@Test
	public void getTransactionsByDate() throws Exception {
		assertTrue(dao.getTransactionsByDate(null, null, ShopTransactionType.MY_SHOP).isEmpty());

		int jeb = players().name("Jeb").insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 0)).item(appleId).player(notchId).amount(-10).quantity(1).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 1)).item(diamondId).player(notchId).amount(1000).quantity(-5).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 2)).item(appleId).player(jeb).amount(-10).quantity(1).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 4)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 5)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 1, 1)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 3, 0)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 5, 1)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 10, 0)).item(appleId).player(notchId).amount(100).quantity(-10).insert();

		//no date range
		{
			Iterator<ShopTransactionDb> it = dao.getTransactionsByDate(null, null, ShopTransactionType.MY_SHOP).iterator();

			ShopTransactionDb t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 0)).item("Apple").player("Notch").amount(490).quantity(-49).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 1)).item("Diamond").player("Notch").amount(1000).quantity(-5).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 2)).item("Apple").player("Jeb").amount(-10).quantity(1).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 5, 1)).item("Apple").player("Notch").amount(100).quantity(-10).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 10, 0)).item("Apple").player("Notch").amount(100).quantity(-10).test(t);

			assertFalse(it.hasNext());
		}

		//with start date
		{
			Iterator<ShopTransactionDb> it = dao.getTransactionsByDate(LocalDateTime.of(2014, 1, 1, 1, 0, 2), null, ShopTransactionType.MY_SHOP).iterator();

			ShopTransactionDb t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 2)).item("Apple").player("Jeb").amount(-10).quantity(1).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 4)).item("Apple").player("Notch").amount(400).quantity(-40).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 5, 1)).item("Apple").player("Notch").amount(100).quantity(-10).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 10, 0)).item("Apple").player("Notch").amount(100).quantity(-10).test(t);

			assertFalse(it.hasNext());
		}

		//with end date
		{
			Iterator<ShopTransactionDb> it = dao.getTransactionsByDate(null, LocalDateTime.of(2014, 1, 1, 1, 1, 0), ShopTransactionType.MY_SHOP).iterator();

			ShopTransactionDb t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 0)).item("Apple").player("Notch").amount(290).quantity(-29).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 1)).item("Diamond").player("Notch").amount(1000).quantity(-5).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 2)).item("Apple").player("Jeb").amount(-10).quantity(1).test(t);

			assertFalse(it.hasNext());
		}

		//with start and end dates
		{
			Iterator<ShopTransactionDb> it = dao.getTransactionsByDate(LocalDateTime.of(2014, 1, 1, 1, 0, 2), LocalDateTime.of(2014, 1, 1, 1, 1, 0), ShopTransactionType.MY_SHOP).iterator();

			ShopTransactionDb t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 2)).item("Apple").player("Jeb").amount(-10).quantity(1).test(t);

			t = it.next();
			transactions().ts(LocalDateTime.of(2014, 1, 1, 1, 0, 4)).item("Apple").player("Notch").amount(200).quantity(-20).test(t);

			assertFalse(it.hasNext());
		}
	}

	@Test
	public void getPlayerGroups() throws Exception {
		assertTrue(dao.getPlayerGroups(null, null, ShopTransactionType.MY_SHOP).isEmpty());

		DateGenerator dg = new DateGenerator();
		int jeb = players().name("Jeb").insert();
		transactions().ts(dg.next()).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(dg.next()).item(diamondId).player(notchId).amount(1000).quantity(-5).insert();
		transactions().ts(dg.next()).item(appleId).player(jeb).amount(-10).quantity(1).insert();
		transactions().ts(dg.next()).item(appleId).player(notchId).amount(100).quantity(-10).insert();

		//no date range
		{
			Map<String, PlayerGroup> groups = playerMap(dao.getPlayerGroups(null, null, ShopTransactionType.MY_SHOP));
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				players().name("Notch").id(notchId).firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(2, items.size());
				ItemGroup ig = items.get("Apple");
				itemGroup().item("Apple").boughtAmt(0).boughtQty(0).soldAmt(200).soldQty(-20).test(ig);
				ig = items.get("Diamond");
				itemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				players().name("Jeb").id(jeb).firstSeen(null).lastSeen(null).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				itemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}

		//with start date
		{
			Map<String, PlayerGroup> groups = playerMap(dao.getPlayerGroups(dg.getGenerated(1), null, ShopTransactionType.MY_SHOP));
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				players().name("Notch").id(notchId).firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(2, items.size());
				ItemGroup ig = items.get("Apple");
				itemGroup().item("Apple").boughtAmt(0).boughtQty(0).soldAmt(100).soldQty(-10).test(ig);
				ig = items.get("Diamond");
				itemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				players().name("Jeb").id(jeb).firstSeen(null).lastSeen(null).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				itemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}

		//with end date
		{
			Map<String, PlayerGroup> groups = playerMap(dao.getPlayerGroups(null, dg.getGenerated(3), ShopTransactionType.MY_SHOP));
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				players().name("Notch").id(notchId).firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(2, items.size());
				ItemGroup ig = items.get("Apple");
				itemGroup().item("Apple").boughtAmt(0).boughtQty(0).soldAmt(100).soldQty(-10).test(ig);
				ig = items.get("Diamond");
				itemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				players().name("Jeb").id(jeb).firstSeen(null).lastSeen(null).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				itemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}

		//with start and end dates
		{
			Map<String, PlayerGroup> groups = playerMap(dao.getPlayerGroups(dg.getGenerated(1), dg.getGenerated(3), ShopTransactionType.MY_SHOP));
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				players().name("Notch").id(notchId).firstSeen(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).lastSeen(LocalDateTime.of(2014, 1, 2, 12, 0, 0)).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Diamond");
				itemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				players().name("Jeb").id(jeb).firstSeen(null).lastSeen(null).test(player);

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				itemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}
	}

	@Test
	public void getInventory() throws Exception {
		assertTrue(dao.getInventory().isEmpty());

		inventory().item(appleId).quantity(5).insert();
		inventory().item(diamondId).quantity(10).insert();

		List<Inventory> inventory = new ArrayList<>(dao.getInventory());
		assertEquals(2, inventory.size());
		inventory.sort(Comparator.comparing(Inventory::getItem));

		Iterator<Inventory> it = inventory.iterator();

		Inventory inv = it.next();
		assertNotNull(inv.getId());
		assertEquals("Apple", inv.getItem());
		assertIntEquals(appleId, inv.getItemId());
		assertIntEquals(5, inv.getQuantity());

		inv = it.next();
		assertNotNull(inv.getId());
		assertEquals("Diamond", inv.getItem());
		assertIntEquals(diamondId, inv.getItemId());
		assertIntEquals(10, inv.getQuantity());

		assertFalse(it.hasNext());
	}

	@Test
	public void upsertInventory() throws Exception {
		inventory().item(appleId).quantity(5).insert();

		dao.upsertInventory("Apple", 10, false);
		dao.upsertInventory("Apple", 1, true);
		dao.upsertInventory("Apple", -4, true);
		dao.upsertInventory("Diamond", 1, true);

		Map<Integer, Integer> actual = inventory().all();
		Map<Integer, Integer> expected = new HashMap<>();
		expected.put(appleId, 7);
		expected.put(diamondId, 1);
		assertEquals(expected, actual);
	}

	@Test
	public void deleteInventory() throws Exception {
		int a = items().name("a").insert();
		int b = items().name("b").insert();
		int c = items().name("c").insert();
		int ai = inventory().item(a).quantity(1).insert();
		inventory().item(b).quantity(2).insert();
		int ci = inventory().item(c).quantity(3).insert();

		dao.deleteInventory(Arrays.asList(ai, ci));

		Map<Integer, Integer> actual = inventory().all();
		Map<Integer, Integer> expected = new HashMap<>();
		expected.put(b, 2);
		assertEquals(expected, actual);

		dao.deleteInventory(Collections.<Integer>emptyList());

		actual = inventory().all();
		assertEquals(expected, actual);
	}

	@Test
	public void updateBonusesFeeTotals() throws Exception {
		dao.updateBonusFeeTotals(Collections.emptyMap());

		bonusesFees().test();

		Map<Class<? extends RupeeTransaction>, MutableInt> map = new HashMap<>();
		map.put(DailySigninBonus.class, new MutableInt(800));
		map.put(HorseSummonFee.class, new MutableInt(-200));
		map.put(LockTransaction.class, new MutableInt(-500));
		map.put(EggifyFee.class, new MutableInt(-200));
		map.put(VaultFee.class, new MutableInt(-20));
		map.put(VoteBonus.class, new MutableInt(300));
		dao.updateBonusFeeTotals(map);
		bonusesFees().since(null).horse(-200).lock(-500).eggify(-200).vault(-20).signIn(800).vote(300).test();

		map.clear();
		map.put(VaultFee.class, new MutableInt(-10));
		map.put(VoteBonus.class, new MutableInt(100));
		dao.updateBonusFeeTotals(map);
		bonusesFees().since(null).horse(-200).lock(-500).eggify(-200).vault(-30).signIn(800).vote(400).test();
	}

	@Test
	public void getBonusesFees() throws Exception {
		BonusesFeesTester tester = bonusesFees().since(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).horse(1).lock(2).eggify(3).vault(4).signIn(5).vote(6);
		tester.set();
		BonusFee bonusFee = dao.getBonusesFees();
		tester.test(bonusFee);
	}

	@Test
	public void updateBonusesFeesSince() throws Exception {
		DateGenerator dg = new DateGenerator();

		dao.updateBonusesFeesSince(dg.next());
		ResultSet rs = query("SELECT since FROM bonuses_fees");
		rs.next();
		assertEquals(timestamp(dg.getGenerated(0)), rs.getTimestamp("since"));
		assertFalse(rs.next());

		//it can only be changed once, when it is null
		dao.updateBonusesFeesSince(dg.next());
		rs = query("SELECT since FROM bonuses_fees");
		rs.next();
		assertEquals(timestamp(dg.getGenerated(0)), rs.getTimestamp("since"));
		assertFalse(rs.next());
	}

	@Test
	public void getProfitsByDay() throws Exception {
		assertTrue(dao.getProfitsByDay(null, null).isEmpty());

		transactions().ts(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 12, 0, 0)).item(diamondId).player(notchId).amount(-50).quantity(5).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 12, 0, 0)).item(diamondId).player(notchId).amount(-50).quantity(5).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 1, 12, 0, 0)).item(diamondId).player(notchId).amount(10).quantity(-1).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 3, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 4, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).insert();

		//no date range
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByDay(null, null);
			assertEquals(3, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 1, 1));
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());

			p = profits.get(LocalDate.of(2014, 1, 3));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());

			p = profits.get(LocalDate.of(2014, 1, 4));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with start date
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByDay(LocalDate.of(2014, 1, 3), null);
			assertEquals(2, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 1, 3));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());

			p = profits.get(LocalDate.of(2014, 1, 4));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with end date
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByDay(null, LocalDate.of(2014, 1, 4));
			assertEquals(2, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 1, 1));
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());

			p = profits.get(LocalDate.of(2014, 1, 3));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with start and end dates
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByDay(LocalDate.of(2014, 1, 3), LocalDate.of(2014, 1, 4));
			assertEquals(1, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 1, 3));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}
	}

	@Test
	public void getProfitsByMonth() throws Exception {
		assertTrue(dao.getProfitsByMonth(null, null).isEmpty());

		transactions().ts(LocalDateTime.of(2014, 1, 1, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).balance(500).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 2, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).balance(510).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 3, 0, 0, 0)).item(diamondId).player(notchId).amount(-50).quantity(5).balance(520).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 4, 0, 0, 0)).item(diamondId).player(notchId).amount(-50).quantity(5).balance(500).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 5, 0, 0, 0)).item(diamondId).player(notchId).amount(10).quantity(-1).balance(490).insert();
		transactions().ts(LocalDateTime.of(2014, 1, 6, 0, 0, 0)).item(diamondId).player((Integer) null).amount(50).quantity(-5).balance(540).insert();
		transactions().ts(LocalDateTime.of(2014, 2, 1, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).balance(600).insert();
		transactions().ts(LocalDateTime.of(2014, 4, 1, 0, 0, 0)).item(appleId).player(notchId).amount(100).quantity(-10).balance(700).insert();
		transactions().ts(LocalDateTime.of(2014, 5, 1, 0, 0, 0)).item(diamondId).player((Integer) null).amount(50).quantity(-5).balance(800).insert();

		//no date range
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByMonth(null, null);
			assertEquals(4, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 1, 1));
			assertTrue(p.hasTransactions());
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());
			assertEquals(540, p.getBalance());

			p = profits.get(LocalDate.of(2014, 2, 1));
			assertTrue(p.hasTransactions());
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
			assertEquals(600, p.getBalance());

			p = profits.get(LocalDate.of(2014, 4, 1));
			assertTrue(p.hasTransactions());
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
			assertEquals(700, p.getBalance());

			p = profits.get(LocalDate.of(2014, 5, 1));
			assertFalse(p.hasTransactions());
			assertEquals(800, p.getBalance());
		}

		//with start date
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByMonth(LocalDate.of(2014, 2, 1), null);
			assertEquals(3, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 2, 1));
			assertTrue(p.hasTransactions());
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
			assertEquals(600, p.getBalance());

			p = profits.get(LocalDate.of(2014, 4, 1));
			assertTrue(p.hasTransactions());
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
			assertEquals(700, p.getBalance());

			p = profits.get(LocalDate.of(2014, 5, 1));
			assertFalse(p.hasTransactions());
			assertEquals(800, p.getBalance());
		}

		//with end date
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByMonth(null, LocalDate.of(2014, 4, 1));
			assertEquals(2, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 1, 1));
			assertTrue(p.hasTransactions());
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());
			assertEquals(540, p.getBalance());

			p = profits.get(LocalDate.of(2014, 2, 1));
			assertTrue(p.hasTransactions());
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
			assertEquals(600, p.getBalance());
		}

		//with start and end dates
		{
			Map<LocalDate, Profits> profits = dao.getProfitsByMonth(LocalDate.of(2014, 2, 1), LocalDate.of(2014, 4, 1));
			assertEquals(1, profits.size());

			Profits p = profits.get(LocalDate.of(2014, 2, 1));
			assertTrue(p.hasTransactions());
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
			assertEquals(600, p.getBalance());
		}
	}

	@Test
	public void updateToLatestVersion() throws Exception {
		DirbyDbDao dao = new DirbyMemoryDbDao("updateToLatestVersion") {
			@Override
			Reader getMigrationScript(int currentVersion) {
				String sql = "";
				switch (currentVersion) {
				case 1:
					sql = "INSERT INTO items (name) VALUES ('Item');";
					break;
				case 2:
					sql = "DELETE FROM items WHERE name = 'Apple';";
					break;
				}

				return new StringReader(sql);
			}

			@Override
			public int getAppDbVersion() {
				return 3;
			}
		};
		conn = dao.getConnection();

		meta().dbSchemaVersion(1);
		dao.commit();

		DbListenerImpl listener = new DbListenerImpl();
		dao.updateToLatestVersion(listener);
		assertEquals(1, listener.onMigrateOldVersion);
		assertEquals(3, listener.onMigrateNewVersion);

		assertNotNull(items().name("Item").id());
		assertNull(items().name("Apple").id());
		assertIntEquals(3, meta().dbSchemaVersion());
	}

	@Test
	public void updateToLatestVersion_sql_error() throws Exception {
		DirbyDbDao dao = new DirbyMemoryDbDao("updateToLatestVersion_sql_error") {
			@Override
			Reader getMigrationScript(int currentVersion) {
				String sql = "";
				switch (currentVersion) {
				case 1:
					sql = "INSERT INTO items (name) VALUES ('Item');";
					break;
				case 2:
					sql = "bad-statement";
					break;
				}

				return new StringReader(sql);
			}

			@Override
			public int getAppDbVersion() {
				return 3;
			}
		};
		conn = dao.getConnection();

		meta().dbSchemaVersion(1);
		dao.commit();

		try {
			DbListenerImpl listener = new DbListenerImpl();
			dao.updateToLatestVersion(listener);
			assertEquals(1, listener.onMigrateOldVersion);
			assertEquals(3, listener.onMigrateNewVersion);
			fail();
		} catch (SQLException e) {
			//changes should be rolled-back
			assertNull(items().name("Item").id());
			assertNotNull(items().name("Apple").id());
			assertIntEquals(1, meta().dbSchemaVersion());
		}
	}

	@Test(expected = SQLException.class)
	public void updateToLatestVersion_db_version_higher() throws Exception {
		DirbyDbDao dao = new DirbyMemoryDbDao("updateToLatestVersion_db_version_higher") {
			@Override
			Reader getMigrationScript(int currentVersion) {
				throw new RuntimeException(); //should not be called
			}

			@Override
			public int getAppDbVersion() {
				return 3;
			}
		};
		conn = dao.getConnection();

		meta().dbSchemaVersion(4);
		dao.commit();

		DbListenerImpl listener = new DbListenerImpl();
		dao.updateToLatestVersion(listener);
	}

	private class DbListenerImpl implements DbListener {
		private int onCreate, onMigrateOldVersion, onMigrateNewVersion;

		@Override
		public void onCreate() {
			onCreate++;
		}

		@Override
		public void onMigrate(int oldVersion, int newVersion) {
			onMigrateOldVersion = oldVersion;
			onMigrateNewVersion = newVersion;
		}
	}

	@Test
	public void wipe() throws Exception {
		DirbyDbDao dao = new DirbyMemoryDbDao("wipe");
		conn = dao.getConnection();

		int notch = players().name("Notch").insert();
		int transaction = transactions().player(notch).insert();
		paymentTransactions().transaction(transaction).insert();
		inventory().item(appleId).insert();
		items().name("Item").insert();
		bonusesFees().horse(100).since(LocalDateTime.now()).set();

		dao.wipe();

		assertIntEquals(dao.getAppDbVersion(), meta().dbSchemaVersion());
		assertEquals(0, players().count());
		assertEquals(0, transactions().count());
		assertEquals(0, paymentTransactions().count());
		assertEquals(0, inventory().count());
		assertTrue(items().count() > 0);
		assertNull(items().name("Item").id());
		bonusesFees().test();
	}

	/**
	 * Timestamp values in the database are stored with millisecond-precision, whereas Java uses nanosecond-precision.
	 * @param expected
	 * @param actual
	 */
	private static void assertTimestampEquals(LocalDateTime expected, LocalDateTime actual) {
		assertEquals(expected.truncatedTo(ChronoUnit.MILLIS), actual);
	}

	private static MetaHelper meta() {
		return new MetaHelper();
	}

	private static class MetaHelper {
		public MetaHelper dbSchemaVersion(int dbSchemaVersion) throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("UPDATE meta SET db_schema_version = ?");
			stmt.setInt(1, dbSchemaVersion);
			stmt.executeUpdate();
			return this;
		}

		public Integer dbSchemaVersion() throws SQLException {
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT db_schema_version FROM meta");
			return rs.next() ? rs.getInt(1) : null;
		}
	}

	public static UpdateLogTester updateLog() {
		return new UpdateLogTester();
	}

	private static class UpdateLogTester {
		private LocalDateTime ts = LocalDateTime.now();
		private int rupeeBalance = 0;
		private int shopTransactionCount = 0, paymentTransactionCount = 0, bonusFeeTransactionCount = 0;
		private long timeTaken = 0;

		public UpdateLogTester ts(LocalDateTime ts) {
			this.ts = ts;
			return this;
		}

		public UpdateLogTester rupeeBalance(int rupeeBalance) {
			this.rupeeBalance = rupeeBalance;
			return this;
		}

		public UpdateLogTester shopTransactionCount(int shopTransactionCount) {
			this.shopTransactionCount = shopTransactionCount;
			return this;
		}

		public UpdateLogTester paymentTransactionCount(int paymentTransactionCount) {
			this.paymentTransactionCount = paymentTransactionCount;
			return this;
		}

		public UpdateLogTester bonusFeeTransactionCount(int bonusFeeTransactionCount) {
			this.bonusFeeTransactionCount = bonusFeeTransactionCount;
			return this;
		}

		public UpdateLogTester timeTaken(long timeTaken) {
			this.timeTaken = timeTaken;
			return this;
		}

		public int insert() throws SQLException {
			InsertStatement stmt = new InsertStatement("update_log");
			stmt.setTimestamp("ts", ts);
			stmt.setInt("rupee_balance", rupeeBalance);
			stmt.setInt("transaction_count", shopTransactionCount);
			stmt.setInt("payment_transaction_count", paymentTransactionCount);
			stmt.setInt("bonus_fee_transaction_count", bonusFeeTransactionCount);
			stmt.setInt("time_taken", (int) timeTaken);
			return stmt.execute(conn);
		}

		public void test() throws SQLException {
			ResultSet rs = query("SELECT * FROM update_log");
			rs.next();
			assertTimestampEquals(ts, toLocalDateTime(rs.getTimestamp("ts")));
			assertEquals(rupeeBalance, rs.getInt("rupee_balance"));
			assertEquals(shopTransactionCount, rs.getInt("transaction_count"));
			assertEquals(paymentTransactionCount, rs.getInt("payment_transaction_count"));
			assertEquals(bonusFeeTransactionCount, rs.getInt("bonus_fee_transaction_count"));
			assertEquals(timeTaken, rs.getInt("time_taken"));
			assertFalse(rs.next());
		}
	}

	public static BonusesFeesTester bonusesFees() {
		return new BonusesFeesTester();
	}

	private static class BonusesFeesTester {
		private LocalDateTime since;
		private int horse, lock, eggify, vault, signIn, vote;

		public BonusesFeesTester since(LocalDateTime since) {
			this.since = since;
			return this;
		}

		public BonusesFeesTester horse(int horse) {
			this.horse = horse;
			return this;
		}

		public BonusesFeesTester lock(int lock) {
			this.lock = lock;
			return this;
		}

		public BonusesFeesTester eggify(int eggify) {
			this.eggify = eggify;
			return this;
		}

		public BonusesFeesTester vault(int vault) {
			this.vault = vault;
			return this;
		}

		public BonusesFeesTester signIn(int signIn) {
			this.signIn = signIn;
			return this;
		}

		public BonusesFeesTester vote(int vote) {
			this.vote = vote;
			return this;
		}

		public void test() throws SQLException {
			ResultSet rs = query("SELECT * FROM bonuses_fees");
			rs.next();
			assertEquals(since, toLocalDateTime(rs.getTimestamp("since")));
			assertEquals(horse, rs.getInt("horse"));
			assertEquals(lock, rs.getInt("lock"));
			assertEquals(eggify, rs.getInt("eggify"));
			assertEquals(vault, rs.getInt("vault"));
			assertEquals(signIn, rs.getInt("sign_in"));
			assertEquals(vote, rs.getInt("vote"));
			assertFalse(rs.next());
		}

		public void test(BonusFee bonusFee) {
			assertEquals(since, bonusFee.getSince());
			assertEquals(horse, bonusFee.getHorse());
			assertEquals(lock, bonusFee.getLock());
			assertEquals(eggify, bonusFee.getEggify());
			assertEquals(vault, bonusFee.getVault());
			assertEquals(signIn, bonusFee.getSignIn());
			assertEquals(vote, bonusFee.getVote());
		}

		public void set() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("UPDATE bonuses_fees SET since = ?, horse = ?, lock = ?, eggify = ?, vault = ?, sign_in = ?, vote = ?");
			int i = 1;
			stmt.setTimestamp(i++, timestamp(since));
			stmt.setInt(i++, horse);
			stmt.setInt(i++, lock);
			stmt.setInt(i++, eggify);
			stmt.setInt(i++, vault);
			stmt.setInt(i++, signIn);
			stmt.setInt(i++, vote);
			stmt.executeUpdate();
		}
	}

	private static ItemGroupTester itemGroup() {
		return new ItemGroupTester();
	}

	private static class ItemGroupTester {
		private String item;
		private int boughtAmt, boughtQty, soldAmt, soldQty;

		public ItemGroupTester item(String item) {
			this.item = item;
			return this;
		}

		public ItemGroupTester boughtAmt(int boughtAmt) {
			this.boughtAmt = boughtAmt;
			return this;
		}

		public ItemGroupTester boughtQty(int boughtQty) {
			this.boughtQty = boughtQty;
			return this;
		}

		public ItemGroupTester soldAmt(int soldAmt) {
			this.soldAmt = soldAmt;
			return this;
		}

		public ItemGroupTester soldQty(int soldQty) {
			this.soldQty = soldQty;
			return this;
		}

		public void test(ItemGroup actual) {
			assertEquals(item, actual.getItem());
			assertEquals(boughtAmt, actual.getBoughtAmount());
			assertEquals(boughtQty, actual.getBoughtQuantity());
			assertEquals(soldAmt, actual.getSoldAmount());
			assertEquals(soldQty, actual.getSoldQuantity());
			assertEquals(boughtAmt + soldAmt, actual.getNetAmount());
			assertEquals(boughtQty + soldQty, actual.getNetQuantity());
		}
	}

	private static ItemTester items() {
		return new ItemTester();
	}

	private static class ItemTester {
		private String name;

		public ItemTester name(String name) {
			this.name = name;
			return this;
		}

		public int selsert() throws SQLException {
			Integer id = id();
			return (id == null) ? insert() : id;
		}

		public int insert() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO items (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);

			int i = 1;
			stmt.setString(i++, name);
			stmt.executeUpdate();

			return getKey(stmt);
		}

		public Integer id() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("SELECT id FROM items WHERE name = ?");

			int i = 1;
			stmt.setString(i++, name);
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : null;
		}

		public int count() throws SQLException {
			ResultSet rs = query("SELECT Count(*) FROM items");
			rs.next();
			return rs.getInt(1);
		}

		public Map<Integer, String> all() throws SQLException {
			Map<Integer, String> map = new HashMap<>();
			ResultSet rs = query("SELECT id, name FROM items");
			while (rs.next()) {
				map.put(rs.getInt("id"), rs.getString("name"));
			}
			return map;
		}

		public void delete() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("DELETE FROM items WHERE name = ?");
			stmt.setString(1, name);
			assertTrue(stmt.executeUpdate() > 0);
		}
	}

	private static PlayerTester players() {
		return new PlayerTester();
	}

	private static class PlayerTester {
		private Integer id;
		private String name;
		private LocalDateTime firstSeen, lastSeen;

		public PlayerTester id(Integer id) {
			this.id = id;
			return this;
		}

		public PlayerTester name(String name) {
			this.name = name;
			return this;
		}

		public PlayerTester firstSeen(LocalDateTime firstSeen) {
			this.firstSeen = firstSeen;
			return this;
		}

		public PlayerTester lastSeen(LocalDateTime lastSeen) {
			this.lastSeen = lastSeen;
			return this;
		}

		public Integer id() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("SELECT id FROM players WHERE name = ?");

			int i = 1;
			stmt.setString(i++, name);
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : null;
		}

		public int insert() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO players (name, first_seen, last_seen) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			int i = 1;
			stmt.setString(i++, name);
			stmt.setTimestamp(i++, timestamp(firstSeen));
			stmt.setTimestamp(i++, timestamp(lastSeen));
			stmt.executeUpdate();

			return getKey(stmt);
		}

		public void test(Player player) {
			if (id != null) {
				assertEquals(id, player.getId());
			}
			assertEquals(name, player.getName());
			assertEquals(firstSeen, player.getFirstSeen());
			assertEquals(lastSeen, player.getLastSeen());
		}

		public int count() throws SQLException {
			ResultSet rs = query("SELECT Count(*) FROM players");
			rs.next();
			return rs.getInt(1);
		}

		public List<String> names() throws SQLException {
			List<String> players = new ArrayList<>();
			ResultSet rs = query("SELECT name FROM players ORDER BY id");
			while (rs.next()) {
				players.add(rs.getString(1));
			}
			return players;
		}

		public ResultSet all() throws SQLException {
			return query("SELECT * FROM players ORDER BY id");
		}
	}

	private static InventoryTester inventory() {
		return new InventoryTester();
	}

	private static class InventoryTester {
		private int item = appleId, quantity;
		private String itemStr = "Apple";

		public InventoryTester item(int item) {
			this.item = item;
			this.itemStr = null;
			return this;
		}

		public InventoryTester item(String itemStr) {
			this.item = 0;
			this.itemStr = itemStr;
			return this;
		}

		public InventoryTester quantity(int quantity) {
			this.quantity = quantity;
			return this;
		}

		public int insert() throws SQLException {
			if (item == 0) {
				item = items().name(itemStr).selsert();
			}

			PreparedStatement stmt = conn.prepareStatement("INSERT INTO inventory (item, quantity) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);

			int i = 1;
			stmt.setInt(i++, item);
			stmt.setInt(i++, quantity);
			stmt.executeUpdate();

			return getKey(stmt);
		}

		public int count() throws SQLException {
			ResultSet rs = query("SELECT Count(*) FROM inventory");
			rs.next();
			return rs.getInt(1);
		}

		public Map<Integer, Integer> all() throws SQLException {
			Map<Integer, Integer> map = new HashMap<>();
			ResultSet rs = query("SELECT item, quantity FROM inventory");
			while (rs.next()) {
				map.put(rs.getInt("item"), rs.getInt("quantity"));
			}
			return map;
		}
	}

	private static TransactionTester transactions() {
		return new TransactionTester();
	}

	private static class TransactionTester {
		private LocalDateTime ts = LocalDateTime.now();
		private int item = appleId, amount, quantity, balance;
		private Integer player = notchId;
		private String itemStr = "Apple", playerStr = "Notch";

		public TransactionTester ts(LocalDateTime ts) {
			this.ts = ts;
			return this;
		}

		public TransactionTester item(int item) {
			this.item = item;
			this.itemStr = null;
			return this;
		}

		public TransactionTester item(String item) {
			this.itemStr = item;
			this.item = 0;
			return this;
		}

		public TransactionTester player(Integer player) {
			this.player = player;
			this.playerStr = null;
			return this;
		}

		public TransactionTester player(String player) {
			this.playerStr = player;
			this.player = 0;
			return this;
		}

		public TransactionTester amount(int amount) {
			this.amount = amount;
			return this;
		}

		public TransactionTester quantity(int quantity) {
			this.quantity = quantity;
			return this;
		}

		public TransactionTester balance(int balance) {
			this.balance = balance;
			return this;
		}

		public void test(ResultSet rs) throws SQLException {
			assertTimestampEquals(ts, toLocalDateTime(rs.getTimestamp("ts")));
			assertEquals(item, rs.getInt("item"));
			assertEquals(player, rs.getObject("player"));
			assertEquals(balance, rs.getInt("balance"));
			assertEquals(amount, rs.getInt("amount"));
			assertEquals(quantity, rs.getInt("quantity"));
		}

		public void test(ShopTransactionDb transaction) {
			assertTimestampEquals(ts, transaction.getTs());
			assertEquals(itemStr, transaction.getItem());
			assertEquals(playerStr, transaction.getShopCustomer());
			assertEquals(quantity, transaction.getQuantity());
			assertEquals(amount, transaction.getAmount());
		}

		public int insert() throws SQLException {
			if (item == 0) {
				item = items().name(itemStr).selsert();
			}

			PreparedStatement stmt = conn.prepareStatement("INSERT INTO transactions (ts, item, player, amount, quantity, balance) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			int i = 1;
			stmt.setTimestamp(i++, timestamp(ts));
			stmt.setInt(i++, item);
			if (player == null) {
				stmt.setNull(i++, Types.INTEGER);
			} else {
				stmt.setInt(i++, player);
			}
			stmt.setInt(i++, amount);
			stmt.setInt(i++, quantity);
			stmt.setInt(i++, balance);
			stmt.executeUpdate();

			return getKey(stmt);
		}

		public int count() throws SQLException {
			ResultSet rs = query("SELECT Count(*) FROM transactions");
			rs.next();
			return rs.getInt(1);
		}

		public ResultSet all() throws SQLException {
			return query("SELECT * FROM transactions ORDER BY id");
		}

		public ShopTransactionDb dto() {
			ShopTransactionDb dto = new ShopTransactionDb();
			dto.setTs(ts);
			dto.setItem(itemStr);
			dto.setShopCustomer(playerStr);
			dto.setBalance(balance);
			dto.setAmount(amount);
			dto.setQuantity(quantity);
			return dto;
		}
	}

	private static PaymentTransactionTester paymentTransactions() {
		return new PaymentTransactionTester();
	}

	private static class PaymentTransactionTester {
		private LocalDateTime ts = LocalDateTime.now();
		private int player = notchId, amount, balance;
		private String playerStr = "Notch";
		private boolean ignore = false;
		private Integer id = null, transaction = null;

		public PaymentTransactionTester id(Integer id) {
			this.id = id;
			return this;
		}

		public PaymentTransactionTester ts(LocalDateTime ts) {
			this.ts = ts;
			return this;
		}

		public PaymentTransactionTester player(int player) {
			this.player = player;
			this.playerStr = null;
			return this;
		}

		public PaymentTransactionTester player(String player) {
			this.playerStr = player;
			this.player = 0;
			return this;
		}

		public PaymentTransactionTester amount(int amount) {
			this.amount = amount;
			return this;
		}

		public PaymentTransactionTester balance(int balance) {
			this.balance = balance;
			return this;
		}

		public PaymentTransactionTester ignore(boolean ignore) {
			this.ignore = ignore;
			return this;
		}

		public PaymentTransactionTester transaction(Integer id) {
			this.transaction = id;
			return this;
		}

		public int insert() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO payment_transactions (ts, player, amount, balance, ignore, \"transaction\") VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			int i = 1;
			stmt.setTimestamp(i++, timestamp(ts));
			stmt.setInt(i++, player);
			stmt.setInt(i++, amount);
			stmt.setInt(i++, balance);
			stmt.setBoolean(i++, ignore);
			if (transaction == null) {
				stmt.setNull(i++, Types.INTEGER);
			} else {
				stmt.setInt(i++, transaction);
			}
			stmt.executeUpdate();

			return getKey(stmt);
		}

		public void test(ResultSet rs) throws SQLException {
			if (id != null) {
				assertIntEquals(id, rs.getInt("id"));
			}
			assertTimestampEquals(ts, toLocalDateTime(rs.getTimestamp("ts")));
			assertEquals(player, rs.getInt("player"));
			assertEquals(balance, rs.getInt("balance"));
			assertEquals(amount, rs.getInt("amount"));
			assertEquals(transaction, rs.getObject("transaction"));
			assertEquals(ignore, rs.getBoolean("ignore"));
		}

		public void test(PaymentTransactionDb transaction) {
			if (id != null) {
				assertEquals(id, transaction.getId());
			}
			assertTimestampEquals(ts, transaction.getTs());
			assertEquals(playerStr, transaction.getPlayer());
			assertEquals(balance, transaction.getBalance());
			assertEquals(amount, transaction.getAmount());
		}

		public int count() throws SQLException {
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT Count(*) FROM payment_transactions");
			rs.next();
			return rs.getInt(1);
		}

		public ResultSet all() throws SQLException {
			return query("SELECT * FROM payment_transactions ORDER BY id");
		}

		public PaymentTransactionDb dto() {
			PaymentTransactionDb transaction = new PaymentTransactionDb();
			transaction.setId(id);
			transaction.setTs(ts);
			transaction.setAmount(amount);
			transaction.setBalance(balance);
			transaction.setPlayer(playerStr);
			return transaction;
		}
	}

	private static int getKey(Statement stmt) throws SQLException {
		ResultSet rs = stmt.getGeneratedKeys();
		rs.next();
		return rs.getInt(1);
	}

	private static ResultSet query(String sql) throws SQLException {
		Statement stmt = conn.createStatement();
		return stmt.executeQuery(sql);
	}

	private static Map<String, ItemGroup> itemMap(Collection<ItemGroup> itemGroups) {
		Map<String, ItemGroup> map = new HashMap<>();
		for (ItemGroup itemGroup : itemGroups) {
			map.put(itemGroup.getItem(), itemGroup);
		}
		return map;
	}

	private static Map<String, PlayerGroup> playerMap(Collection<PlayerGroup> playerGroups) {
		Map<String, PlayerGroup> map = new HashMap<>();
		for (PlayerGroup playerGroup : playerGroups) {
			map.put(playerGroup.getPlayer().getName(), playerGroup);
		}
		return map;
	}
}
