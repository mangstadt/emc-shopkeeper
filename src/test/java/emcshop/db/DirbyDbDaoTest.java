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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import emcshop.ItemIndex;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.ShopTransaction;

public class DirbyDbDaoTest {
	private static final DirbyDbDao dao;
	private static final int appleId, diamondId, notchId;
	static {
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
		assertEquals(df.parse("2014-01-01 00:00:00"), player.getFirstSeen());
		assertEquals(df.parse("2014-01-02 12:00:00"), player.getLastSeen());

		player = dao.selsertPlayer("notch");
		assertIntEquals(notchId, player.getId());
		assertEquals("Notch", player.getName());
		assertEquals(df.parse("2014-01-01 00:00:00"), player.getFirstSeen());
		assertEquals(df.parse("2014-01-02 12:00:00"), player.getLastSeen());

		player = dao.selsertPlayer("Jeb");
		assertNotNull(player.getId());
		assertEquals("Jeb", player.getName());
		assertNull(player.getFirstSeen());
		assertNull(player.getLastSeen());

	}

	@Test
	public void getEarliestTransactionDate() throws Throwable {
		assertNull(dao.getEarliestTransactionDate());

		insertTransaction("2014-01-05 00:00:00", 1, notchId, 1, 1);
		insertTransaction("2014-01-01 00:00:00", 1, notchId, 1, 1);
		insertTransaction("2014-01-10 00:00:00", 1, notchId, 1, 1);

		Date actual = dao.getEarliestTransactionDate();
		Date expected = df.parse("2014-01-01 00:00:00");
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

		insertTransaction("2014-01-01 00:00:00", a, notchId, 1, 1);
		insertTransaction("2014-01-01 00:00:00", b, notchId, 1, 1);
		insertTransaction("2014-01-01 00:00:00", c, notchId, 1, 1);
		insertTransaction("2014-01-01 00:00:00", d, notchId, 1, 1);
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

		insertTransaction("2014-01-01 00:00:00", a, 1, 1, 1);
		insertTransaction("2014-01-01 00:00:00", b, 1, 1, 1);
		insertTransaction("2014-01-01 00:00:00", c, 1, 1, 1);
		insertTransaction("2014-01-01 00:00:00", d, 1, 1, 1);
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
		ShopTransaction transaction = new ShopTransaction();
		transaction.setTs(df.parse("2014-01-01 01:23:45"));
		transaction.setItem("apple");
		transaction.setPlayer("notch");
		transaction.setBalance(1000);
		transaction.setAmount(-10);
		transaction.setQuantity(5);
		dao.insertTransaction(transaction, false);

		transaction = new ShopTransaction();
		transaction.setTs(df.parse("2014-01-01 02:23:45"));
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
		assertEquals(ts("2014-01-01 01:23:45"), rs.getTimestamp("ts"));
		assertEquals(appleId, rs.getInt("item"));
		assertEquals(notchId, rs.getInt("player"));
		assertEquals(1000, rs.getInt("balance"));
		assertEquals(-10, rs.getInt("amount"));
		assertEquals(5, rs.getInt("quantity"));

		rs.next();
		assertEquals(ts("2014-01-01 02:23:45"), rs.getTimestamp("ts"));
		assertIntEquals(dao.getItemId("Item"), rs.getInt("item"));
		assertIntEquals(getPlayerId("Jeb"), rs.getInt("player"));
		assertEquals(1200, rs.getInt("balance"));
		assertEquals(200, rs.getInt("amount"));
		assertEquals(-7, rs.getInt("quantity"));

		assertFalse(rs.next());
	}

	@Test
	public void insertTransaction_updateInventory() throws Throwable {
		insertInventory(appleId, 100);
		insertInventory(diamondId, 20);

		ShopTransaction transaction = new ShopTransaction();
		transaction.setTs(df.parse("2014-01-01 01:23:45"));
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
		assertPaymentTransaction().ts(dg.getGenerated(0)).player(notchId).balance(20000).amount(1000).transaction(null).ignore(false).test(rs);

		rs.next();
		assertPaymentTransaction().ts(dg.getGenerated(1)).player(getPlayerId("Jeb")).balance(15000).amount(-5000).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void upsertPaymentTransaction() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int jeb = insertPlayer("Jeb");
		int t1 = insertPaymentTransaction(dg.next(), notchId, 100, 20000, false, null);
		insertPaymentTransaction(dg.next(), jeb, -5000, 15000, false, null);

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
		assertPaymentTransaction().ts(dg.getGenerated(0)).player(notchId).balance(20100).amount(200).transaction(null).ignore(false).test(rs);

		rs.next();
		assertPaymentTransaction().ts(dg.getGenerated(1)).player(jeb).balance(15000).amount(-5000).transaction(null).ignore(false).test(rs);

		rs.next();
		assertPaymentTransaction().ts(dg.getGenerated(2)).player(notchId).balance(15100).amount(100).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void getPendingPaymentTransactions() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int transId = insertTransaction(dg.next(), appleId, notchId, 500, -10);
		int t1 = insertPaymentTransaction(dg.next(), notchId, 100, 20000, false, null);
		int t2 = insertPaymentTransaction(dg.next(), notchId, 200, 30000, false, null);
		insertPaymentTransaction(dg.next(), notchId, 300, 40000, true, null);
		insertPaymentTransaction(dg.next(), notchId, 400, 50000, false, transId);
		insertPaymentTransaction(dg.next(), notchId, 500, 60000, true, transId);

		Iterator<PaymentTransaction> it = dao.getPendingPaymentTransactions().iterator();

		PaymentTransaction t = it.next();
		assertPaymentTransaction().ts(dg.getGenerated(2)).player("Notch").balance(30000).amount(200).id(t2).test(t);

		t = it.next();
		assertPaymentTransaction().ts(dg.getGenerated(1)).player("Notch").balance(20000).amount(100).id(t1).test(t);

		assertFalse(it.hasNext());
	}

