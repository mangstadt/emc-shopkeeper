package emcshop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Map.Entry;

/**
 * Contains stored procedure code for calling from the SQL scripts.
 * @author Michael Angstadt
 * @see "http://wiki.apache.org/db-derby/DerbySQLroutines"
 */
public final class MigrationSprocs {
	//these sprocs may be called multiple times over the course of a database update
	//however, they only need to be called once
	static boolean populateItemsTableCalled = false;
	static boolean updateItemNamesCalled = false;

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
	 * <p>
	 * On 4/25/2015, a new feature was added that allows you to assign a
	 * "reason" to a payment transaction when making one in-game. This effects
	 * the transaction's description in the rupee history page. Because of the
	 * way EMC Shopkeeper was parsing those transactions, EMC Shopkeeper would
	 * end up treating the player name *and* reason string as the entire player
	 * name, leading to bad data being saved to the database.
	 * </p>
	 * <p>
	 * This stored procedure corrects those errors.
	 * </p>
	 * @throws SQLException
	 */
	public static void fixPaymentTransactionReason() throws SQLException {
		Connection conn = conn();

		try {
			DbDao dao = new DirbyEmbeddedDbDao(conn);

			//find all the bad player names
			List<Integer> badPlayerIds = new ArrayList<Integer>();
			List<Integer> goodPlayerIds = new ArrayList<Integer>();
			List<String> reasons = new ArrayList<String>();
			{
				PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM players");
				ResultSet rs = stmt.executeQuery();
				while (rs.next()) {
					String name = rs.getString("name");
					int colonPos = name.indexOf(':');
					if (colonPos < 0) {
						continue;
					}

					Integer id = rs.getInt("id");
					badPlayerIds.add(id);

					String playerName = name.substring(0, colonPos);
					Player goodPlayer = dao.selsertPlayer(playerName);
					goodPlayerIds.add(goodPlayer.getId());

					String reason;
					if (colonPos == name.length() - 1) {
						reason = null;
					} else {
						reason = name.substring(colonPos + 1).trim();
						if (reason.isEmpty()) {
							reason = null;
						}
					}
					reasons.add(reason);
				}
			}

			if (badPlayerIds.isEmpty()) {
				//no bad data was saved to the database
				return;
			}

			//find all transactions that are affected and determine their proper playerId
			Map<Integer, Integer> transactionIdToNewPlayerId = new HashMap<Integer, Integer>();
			{
				PreparedStatement stmt = conn.prepareStatement("SELECT \"transaction\" FROM payment_transactions WHERE player = ? AND \"transaction\" IS NOT NULL");
				for (int i = 0; i < badPlayerIds.size(); i++) {
					Integer badPlayerId = badPlayerIds.get(i);
					Integer goodPlayerId = goodPlayerIds.get(i);

					stmt.setInt(1, badPlayerId);
					ResultSet rs = stmt.executeQuery();
					while (rs.next()) {
						Integer transactionId = rs.getInt(1);
						transactionIdToNewPlayerId.put(transactionId, goodPlayerId);
					}
				}
			}

			//assign the correct playerId and reason to each payment_transaction
			PreparedStatement stmt = conn.prepareStatement("UPDATE payment_transactions SET player = ?, reason = ? WHERE player = ?");
			for (int i = 0; i < badPlayerIds.size(); i++) {
				Integer goodPlayerId = goodPlayerIds.get(i);
				String reason = reasons.get(i);
				Integer badPlayerId = badPlayerIds.get(i);

				stmt.setInt(1, goodPlayerId);

				//if (reason == null) {
				//stmt.setNull(2, Types.VARCHAR);
				//} else {
				stmt.setString(2, reason);
				//}
				stmt.setInt(3, badPlayerId);
				stmt.executeUpdate();
			}

			//assign the correct playerId to each transaction
			stmt = conn.prepareStatement("UPDATE transactions SET player = ? WHERE id = ? AND player IS NOT NULL");
			for (Entry<Integer, Integer> entry : transactionIdToNewPlayerId.entrySet()) {
				Integer transactionId = entry.getKey();
				Integer newPlayerId = entry.getValue();

				stmt.setInt(1, newPlayerId);
				stmt.setInt(2, transactionId);
				stmt.executeUpdate();
			}
			stmt = conn.prepareStatement("UPDATE transactions SET shop_owner = ? WHERE id = ? AND shop_owner IS NOT NULL");
			for (Entry<Integer, Integer> entry : transactionIdToNewPlayerId.entrySet()) {
				Integer transactionId = entry.getKey();
				Integer newPlayerId = entry.getValue();

				stmt.setInt(1, newPlayerId);
				stmt.setInt(2, transactionId);
				stmt.executeUpdate();
			}

			//delete all of the bad player names
			stmt = conn.prepareStatement("DELETE FROM players WHERE id = ?");
			for (Integer badPlayerId : badPlayerIds) {
				stmt.setInt(1, badPlayerId);
				stmt.executeUpdate();
			}
		} finally {
			conn.close();
		}
	}

	/**
	 * Populates the "bonuses_fees.highest_balance" and
	 * "bonuses_fees.highest_balance_ts" database fields.
	 * @throws SQLException if there's a database problem
	 */
	public static void findHighestBalance() throws SQLException {
		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.findHighestBalance();
		} finally {
			conn.close();
		}
	}

	/**
	 * When EMC updated to 1.15, some item names changed such that the old names
	 * were being used by new items. For example, "Smooth Sandstone" changed to
	 * "Cut Sandstone", and a new block was added called "Smooth Sandstone".
	 * This method updates all existing transactions so that only transactions
	 * before the server transition are updated with the new name.
	 * @throws SQLException if there's a database problem
	 * @see "https://empireminecraft.com/threads/empire-update-to-1-15-the-aquatic-buzzy-village-update.81962/"
	 */
	public static void updateItemsWhoseOldNamesAreUsedByExistingItemsIn1_15() throws SQLException {
		List<String> oldNames = Arrays.asList("Smooth Sandstone", "Smooth Red Sandstone", "Stone Slab");
		List<String> newNames = Arrays.asList("Cut Sandstone", "Cut Red Sandstone", "Smooth Stone Slab");
		Date date;
		{
			TimeZone emcTime = TimeZone.getTimeZone("America/New_York");
			Calendar c = Calendar.getInstance(emcTime);
			c.set(Calendar.YEAR, 2020);
			c.set(Calendar.MONTH, Calendar.MARCH);
			c.set(Calendar.DATE, 15);
			c.set(Calendar.HOUR_OF_DAY, 13);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			date = c.getTime();
		}

		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.updateItemsWhoseOldNamesAreUsedByExistingItems(oldNames, newNames, date);
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
