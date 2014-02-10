package emcshop.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import org.junit.After;
import org.junit.Test;

import emcshop.ItemIndex;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.ShopTransaction;

public class DirbyDbDaoTest {
	private static final DirbyDbDao dao;
	private static final int appleId, diamondId, notchId;
	static {
		LogManager.getLogManager().reset();

		try {
			//@formatter:off
			String sql[] = {
				"INSERT INTO players (name, first_seen, last_seen) VALUES ('Notch', '2014-01-01 00:00:00', '2014-01-02 12:00:00')"
			};
			//@formatter:on

			dao = new DirbyMemoryDbDao();
			Statement stmt = dao.getConnection().createStatement();
			for (String s : sql) {
				stmt.execute(s);
			}
			dao.commit();

			appleId = dao.getItemId("Apple");
			diamondId = dao.getItemId("Diamond");
			notchId = 1;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Connection conn = dao.getConnection();

	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@After
	public void after() {
		dao.rollback();
	}

	@Test
	public void selectDbVersion() throws Throwable {
		assertEquals(DirbyDbDao.schemaVersion, dao.selectDbVersion());
	}

	@Test
	public void upsertDbVersion() throws Throwable {
		dao.upsertDbVersion(100);
		assertEquals(100, dao.selectDbVersion());
	}

	@Test
	public void selsertPlayer() throws Throwable {
		Player player = dao.selsertPlayer("Notch");
		assertIntEquals(notchId, player.getId());
		assertEquals("Notch", player.getName());
		assertEquals(date("2014-01-01 00:00:00"), player.getFirstSeen());
		assertEquals(date("2014-01-02 12:00:00"), player.getLastSeen());

		player = dao.selsertPlayer("notch");
		assertIntEquals(notchId, player.getId());
		assertEquals("Notch", player.getName());
		assertEquals(date("2014-01-01 00:00:00"), player.getFirstSeen());
		assertEquals(date("2014-01-02 12:00:00"), player.getLastSeen());

		player = dao.selsertPlayer("Jeb");
		assertNotNull(player.getId());
		assertEquals("Jeb", player.getName());
		assertNull(player.getFirstSeen());
		assertNull(player.getLastSeen());

	}

	@Test
	public void getEarliestTransactionDate() throws Throwable {
		assertNull(dao.getEarliestTransactionDate());

		transaction().ts("2014-01-05 00:00:00").insert();
		transaction().ts("2014-01-01 00:00:00").insert();
		transaction().ts("2014-01-10 00:00:00").insert();

		Date actual = dao.getEarliestTransactionDate();
		Date expected = date("2014-01-01 00:00:00");
		assertEquals(expected, actual);
	}

	@Test
	public void getItemId() throws Throwable {
		Integer itemId = insertItem("Item");
		assertEquals(itemId, dao.getItemId("Item"));
		assertEquals(itemId, dao.getItemId("item"));

		assertNull(dao.getItemId("does-not-exist"));
	}

	@Test
	public void upsertItem() throws Throwable {
		assertEquals(appleId, dao.upsertItem("Apple"));

		assertNull(dao.getItemId("Item"));
		int id = dao.upsertItem("Item");
		assertIntEquals(id, dao.getItemId("Item"));
	}

	@Test
	public void getItemNames() throws Throwable {
		List<String> expected = new ArrayList<String>(ItemIndex.instance().getItemNames());
		expected.add("item");
		Collections.sort(expected, String.CASE_INSENSITIVE_ORDER);

		insertItem("item");
		List<String> actual = dao.getItemNames();
		assertEquals(expected, actual);
	}

	@Test
	public void updateTransactionItem() throws Throwable {
		int a = insertItem("a");
		int b = insertItem("b");
		int c = insertItem("c");
		int d = insertItem("d");

		transaction().ts("2014-01-01 00:00:00").item(a).insert();
		transaction().ts("2014-01-01 00:00:00").item(b).insert();
		transaction().ts("2014-01-01 00:00:00").item(c).insert();
		transaction().ts("2014-01-01 00:00:00").item(d).insert();
		dao.updateTransactionItem(Arrays.asList(a, c), b);

		List<Integer> actual = new ArrayList<Integer>();
		ResultSet rs = conn.createStatement().executeQuery("SELECT item FROM transactions ORDER BY item");
		while (rs.next()) {
			actual.add(rs.getInt(1));
		}

		List<Integer> expected = Arrays.asList(b, b, b, d);
		assertEquals(expected, actual);
	}

	@Test
	public void updateItemName() throws Throwable {
		dao.updateItemName(appleId, "Pear");

		assertNull(dao.getItemId("Apple"));
		assertIntEquals(appleId, dao.getItemId("Pear"));
	}

	@Test
	public void deleteItems() throws Throwable {
		//nothing should happen if no values are passed into the method
		int itemCount = dao.getItemNames().size();
		dao.deleteItems();
		assertEquals(itemCount, dao.getItemNames().size());

		dao.deleteItems(appleId, diamondId);
		assertNull(dao.getItemId("Apple"));
		assertNull(dao.getItemId("Diamond"));
	}

	@Test
	public void populateItemsTable() throws Throwable {
		//table shouldn't be touched if no items are missing
		Map<Integer, String> before = getItemsById();
		dao.populateItemsTable();
		Map<Integer, String> after = getItemsById();
		assertEquals(before, after);

		dao.deleteItems(appleId);
		dao.populateItemsTable();
		assertNotNull(dao.getItemId("Apple"));
		assertFalse(appleId == dao.getItemId("Apple"));
	}

	@Test
	public void removeDuplicateItems() throws Throwable {
		int a = appleId;
		int b = insertItem("Apple");
		int c = insertItem("apple");
		int d = diamondId;

		transaction().ts("2014-01-01 00:00:00").item(a).insert();
		transaction().ts("2014-01-01 00:00:00").item(b).insert();
		transaction().ts("2014-01-01 00:00:00").item(c).insert();
		transaction().ts("2014-01-01 00:00:00").item(d).insert();
		insertInventory(a, 1);
		insertInventory(b, 2);
		insertInventory(c, 3);
		insertInventory(d, 4);

		dao.removeDuplicateItems();

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT Count(*) FROM items WHERE name = 'Apple'");
		rs.next();
		assertEquals(1, rs.getInt(1));

		stmt = conn.createStatement();
		rs = stmt.executeQuery("SELECT Count(*) FROM inventory i INNER JOIN items it ON i.item = it.id WHERE Lower(it.name) = 'apple'");
		rs.next();
		assertEquals(1, rs.getInt(1));

		stmt = conn.createStatement();
		rs = stmt.executeQuery("SELECT i.quantity FROM inventory i INNER JOIN items it ON i.item = it.id WHERE Lower(it.name) = 'apple'");
		rs.next();
		assertEquals(6, rs.getInt(1));
	}

	@Test
	public void insertTransaction() throws Throwable {
		DateGenerator dg = new DateGenerator();
		ShopTransaction transaction = new ShopTransaction();
		transaction.setTs(dg.next());
		transaction.setItem("apple");
		transaction.setPlayer("notch");
		transaction.setBalance(1000);
		transaction.setAmount(-10);
		transaction.setQuantity(5);
		dao.insertTransaction(transaction, false);

		transaction = new ShopTransaction();
		transaction.setTs(dg.next());
		transaction.setItem("Item");
		transaction.setPlayer("Jeb");
		transaction.setBalance(1200);
		transaction.setAmount(200);
		transaction.setQuantity(-7);
		dao.insertTransaction(transaction, false);

		assertNotNull(dao.getItemId("Item"));

		List<String> actualNames = getPlayerNames();
		assertEquals(2, actualNames.size());
		assertTrue(actualNames.contains("Notch"));
		assertTrue(actualNames.contains("Jeb"));

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM transactions ORDER BY id");

		rs.next();
		assertEquals(dg.getGenerated(0), rs.getTimestamp("ts"));
		assertEquals(appleId, rs.getInt("item"));
		assertEquals(notchId, rs.getInt("player"));
		assertEquals(1000, rs.getInt("balance"));
		assertEquals(-10, rs.getInt("amount"));
		assertEquals(5, rs.getInt("quantity"));

		rs.next();
		assertEquals(dg.getGenerated(1), rs.getTimestamp("ts"));
		assertIntEquals(dao.getItemId("Item"), rs.getInt("item"));
		assertIntEquals(getPlayerId("Jeb"), rs.getInt("player"));
		assertEquals(1200, rs.getInt("balance"));
		assertEquals(200, rs.getInt("amount"));
		assertEquals(-7, rs.getInt("quantity"));

		assertFalse(rs.next());
	}

	@Test
	public void insertTransaction_updateInventory() throws Throwable {
		DateGenerator dg = new DateGenerator();
		insertInventory(appleId, 100);
		insertInventory(diamondId, 20);

		ShopTransaction transaction = new ShopTransaction();
		transaction.setTs(dg.next());
		transaction.setItem("apple");
		transaction.setPlayer("notch");
		transaction.setBalance(1000);
		transaction.setAmount(10);

		transaction.setQuantity(5);
		dao.insertTransaction(transaction, false);

		transaction.setQuantity(5);
		dao.insertTransaction(transaction, true);

		transaction.setQuantity(-15);
		dao.insertTransaction(transaction, true);

		Map<Integer, Integer> actual = getInventoryByItem();
		Map<Integer, Integer> expected = new HashMap<Integer, Integer>();
		expected.put(appleId, 90);
		expected.put(diamondId, 20);
		assertEquals(expected, actual);
	}

	@Test
	public void insertPaymentTransactions() throws Throwable {
		DateGenerator dg = new DateGenerator();
		PaymentTransaction t1 = new PaymentTransaction();
		t1.setAmount(1000);
		t1.setBalance(20000);
		t1.setPlayer("Notch");
		t1.setTs(dg.next());

		PaymentTransaction t2 = new PaymentTransaction();
		t2.setAmount(-5000);
		t2.setBalance(15000);
		t2.setPlayer("Jeb");
		t2.setTs(dg.next());

		dao.insertPaymentTransactions(Arrays.asList(t1, t2));
		assertNull(t1.getId()); //IDs are not retrieved
		assertNull(t2.getId());

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM payment_transactions ORDER BY id");

		rs.next();
		paymentTransaction().ts(dg.getGenerated(0)).player(notchId).balance(20000).amount(1000).transaction(null).ignore(false).test(rs);

		rs.next();
		paymentTransaction().ts(dg.getGenerated(1)).player(getPlayerId("Jeb")).balance(15000).amount(-5000).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void upsertPaymentTransaction() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int jeb = insertPlayer("Jeb");
		int t1 = paymentTransaction().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		paymentTransaction().ts(dg.next()).player(jeb).amount(-5000).balance(15000).ignore(false).transaction(null).insert();

		PaymentTransaction t3 = new PaymentTransaction();
		t3.setAmount(100);
		t3.setBalance(15100);
		t3.setPlayer("Notch");
		t3.setTs(dg.next());

		PaymentTransaction t4 = new PaymentTransaction();
		t4.setId(t1);
		t4.setAmount(200);
		t4.setBalance(20100);

		dao.upsertPaymentTransaction(t3);
		assertNotNull(t3.getId());

		dao.upsertPaymentTransaction(t4);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM payment_transactions ORDER BY id");

		rs.next();
		paymentTransaction().ts(dg.getGenerated(0)).player(notchId).balance(20100).amount(200).transaction(null).ignore(false).test(rs);

		rs.next();
		paymentTransaction().ts(dg.getGenerated(1)).player(jeb).balance(15000).amount(-5000).transaction(null).ignore(false).test(rs);

		rs.next();
		paymentTransaction().ts(dg.getGenerated(2)).player(notchId).balance(15100).amount(100).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void getPendingPaymentTransactions() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int transId = transaction().ts(dg.next()).item(appleId).player(notchId).amount(500).quantity(-10).insert();
		int t1 = paymentTransaction().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		int t2 = paymentTransaction().ts(dg.next()).player(notchId).amount(200).balance(30000).ignore(false).transaction(null).insert();
		paymentTransaction().ts(dg.next()).player(notchId).amount(300).balance(40000).ignore(true).transaction(null).insert();
		paymentTransaction().ts(dg.next()).player(notchId).amount(400).balance(50000).ignore(false).transaction(transId).insert();
		paymentTransaction().ts(dg.next()).player(notchId).amount(500).balance(60000).ignore(true).transaction(transId).insert();

		Iterator<PaymentTransaction> it = dao.getPendingPaymentTransactions().iterator();

		PaymentTransaction t = it.next();
		paymentTransaction().ts(dg.getGenerated(2)).player("Notch").balance(30000).amount(200).id(t2).test(t);

		t = it.next();
		paymentTransaction().ts(dg.getGenerated(1)).player("Notch").balance(20000).amount(100).id(t1).test(t);

		assertFalse(it.hasNext());
	}

	@Test
	public void ignorePaymentTransaction() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int t1 = paymentTransaction().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		int t2 = paymentTransaction().ts(dg.next()).player(notchId).amount(200).balance(30000).ignore(true).transaction(null).insert();

		dao.ignorePaymentTransaction(t1);
		dao.ignorePaymentTransaction(t2);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM payment_transactions ORDER BY id");

		rs.next();
		paymentTransaction().ts(dg.getGenerated(0)).player(notchId).balance(20000).amount(100).transaction(null).ignore(true).test(rs);

		rs.next();
		paymentTransaction().ts(dg.getGenerated(1)).player(notchId).balance(30000).amount(200).transaction(null).ignore(true).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void assignPaymentTransaction() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int transId = transaction().ts(dg.next()).item(appleId).player(notchId).amount(500).quantity(-10).insert();
		int t1 = paymentTransaction().ts(dg.next()).player(notchId).amount(100).balance(20000).ignore(false).transaction(null).insert();
		paymentTransaction().ts(dg.next()).player(notchId).amount(200).balance(30000).ignore(false).transaction(null).insert();

		dao.assignPaymentTransaction(t1, transId);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM payment_transactions ORDER BY id");

		rs.next();
		paymentTransaction().ts(dg.getGenerated(1)).player(notchId).balance(20000).amount(100).transaction(transId).ignore(false).test(rs);

		rs.next();
		paymentTransaction().ts(dg.getGenerated(2)).player(notchId).balance(30000).amount(200).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void getItemGroups() throws Throwable {
		assertTrue(dao.getItemGroups(null, null).isEmpty());

		int jeb = insertPlayer("Jeb");
		DateGenerator dg = new DateGenerator();
		transaction().ts(dg.next()).item(appleId).player(notchId).amount(-5).quantity(100).insert();
		transaction().ts(dg.next()).item(appleId).player(notchId).amount(-20).quantity(1000).insert();
		transaction().ts(dg.next()).item(appleId).player(notchId).amount(10).quantity(-200).insert();
		transaction().ts(dg.next()).item(appleId).player(notchId).amount(1).quantity(-20).insert();
		transaction().ts(dg.next()).item(appleId).player(jeb).amount(1).quantity(-20).insert();
		transaction().ts(dg.next()).item(diamondId).player(notchId).amount(-1).quantity(50).insert();

		//no date range
		{
			Map<String, ItemGroup> groups = dao.getItemGroups(null, null);
			assertEquals(2, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			assertItemGroup().item("Apple").boughtAmt(-25).boughtQty(1100).soldAmt(12).soldQty(-240).test(itemGroup);

			itemGroup = groups.get("Diamond");
			assertItemGroup().item("Diamond").boughtAmt(-1).boughtQty(50).soldAmt(0).soldQty(0).test(itemGroup);
		}

		//start date (first transaction is not included)
		{
			Map<String, ItemGroup> groups = dao.getItemGroups(dg.getGenerated(1), null);
			assertEquals(2, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			assertItemGroup().item("Apple").boughtAmt(-20).boughtQty(1000).soldAmt(12).soldQty(-240).test(itemGroup);

			itemGroup = groups.get("Diamond");
			assertItemGroup().item("Diamond").boughtAmt(-1).boughtQty(50).soldAmt(0).soldQty(0).test(itemGroup);
		}

		//end date (last transaction is not included)
		{
			Map<String, ItemGroup> groups = dao.getItemGroups(null, dg.getGenerated(5));
			assertEquals(1, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			assertItemGroup().item("Apple").boughtAmt(-25).boughtQty(1100).soldAmt(12).soldQty(-240).test(itemGroup);
		}

		//start and end date (first and last transactions are not included)
		{
			Map<String, ItemGroup> groups = dao.getItemGroups(dg.getGenerated(1), dg.getGenerated(5));
			assertEquals(1, groups.size());

			ItemGroup itemGroup = groups.get("Apple");
			assertItemGroup().item("Apple").boughtAmt(-20).boughtQty(1000).soldAmt(12).soldQty(-240).test(itemGroup);
		}

		//start and end date (no transactions included)
		{
			Map<String, ItemGroup> groups = dao.getItemGroups(dg.next(), dg.next());
			assertEquals(0, groups.size());
		}
	}

	@Test
	public void getTransactionsByDate() throws Throwable {
		assertTrue(dao.getTransactionsByDate(null, null).isEmpty());

		int jeb = insertPlayer("Jeb");
		transaction().ts("2014-01-01 01:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 01:00:00").item(appleId).player(notchId).amount(-10).quantity(1).insert();
		transaction().ts("2014-01-01 01:00:01").item(diamondId).player(notchId).amount(1000).quantity(-5).insert();
		transaction().ts("2014-01-01 01:00:02").item(appleId).player(jeb).amount(-10).quantity(1).insert();
		transaction().ts("2014-01-01 01:00:04").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 01:00:05").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 01:01:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 01:03:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 01:05:01").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 01:10:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();

		//no date range
		{
			Iterator<ShopTransaction> it = dao.getTransactionsByDate(null, null).iterator();

			ShopTransaction t = it.next();
			assertEquals(date("2014-01-01 01:00:00"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-49, t.getQuantity());
			assertEquals(490, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:00:01"), t.getTs());
			assertEquals("Diamond", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-5, t.getQuantity());
			assertEquals(1000, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:00:02"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Jeb", t.getPlayer());
			assertEquals(1, t.getQuantity());
			assertEquals(-10, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:05:01"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-10, t.getQuantity());
			assertEquals(100, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:10:00"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-10, t.getQuantity());
			assertEquals(100, t.getAmount());

			assertFalse(it.hasNext());
		}

		//with start date
		{
			Iterator<ShopTransaction> it = dao.getTransactionsByDate(date("2014-01-01 01:00:02"), null).iterator();

			ShopTransaction t = it.next();
			assertEquals(date("2014-01-01 01:00:02"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Jeb", t.getPlayer());
			assertEquals(1, t.getQuantity());
			assertEquals(-10, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:00:04"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-40, t.getQuantity());
			assertEquals(400, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:05:01"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-10, t.getQuantity());
			assertEquals(100, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:10:00"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-10, t.getQuantity());
			assertEquals(100, t.getAmount());

			assertFalse(it.hasNext());
		}

		//with end date
		{
			Iterator<ShopTransaction> it = dao.getTransactionsByDate(null, date("2014-01-01 01:01:00")).iterator();

			ShopTransaction t = it.next();
			assertEquals(date("2014-01-01 01:00:00"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-29, t.getQuantity());
			assertEquals(290, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:00:01"), t.getTs());
			assertEquals("Diamond", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-5, t.getQuantity());
			assertEquals(1000, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:00:02"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Jeb", t.getPlayer());
			assertEquals(1, t.getQuantity());
			assertEquals(-10, t.getAmount());

			assertFalse(it.hasNext());
		}

		//with start and end dates
		{
			Iterator<ShopTransaction> it = dao.getTransactionsByDate(date("2014-01-01 01:00:02"), date("2014-01-01 01:01:00")).iterator();

			ShopTransaction t = it.next();
			assertEquals(date("2014-01-01 01:00:02"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Jeb", t.getPlayer());
			assertEquals(1, t.getQuantity());
			assertEquals(-10, t.getAmount());

			t = it.next();
			assertEquals(date("2014-01-01 01:00:04"), t.getTs());
			assertEquals("Apple", t.getItem());
			assertEquals("Notch", t.getPlayer());
			assertEquals(-20, t.getQuantity());
			assertEquals(200, t.getAmount());

			assertFalse(it.hasNext());
		}
	}

	@Test
	public void getPlayerGroups() throws Throwable {
		assertTrue(dao.getPlayerGroups(null, null).isEmpty());

		DateGenerator dg = new DateGenerator();
		int jeb = insertPlayer("Jeb");
		transaction().ts(dg.next()).item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts(dg.next()).item(diamondId).player(notchId).amount(1000).quantity(-5).insert();
		transaction().ts(dg.next()).item(appleId).player(jeb).amount(-10).quantity(1).insert();
		transaction().ts(dg.next()).item(appleId).player(notchId).amount(100).quantity(-10).insert();

		//no date range
		{
			Map<String, PlayerGroup> groups = dao.getPlayerGroups(null, null);
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				assertEquals("Notch", player.getName());
				assertIntEquals(notchId, player.getId());
				assertEquals(date("2014-01-01 00:00:00"), player.getFirstSeen());
				assertEquals(date("2014-01-02 12:00:00"), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(2, items.size());
				ItemGroup ig = items.get("Apple");
				assertItemGroup().item("Apple").boughtAmt(0).boughtQty(0).soldAmt(200).soldQty(-20).test(ig);
				ig = items.get("Diamond");
				assertItemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				assertEquals("Jeb", player.getName());
				assertIntEquals(jeb, player.getId());
				assertEquals(dg.getGenerated(2), player.getFirstSeen());
				assertEquals(dg.getGenerated(2), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				assertItemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}

		//with start date
		{
			Map<String, PlayerGroup> groups = dao.getPlayerGroups(dg.getGenerated(1), null);
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				assertEquals("Notch", player.getName());
				assertIntEquals(notchId, player.getId());
				assertEquals(date("2014-01-01 00:00:00"), player.getFirstSeen());
				assertEquals(date("2014-01-02 12:00:00"), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(2, items.size());
				ItemGroup ig = items.get("Apple");
				assertItemGroup().item("Apple").boughtAmt(0).boughtQty(0).soldAmt(100).soldQty(-10).test(ig);
				ig = items.get("Diamond");
				assertItemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				assertEquals("Jeb", player.getName());
				assertIntEquals(jeb, player.getId());
				assertEquals(dg.getGenerated(2), player.getFirstSeen());
				assertEquals(dg.getGenerated(2), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				assertItemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}

		//with end date
		{
			Map<String, PlayerGroup> groups = dao.getPlayerGroups(null, dg.getGenerated(3));
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				assertEquals("Notch", player.getName());
				assertIntEquals(notchId, player.getId());
				assertEquals(date("2014-01-01 00:00:00"), player.getFirstSeen());
				assertEquals(date("2014-01-02 12:00:00"), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(2, items.size());
				ItemGroup ig = items.get("Apple");
				assertItemGroup().item("Apple").boughtAmt(0).boughtQty(0).soldAmt(100).soldQty(-10).test(ig);
				ig = items.get("Diamond");
				assertItemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				assertEquals("Jeb", player.getName());
				assertIntEquals(jeb, player.getId());
				assertEquals(dg.getGenerated(2), player.getFirstSeen());
				assertEquals(dg.getGenerated(2), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				assertItemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}

		//with start and end dates
		{
			Map<String, PlayerGroup> groups = dao.getPlayerGroups(dg.getGenerated(1), dg.getGenerated(3));
			assertEquals(2, groups.size());

			{
				PlayerGroup group = groups.get("Notch");

				Player player = group.getPlayer();
				assertEquals("Notch", player.getName());
				assertIntEquals(notchId, player.getId());
				assertEquals(date("2014-01-01 00:00:00"), player.getFirstSeen());
				assertEquals(date("2014-01-02 12:00:00"), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Diamond");
				assertItemGroup().item("Diamond").boughtAmt(0).boughtQty(0).soldAmt(1000).soldQty(-5).test(ig);
			}

			{
				PlayerGroup group = groups.get("Jeb");

				Player player = group.getPlayer();
				assertEquals("Jeb", player.getName());
				assertIntEquals(jeb, player.getId());
				assertEquals(dg.getGenerated(2), player.getFirstSeen());
				assertEquals(dg.getGenerated(2), player.getLastSeen());

				Map<String, ItemGroup> items = group.getItems();
				assertEquals(1, items.size());
				ItemGroup ig = items.get("Apple");
				assertItemGroup().item("Apple").boughtAmt(-10).boughtQty(1).soldAmt(0).soldQty(0).test(ig);
			}
		}
	}

	@Test
	public void getInventory() throws Throwable {
		assertTrue(dao.getInventory().isEmpty());

		insertInventory(appleId, 5);
		insertInventory(diamondId, 10);

		List<Inventory> inventory = dao.getInventory();
		assertEquals(2, inventory.size());
		Collections.sort(inventory, new Comparator<Inventory>() {
			@Override
			public int compare(Inventory a, Inventory b) {
				return a.getItem().compareTo(b.getItem());
			}
		});

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
	public void updateInventoryItem() throws Throwable {
		int a = insertItem("a");
		int b = insertItem("b");
		int c = insertItem("c");
		insertInventory(appleId, 5);
		insertInventory(a, 1);
		insertInventory(b, -2);
		insertInventory(c, 3);

		dao.updateInventoryItem(Arrays.asList(a, c), b);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT item, quantity FROM inventory ORDER BY id");

		rs.next();
		assertEquals(appleId, rs.getInt("item"));
		assertEquals(5, rs.getInt("quantity"));

		rs.next();
		assertEquals(b, rs.getInt("item"));
		assertEquals(2, rs.getInt("quantity"));

		assertFalse(rs.next());
	}

	@Test
	public void upsertInventory() throws Throwable {
		insertInventory(appleId, 5);

		dao.upsertInventory("Apple", 10, false);
		dao.upsertInventory("Apple", 1, true);
		dao.upsertInventory("Apple", -4, true);
		dao.upsertInventory("Diamond", 1, true);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT item, quantity FROM inventory ORDER BY id");

		rs.next();
		assertEquals(appleId, rs.getInt("item"));
		assertEquals(7, rs.getInt("quantity"));

		rs.next();
		assertEquals(diamondId, rs.getInt("item"));
		assertEquals(1, rs.getInt("quantity"));

		assertFalse(rs.next());
	}

	@Test
	public void deleteInventory() throws Throwable {
		int a = insertItem("a");
		int b = insertItem("b");
		int c = insertItem("c");
		int ai = insertInventory(a, 1);
		insertInventory(b, 2);
		int ci = insertInventory(c, 3);

		dao.deleteInventory(Arrays.asList(ai, ci));

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT item, quantity FROM inventory ORDER BY id");

		rs.next();
		assertEquals(b, rs.getInt("item"));
		assertEquals(2, rs.getInt("quantity"));

		dao.deleteInventory(Arrays.<Integer> asList());

		stmt = conn.createStatement();
		rs = stmt.executeQuery("SELECT item, quantity FROM inventory ORDER BY id");

		rs.next();
		assertEquals(b, rs.getInt("item"));
		assertEquals(2, rs.getInt("quantity"));
	}

	@Test
	public void updateBonusesFees() throws Throwable {
		List<BonusFeeTransaction> transactions = new ArrayList<BonusFeeTransaction>();

		dao.updateBonusesFees(transactions);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM bonuses_fees");
		rs.next();
		assertNull(rs.getTimestamp("since"));
		assertEquals(0, rs.getInt("horse"));
		assertEquals(0, rs.getInt("lock"));
		assertEquals(0, rs.getInt("eggify"));
		assertEquals(0, rs.getInt("vault"));
		assertEquals(0, rs.getInt("sign_in"));
		assertEquals(0, rs.getInt("vote"));
		assertFalse(rs.next());

		BonusFeeTransaction t = new BonusFeeTransaction();
		t.setAmount(400);
		t.setSignInBonus(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(400);
		t.setSignInBonus(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(-100);
		t.setHorseFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(-100);
		t.setHorseFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(-1000);
		t.setLockFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(500);
		t.setLockFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(-100);
		t.setEggifyFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(-100);
		t.setEggifyFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(-10);
		t.setVaultFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(-10);
		t.setVaultFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(100);
		t.setVoteBonus(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(200);
		t.setVoteBonus(true);
		transactions.add(t);

		dao.updateBonusesFees(transactions);

		stmt = conn.createStatement();
		rs = stmt.executeQuery("SELECT * FROM bonuses_fees");
		rs.next();
		assertNull(rs.getTimestamp("since"));
		assertEquals(-200, rs.getInt("horse"));
		assertEquals(-500, rs.getInt("lock"));
		assertEquals(-200, rs.getInt("eggify"));
		assertEquals(-20, rs.getInt("vault"));
		assertEquals(800, rs.getInt("sign_in"));
		assertEquals(300, rs.getInt("vote"));
		assertFalse(rs.next());

		transactions.clear();

		t = new BonusFeeTransaction();
		t.setAmount(-10);
		t.setVaultFee(true);
		transactions.add(t);

		t = new BonusFeeTransaction();
		t.setAmount(100);
		t.setVoteBonus(true);
		transactions.add(t);

		dao.updateBonusesFees(transactions);

		stmt = conn.createStatement();
		rs = stmt.executeQuery("SELECT * FROM bonuses_fees");
		rs.next();
		assertNull(rs.getTimestamp("since"));
		assertEquals(-200, rs.getInt("horse"));
		assertEquals(-500, rs.getInt("lock"));
		assertEquals(-200, rs.getInt("eggify"));
		assertEquals(-30, rs.getInt("vault"));
		assertEquals(800, rs.getInt("sign_in"));
		assertEquals(400, rs.getInt("vote"));
		assertFalse(rs.next());
	}

	@Test
	public void getBonusesFees() throws Throwable {
		Statement stmt = conn.createStatement();
		stmt.executeUpdate("UPDATE bonuses_fees SET since = '2014-01-01 00:00:00', horse = 1, lock = 2, eggify = 3, vault = 4, sign_in = 5, vote = 6");

		BonusFee bonusFee = dao.getBonusesFees();
		assertEquals(date("2014-01-01 00:00:00"), bonusFee.getSince());
		assertEquals(1, bonusFee.getHorse());
		assertEquals(2, bonusFee.getLock());
		assertEquals(3, bonusFee.getEggify());
		assertEquals(4, bonusFee.getVault());
		assertEquals(5, bonusFee.getSignIn());
		assertEquals(6, bonusFee.getVote());
	}

	@Test
	public void updateBonusesFeesSince() throws Throwable {
		DateGenerator dg = new DateGenerator();
		dao.updateBonusesFeesSince(dg.next());

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT since FROM bonuses_fees");
		rs.next();
		assertEquals(dg.getGenerated(0).getTime(), rs.getTimestamp("since").getTime());
		assertFalse(rs.next());

		dao.updateBonusesFeesSince(dg.next());

		stmt = conn.createStatement();
		rs = stmt.executeQuery("SELECT since FROM bonuses_fees");
		rs.next();
		assertEquals(dg.getGenerated(0).getTime(), rs.getTimestamp("since").getTime());
		assertFalse(rs.next());
	}

	@Test
	public void getProfitsByDay() throws Throwable {
		assertTrue(dao.getProfitsByDay(null, null).isEmpty());

		transaction().ts("2014-01-01 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-01 12:00:00").item(diamondId).player(notchId).amount(-50).quantity(5).insert();
		transaction().ts("2014-01-01 12:00:00").item(diamondId).player(notchId).amount(-50).quantity(5).insert();
		transaction().ts("2014-01-01 12:00:00").item(diamondId).player(notchId).amount(10).quantity(-1).insert();
		transaction().ts("2014-01-03 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-04 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();

		//no date range
		{
			Map<Date, Profits> profits = dao.getProfitsByDay(null, null);
			assertEquals(3, profits.size());

			Profits p = profits.get(date("2014-01-01 00:00:00"));
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());

			p = profits.get(date("2014-01-03 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());

			p = profits.get(date("2014-01-04 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with start date
		{
			Map<Date, Profits> profits = dao.getProfitsByDay(date("2014-01-03 00:00:00"), null);
			assertEquals(2, profits.size());

			Profits p = profits.get(date("2014-01-03 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());

			p = profits.get(date("2014-01-04 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with end date
		{
			Map<Date, Profits> profits = dao.getProfitsByDay(null, date("2014-01-04 00:00:00"));
			assertEquals(2, profits.size());

			Profits p = profits.get(date("2014-01-01 00:00:00"));
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());

			p = profits.get(date("2014-01-03 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with start and end dates
		{
			Map<Date, Profits> profits = dao.getProfitsByDay(date("2014-01-03 00:00:00"), date("2014-01-04 00:00:00"));
			assertEquals(1, profits.size());

			Profits p = profits.get(date("2014-01-03 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}
	}

	@Test
	public void getProfitsByMonth() throws Throwable {
		assertTrue(dao.getProfitsByMonth(null, null).isEmpty());

		transaction().ts("2014-01-01 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-02 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-01-03 00:00:00").item(diamondId).player(notchId).amount(-50).quantity(5).insert();
		transaction().ts("2014-01-04 00:00:00").item(diamondId).player(notchId).amount(-50).quantity(5).insert();
		transaction().ts("2014-01-05 00:00:00").item(diamondId).player(notchId).amount(10).quantity(-1).insert();
		transaction().ts("2014-02-01 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();
		transaction().ts("2014-04-01 00:00:00").item(appleId).player(notchId).amount(100).quantity(-10).insert();

		//no date range
		{
			Map<Date, Profits> profits = dao.getProfitsByMonth(null, null);
			assertEquals(3, profits.size());

			Profits p = profits.get(date("2014-01-01 00:00:00"));
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());

			p = profits.get(date("2014-02-01 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());

			p = profits.get(date("2014-04-01 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with start date
		{
			Map<Date, Profits> profits = dao.getProfitsByMonth(date("2014-02-01 00:00:00"), null);
			assertEquals(2, profits.size());

			Profits p = profits.get(date("2014-02-01 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());

			p = profits.get(date("2014-04-01 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with end date
		{
			Map<Date, Profits> profits = dao.getProfitsByMonth(null, date("2014-04-01 00:00:00"));
			assertEquals(2, profits.size());

			Profits p = profits.get(date("2014-01-01 00:00:00"));
			assertEquals(2, p.getCustomerTotals().size());
			assertIntEquals(200, p.getCustomerTotals().get("apple"));
			assertIntEquals(10, p.getCustomerTotals().get("diamond"));
			assertEquals(1, p.getSupplierTotals().size());
			assertIntEquals(-100, p.getSupplierTotals().get("diamond"));
			assertEquals(210, p.getCustomerTotal());
			assertEquals(-100, p.getSupplierTotal());

			p = profits.get(date("2014-02-01 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}

		//with start and end dates
		{
			Map<Date, Profits> profits = dao.getProfitsByMonth(date("2014-02-01 00:00:00"), date("2014-04-01 00:00:00"));
			assertEquals(1, profits.size());

			Profits p = profits.get(date("2014-02-01 00:00:00"));
			assertEquals(1, p.getCustomerTotals().size());
			assertIntEquals(100, p.getCustomerTotals().get("apple"));
			assertEquals(0, p.getSupplierTotals().size());
			assertEquals(100, p.getCustomerTotal());
			assertEquals(0, p.getSupplierTotal());
		}
	}

	private static ItemGroupTester assertItemGroup() {
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

	private List<String> getPlayerNames() throws SQLException {
		List<String> names = new ArrayList<String>();
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT name FROM players");
		while (rs.next()) {
			names.add(rs.getString(1));
		}
		return names;
	}

	private Integer getPlayerId(String name) throws SQLException {
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT id FROM players WHERE name = '" + name + "'");
		return rs.next() ? rs.getInt(1) : null;
	}

	private Map<Integer, String> getItemsById() throws SQLException {
		Map<Integer, String> map = new HashMap<Integer, String>();
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT id, name FROM items");
		while (rs.next()) {
			map.put(rs.getInt("id"), rs.getString("name"));
		}
		return map;
	}

	private Map<Integer, Integer> getInventoryByItem() throws SQLException {
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();
		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT item, quantity FROM inventory");
		while (rs.next()) {
			map.put(rs.getInt("item"), rs.getInt("quantity"));
		}
		return map;
	}

	private int insertPlayer(String name) throws SQLException {
		Statement stmt = conn.createStatement();
		stmt.executeUpdate("INSERT INTO players (name) VALUES ('" + name + "')", Statement.RETURN_GENERATED_KEYS);
		return getKey(stmt);
	}

	private int insertItem(String name) throws SQLException {
		Statement stmt = conn.createStatement();
		stmt.executeUpdate("INSERT INTO items (name) VALUES ('" + name + "')", Statement.RETURN_GENERATED_KEYS);
		return getKey(stmt);
	}

	private static TransactionInserter transaction() {
		return new TransactionInserter();
	}

	private static class TransactionInserter {
		private Date ts = new Date();
		private int item = appleId, player = notchId, amount, quantity, balance;

		public TransactionInserter ts(String ts) {
			return ts(date(ts));
		}

		public TransactionInserter ts(Date ts) {
			this.ts = ts;
			return this;
		}

		public TransactionInserter item(int item) {
			this.item = item;
			return this;
		}

		public TransactionInserter player(int player) {
			this.player = player;
			return this;
		}

		public TransactionInserter amount(int amount) {
			this.amount = amount;
			return this;
		}

		public TransactionInserter quantity(int quantity) {
			this.quantity = quantity;
			return this;
		}

		public int insert() throws SQLException {
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO transactions (ts, item, player, amount, quantity, balance) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			int i = 1;
			stmt.setTimestamp(i++, new Timestamp(ts.getTime()));
			stmt.setInt(i++, item);
			stmt.setInt(i++, player);
			stmt.setInt(i++, amount);
			stmt.setInt(i++, quantity);
			stmt.setInt(i++, balance);
			stmt.executeUpdate();

			return getKey(stmt);
		}
	}

	private static PaymentTransactionTester paymentTransaction() {
		return new PaymentTransactionTester();
	}

	private static class PaymentTransactionTester {
		private Date ts = new Date();
		private int player = notchId, amount, balance;
		private String playerStr = "Notch";
		private boolean ignore = false;
		private Integer id = null, transaction = null;

		public PaymentTransactionTester id(Integer id) {
			this.id = id;
			return this;
		}

		public PaymentTransactionTester ts(Date ts) {
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
			stmt.setTimestamp(i++, new Timestamp(ts.getTime()));
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
			assertEquals(ts.getTime(), rs.getTimestamp("ts").getTime());
			assertEquals(player, rs.getInt("player"));
			assertEquals(balance, rs.getInt("balance"));
			assertEquals(amount, rs.getInt("amount"));
			assertEquals(transaction, rs.getObject("transaction"));
			assertEquals(ignore, rs.getBoolean("ignore"));
		}

		public void test(PaymentTransaction transaction) throws SQLException {
			if (id != null) {
				assertEquals(id, transaction.getId());
			}
			assertEquals(ts, transaction.getTs());
			assertEquals(playerStr, transaction.getPlayer());
			assertEquals(balance, transaction.getBalance());
			assertEquals(amount, transaction.getAmount());
		}
	}

	private int insertInventory(int item, int quantity) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement("INSERT INTO inventory (item, quantity) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);

		int i = 1;
		stmt.setInt(i++, item);
		stmt.setInt(i++, quantity);
		stmt.executeUpdate();

		return getKey(stmt);
	}

	private static int getKey(Statement stmt) throws SQLException {
		ResultSet rs = stmt.getGeneratedKeys();
		rs.next();
		return rs.getInt(1);
	}

	private static Date date(String date) {
		try {
			return df.parse(date);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	private static void assertIntEquals(Integer expected, int actual) {
		assertNotNull(expected);
		assertEquals(expected.intValue(), actual);
	}

	private static void assertIntEquals(int expected, Integer actual) {
		assertNotNull(actual);
		assertEquals(Integer.valueOf(expected), actual);
	}

	private static class DateGenerator {
		private final Calendar c = Calendar.getInstance();
		private final List<Date> generated = new ArrayList<Date>();

		public Date next() {
			c.add(Calendar.HOUR_OF_DAY, 1);
			Date d = c.getTime();
			generated.add(d);
			return d;
		}

		public Date getGenerated(int index) {
			return generated.get(index);
		}
	}
}
