package emcshop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

		try (Connection conn = conn()) {
			DbDao dao = new DirbyEmbeddedDbDao(conn);
			dao.populateItemsTable();
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

		try (Connection conn = conn()) {
			DbDao dao = new DirbyEmbeddedDbDao(conn);
			dao.updateItemNamesAndAliases();
		}

		updateItemNamesCalled = true;
	}

	/**
	 * Re-calculates each player's first/last seen dates.
	 * @throws SQLException if there's a database problem
	 */
	public static void calculatePlayersFirstLastSeenDates() throws SQLException {
		try (Connection conn = conn()) {
			DbDao dao = new DirbyEmbeddedDbDao(conn);
			dao.calculatePlayersFirstLastSeenDates();
		}
	}

	/**
	 * Removes duplicate item names in the "items" table.
	 * @throws SQLException if there's a database problem
	 */
	public static void removeDuplicateItemNames() throws SQLException {
		try (Connection conn = conn()) {
			DbDao dao = new DirbyEmbeddedDbDao(conn);
			dao.removeDuplicateItems();
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
		try (Connection conn = conn()) {
			DbDao dao = new DirbyEmbeddedDbDao(conn);

			//find all the bad player names
			List<Integer> badPlayerIds = new ArrayList<>();
			List<Integer> goodPlayerIds = new ArrayList<>();
			List<String> reasons = new ArrayList<>();
			try (PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM players")) {
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
			Map<Integer, Integer> transactionIdToNewPlayerId = new HashMap<>();
			try (PreparedStatement stmt = conn.prepareStatement("SELECT \"transaction\" FROM payment_transactions WHERE player = ? AND \"transaction\" IS NOT NULL")) {
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
			try (PreparedStatement stmt = conn.prepareStatement("UPDATE payment_transactions SET player = ?, reason = ? WHERE player = ?")) {
				for (int i = 0; i < badPlayerIds.size(); i++) {
					Integer goodPlayerId = goodPlayerIds.get(i);
					String reason = reasons.get(i);
					Integer badPlayerId = badPlayerIds.get(i);

					stmt.setInt(1, goodPlayerId);
					stmt.setString(2, reason);
					stmt.setInt(3, badPlayerId);
					stmt.executeUpdate();
				}
			}

			//assign the correct playerId to each transaction
			try (PreparedStatement stmt = conn.prepareStatement("UPDATE transactions SET player = ? WHERE id = ? AND player IS NOT NULL")) {
				for (Entry<Integer, Integer> entry : transactionIdToNewPlayerId.entrySet()) {
					Integer transactionId = entry.getKey();
					Integer newPlayerId = entry.getValue();

					stmt.setInt(1, newPlayerId);
					stmt.setInt(2, transactionId);
					stmt.executeUpdate();
				}
			}
			try (PreparedStatement stmt = conn.prepareStatement("UPDATE transactions SET shop_owner = ? WHERE id = ? AND shop_owner IS NOT NULL")) {
				for (Entry<Integer, Integer> entry : transactionIdToNewPlayerId.entrySet()) {
					Integer transactionId = entry.getKey();
					Integer newPlayerId = entry.getValue();

					stmt.setInt(1, newPlayerId);
					stmt.setInt(2, transactionId);
					stmt.executeUpdate();
				}
			}

			//delete all of the bad player names
			try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM players WHERE id = ?")) {
				for (Integer badPlayerId : badPlayerIds) {
					stmt.setInt(1, badPlayerId);
					stmt.executeUpdate();
				}
			}
		}
	}

	/**
	 * Populates the "bonuses_fees.highest_balance" and
	 * "bonuses_fees.highest_balance_ts" database fields.
	 * @throws SQLException if there's a database problem
	 */
	public static void findHighestBalance() throws SQLException {
		try (Connection conn = conn()) {
			DbDao dao = new DirbyEmbeddedDbDao(conn);
			dao.findHighestBalance();
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

		ZoneId emcTime = ZoneId.of("America/New_York");
		LocalDateTime date = ZonedDateTime.of(2020, 3, 15, 12, 0, 0, 0, emcTime).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

		try (Connection conn = conn()) {
			DbDao dao = new DirbyEmbeddedDbDao(conn);
			dao.updateItemsWhoseOldNamesAreUsedByExistingItems(oldNames, newNames, date);
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
