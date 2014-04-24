package emcshop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Contains stored procedure code for calling from the SQL scripts.
 * @author Michael Angstadt
 * @see "http://wiki.apache.org/db-derby/DerbySQLroutines"
 */
public class MigrationSprocs {
	//these sprocs may be called multiple times over the course of a database update
	//however, they only need to be called once
	private static boolean populateItemsTableCalled = false;
	private static boolean updateItemNamesCalled = false;

	/**
	 * Ensures that the "items" table contains the names of all items. This
	 * method is meant to be called as a stored procedure.
	 * @throws SQLException if there's a database problem
	 */
	public static void populateItemsTable() throws SQLException {
		if (populateItemsTableCalled) {
			return;
		}

		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.populateItemsTable();
		} finally {
			conn.close();
		}

		populateItemsTableCalled = true;
	}

	/**
	 * Updates the names of all items that have display names which differ from
	 * the names on the transaction page. For example, converts "Potion:8193" to
	 * "Potion of Regeneration".
	 * @throws SQLException if there's a database problem
	 */
	public static void updateItemNames() throws SQLException {
		if (updateItemNamesCalled) {
			return;
		}

		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.updateItemNamesAndAliases();
		} finally {
			conn.close();
		}

		updateItemNamesCalled = true;
	}

	/**
	 * Re-calculates each player's first/last seen dates.
	 * @throws SQLException if there's a database problem
	 */
	public static void calculatePlayersFirstLastSeenDates() throws SQLException {
		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.calculatePlayersFirstLastSeenDates();
		} finally {
			conn.close();
		}
	}

	/**
	 * Removes duplicate item names in the "items" table.
	 * @throws SQLException if there's a database problem
	 */
	public static void removeDuplicateItemNames() throws SQLException {
		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.removeDuplicateItems();
		} finally {
			conn.close();
		}
	}

	/**
	 * Creates a connection to the database.
	 * @return the database connection
	 * @throws SQLException if there's a database problem
	 */
	private static Connection conn() throws SQLException {
		return DriverManager.getConnection("jdbc:default:connection");
	}

	private MigrationSprocs() {
		//hide
	}
}
