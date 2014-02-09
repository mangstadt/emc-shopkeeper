package emcshop.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.After;
import org.junit.Test;

public class DirbyDbDaoTest {
	private static final DirbyDbDao dao;
	private static final Connection conn;
	static {
		try {
			dao = new DirbyMemoryDbDao();
			conn = dao.getConnection();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

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
		insertPlayer("Jeb");

		Player player = dao.selsertPlayer("jeb");
		assertEquals(Integer.valueOf(1), player.getId());
		assertEquals("Jeb", player.getName());
		assertNull(player.getFirstSeen());
		assertNull(player.getLastSeen());

		player = dao.selsertPlayer("Notch");
		assertEquals(Integer.valueOf(2), player.getId());
		assertEquals("Notch", player.getName());
		assertNull(player.getFirstSeen());
		assertNull(player.getLastSeen());

		player = dao.selsertPlayer("notch");
		assertEquals(Integer.valueOf(2), player.getId());
		assertEquals("Notch", player.getName());
		assertNull(player.getFirstSeen());
		assertNull(player.getLastSeen());
	}

	@Test
	public void getEarliestTransactionDate() throws Throwable {
		assertNull(dao.getEarliestTransactionDate());

		int itemId = insertItem("Apple");
		int playerId = insertPlayer("Notch");
		insertTransaction("2014-01-05 00:00:00", itemId, playerId, 1, 1);
		insertTransaction("2014-01-01 00:00:00", itemId, playerId, 1, 1);
		insertTransaction("2014-01-10 00:00:00", itemId, playerId, 1, 1);

		Date actual = dao.getEarliestTransactionDate();
		Date expected = df.parse("2014-01-01 00:00:00");
		assertEquals(expected, actual);
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
		PreparedStatement stmt = conn.prepareStatement("INSERT INTO transactions (ts, item, player, amount, quantity, balance) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

		int i = 1;
		stmt.setString(i++, ts);
		stmt.setInt(i++, item);
		stmt.setInt(i++, player);
		stmt.setInt(i++, amount);
		stmt.setInt(i++, quantity);
		stmt.setInt(i++, 0);
		stmt.executeUpdate();

		return getKey(stmt);
	}

	private int getKey(Statement stmt) throws SQLException {
		ResultSet rs = stmt.getGeneratedKeys();
		rs.next();
		return rs.getInt(1);
	}
}
