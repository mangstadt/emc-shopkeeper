package emcshop.db;

import static emcshop.util.TimeUtils.toLocalDate;
import static emcshop.util.TimeUtils.toLocalDateTime;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.derby.jdbc.EmbeddedDriver;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class InsertStatementTest {
	private static Connection conn;

	@BeforeClass
	public static void beforeClass() throws Exception {
		conn = startMemoryDb();

		//@formatter:off
		execute(
		"CREATE TABLE test(" +
			"id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +
			"timestampCol TIMESTAMP," +
			"dateCol DATE," +
			"intCol INTEGER," +
			"stringCol VARCHAR(64)" +
		")"
		);
		//@formatter:on
	}

	@After
	public void after() throws Exception {
		execute("DELETE FROM test");
		execute("ALTER TABLE test ALTER COLUMN id RESTART WITH 1");
	}

	@AfterClass
	public static void afterClass() throws Exception {
		conn.close();
	}

	@Test(expected = IllegalStateException.class)
	public void toSql_zero_rows() {
		InsertStatement stmt = new InsertStatement("the_table");
		stmt.toSql();
	}

	@Test(expected = IllegalStateException.class)
	public void toSql_zero_rows_extra_nextRow_calls() {
		InsertStatement stmt = new InsertStatement("the_table");
		stmt.nextRow();
		stmt.nextRow();
		stmt.toSql();
	}

	@Test
	public void toSql_one_row() {
		InsertStatement stmt = new InsertStatement("the_table");
		stmt.setInt("col1", 5);
		stmt.setString("col2", "value");
		assertEquals("INSERT INTO the_table (col1, col2) VALUES (?, ?)", stmt.toSql());
	}

	@Test
	public void toSql_multiple_rows() {
		InsertStatement stmt = new InsertStatement("the_table");
		stmt.setInt("col1", 5);
		stmt.setString("col2", "value1");
		stmt.nextRow();
		stmt.setInt("col1", 10);
		stmt.setString("col2", "value2");
		stmt.nextRow();
		stmt.setInt("col1", 15);
		stmt.setString("col2", "value3");
		assertEquals("INSERT INTO the_table (col1, col2) VALUES (?, ?),\n(?, ?),\n(?, ?)", stmt.toSql());
	}

	@Test
	public void toSql_one_column() {
		InsertStatement stmt = new InsertStatement("the_table");
		stmt.setInt("col1", 5);
		assertEquals("INSERT INTO the_table (col1) VALUES (?)", stmt.toSql());
	}

	@Test
	public void toSql_multiple_columns() {
		InsertStatement stmt = new InsertStatement("the_table");
		stmt.setInt("col1", 5);
		stmt.setString("col2", "value");
		assertEquals("INSERT INTO the_table (col1, col2) VALUES (?, ?)", stmt.toSql());
	}

	@Test(expected = IllegalStateException.class)
	public void execute_zero_rows() throws Exception {
		InsertStatement stmt = new InsertStatement("the_table");
		stmt.execute(null);
	}

	@Test
	public void execute_one_row() throws Exception {
		InsertStatement stmt = new InsertStatement("test");

		stmt.setTimestamp("timestampCol", LocalDateTime.of(2013, 3, 7, 20, 33, 0));
		stmt.setDate("dateCol", LocalDate.of(2013, 3, 6));
		stmt.setInt("intCol", 5);
		stmt.setString("stringCol", "value1");
		assertEquals(Integer.valueOf(1), stmt.execute(conn));

		Statement statement = conn.createStatement();
		ResultSet rs = statement.executeQuery("SELECT * FROM test ORDER BY id");

		rs.next();
		assertEquals(1, rs.getInt("id"));
		assertEquals(LocalDateTime.of(2013, 3, 7, 20, 33, 0), toLocalDateTime(rs.getTimestamp("timestampCol")));
		assertEquals(LocalDate.of(2013, 3, 6), toLocalDate(rs.getDate("dateCol")));
		assertEquals(5, rs.getInt("intCol"));
		assertEquals("value1", rs.getString("stringCol"));

		assertFalse(rs.next());

		statement.close();
	}

	@Test
	public void execute_multiple_rows() throws Exception {
		InsertStatement stmt = new InsertStatement("test");

		//leaving out columns (same as using null values)
		stmt.setTimestamp("timestampCol", LocalDateTime.of(2013, 3, 4, 8, 33, 0));
		stmt.setInt("intCol", 10);
		stmt.nextRow();

		//null values
		stmt.setTimestamp("timestampCol", null);
		stmt.setDate("dateCol", LocalDate.of(2013, 3, 5));
		stmt.setInt("intCol", null);
		stmt.setString("stringCol", "value2");
		stmt.nextRow();

		//extra calls to "nextRow" should be ignored
		stmt.nextRow();

		//all have values
		stmt.setTimestamp("timestampCol", LocalDateTime.of(2013, 3, 7, 20, 33, 0));
		stmt.setDate("dateCol", LocalDate.of(2013, 3, 6));
		stmt.setInt("intCol", 5);
		stmt.setString("stringCol", "value1");
		stmt.nextRow();

		//all have null values
		stmt.setTimestamp("timestampCol", null);
		stmt.setDate("dateCol", null);
		stmt.setInt("intCol", null);
		stmt.setString("stringCol", null);

		//extra calls to "nextRow" should be ignored
		stmt.nextRow();
		stmt.nextRow();

		assertEquals(Integer.valueOf(1), stmt.execute(conn));

		Statement statement = conn.createStatement();
		ResultSet rs = statement.executeQuery("SELECT * FROM test ORDER BY id");
		int id = 1;

		rs.next();
		assertEquals(id++, rs.getInt("id"));
		assertEquals(LocalDateTime.of(2013, 3, 4, 8, 33, 0), toLocalDateTime(rs.getTimestamp("timestampCol")));
		assertNull(rs.getDate("dateCol"));
		assertEquals(10, rs.getInt("intCol"));
		assertNull(rs.getString("stringCol"));

		rs.next();
		assertEquals(id++, rs.getInt("id"));
		assertNull(rs.getTimestamp("timestampCol"));
		assertEquals(LocalDate.of(2013, 3, 5), toLocalDate(rs.getDate("dateCol")));
		assertNull(rs.getObject("intCol"));
		assertEquals("value2", rs.getString("stringCol"));

		rs.next();
		assertEquals(id++, rs.getInt("id"));
		assertEquals(LocalDateTime.of(2013, 3, 7, 20, 33, 0), toLocalDateTime(rs.getTimestamp("timestampCol")));
		assertEquals(LocalDate.of(2013, 3, 6), toLocalDate(rs.getDate("dateCol")));
		assertEquals(5, rs.getInt("intCol"));
		assertEquals("value1", rs.getString("stringCol"));

		rs.next();
		assertEquals(id++, rs.getInt("id"));
		assertNull(rs.getTimestamp("timestampCol"));
		assertNull(rs.getDate("dateCol"));
		assertNull(rs.getObject("intCol"));
		assertNull(rs.getString("stringCol"));

		assertFalse(rs.next());

		statement.close();
	}

	private static Connection startMemoryDb() throws Exception {
		Class.forName(EmbeddedDriver.class.getName()).getDeclaredConstructor().newInstance();

		String jdbcUrl = "jdbc:derby:memory:emc-shopkeeper;create=true";
		return DriverManager.getConnection(jdbcUrl);
	}

	private static void execute(String sql) throws SQLException {
		Statement stmt = conn.createStatement();
		try {
			stmt.execute(sql);
		} finally {
			stmt.close();
		}
	}
}