	@Test
	public void ignorePaymentTransaction() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int t1 = insertPaymentTransaction(dg.next(), notchId, 100, 20000, false, null);
		int t2 = insertPaymentTransaction(dg.next(), notchId, 200, 30000, true, null);

		dao.ignorePaymentTransaction(t1);
		dao.ignorePaymentTransaction(t2);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM payment_transactions ORDER BY id");

		rs.next();
		assertPaymentTransaction().ts(dg.getGenerated(0)).player(notchId).balance(20000).amount(100).transaction(null).ignore(true).test(rs);

		rs.next();
		assertPaymentTransaction().ts(dg.getGenerated(1)).player(notchId).balance(30000).amount(200).transaction(null).ignore(true).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void assignPaymentTransaction() throws Throwable {
		DateGenerator dg = new DateGenerator();
		int transId = insertTransaction(dg.next(), appleId, notchId, 500, -10);
		int t1 = insertPaymentTransaction(dg.next(), notchId, 100, 20000, false, null);
		insertPaymentTransaction(dg.next(), notchId, 200, 30000, false, null);

		dao.assignPaymentTransaction(t1, transId);

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM payment_transactions ORDER BY id");

		rs.next();
		assertPaymentTransaction().ts(dg.getGenerated(1)).player(notchId).balance(20000).amount(100).transaction(transId).ignore(false).test(rs);

		rs.next();
		assertPaymentTransaction().ts(dg.getGenerated(2)).player(notchId).balance(30000).amount(200).transaction(null).ignore(false).test(rs);

		assertFalse(rs.next());
	}

	@Test
	public void getItemGroups() throws Throwable {
		int jeb = insertPlayer("Jeb");
		DateGenerator dg = new DateGenerator();
		insertTransaction(dg.next(), appleId, notchId, -5, 100);
		insertTransaction(dg.next(), appleId, notchId, -20, 1000);
		insertTransaction(dg.next(), appleId, notchId, 10, -200);
		insertTransaction(dg.next(), appleId, notchId, 1, -20);
		insertTransaction(dg.next(), appleId, jeb, 1, -20);
		insertTransaction(dg.next(), diamondId, notchId, -1, 50);

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

	private static ItemGroupTester assertItemGroup() {
		return new ItemGroupTester();
	}

	private static PaymentTransactionTester assertPaymentTransaction() {
		return new PaymentTransactionTester();
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

	private static class PaymentTransactionTester {
		private Date ts;
		private int player, balance, amount;
		private String playerStr;
		private Integer id = null, transaction = null;
		private boolean ignore = false;

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
			return this;
		}

		public PaymentTransactionTester player(String player) {
			this.playerStr = player;
			return this;
		}

		public PaymentTransactionTester balance(int balance) {
			this.balance = balance;
			return this;
		}

		public PaymentTransactionTester amount(int amount) {
			this.amount = amount;
			return this;
		}

		public PaymentTransactionTester transaction(Integer transaction) {
			this.transaction = transaction;
			return this;
		}

		public PaymentTransactionTester ignore(boolean ignore) {
			this.ignore = ignore;
			return this;
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

	private int insertTransaction(String ts, int item, int player, int amount, int quantity) throws SQLException {
		return insertTransaction(date(ts), item, player, amount, quantity);
	}

	private int insertTransaction(Date ts, int item, int player, int amount, int quantity) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement("INSERT INTO transactions (ts, item, player, amount, quantity, balance) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

		int i = 1;
		stmt.setTimestamp(i++, new Timestamp(ts.getTime()));
		stmt.setInt(i++, item);
		stmt.setInt(i++, player);
		stmt.setInt(i++, amount);
		stmt.setInt(i++, quantity);
		stmt.setInt(i++, 0);
		stmt.executeUpdate();

		return getKey(stmt);
	}

	private int insertPaymentTransaction(String ts, int player, int amount, int balance, boolean ignore, Integer transaction) throws SQLException {
		return insertPaymentTransaction(date(ts), player, amount, balance, ignore, transaction);
	}

	private int insertPaymentTransaction(Date ts, int player, int amount, int balance, boolean ignore, Integer transaction) throws SQLException {
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

	private int insertInventory(int item, int quantity) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement("INSERT INTO inventory (item, quantity) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);

		int i = 1;
		stmt.setInt(i++, item);
		stmt.setInt(i++, quantity);
		stmt.executeUpdate();

		return getKey(stmt);
	}

	private int getKey(Statement stmt) throws SQLException {
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

	private static Date ts(String date) throws ParseException {
		return new Timestamp(date(date).getTime());
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
