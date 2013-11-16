package emcshop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import emcshop.PotionDirectory;

/**
 * Contains stored procedure code which is used to migrate the database to
 * various versions. The code is called from the migration SQL scripts and is
 * used when simple SQL statements aren't enough.
 * @author Michael Angstadt
 * @see "http://wiki.apache.org/db-derby/DerbySQLroutines"
 */
public class MigrationSprocs {
	/**
	 * Migrates a version 4 database to version 5.
	 * @throws SQLException
	 */
	public static void migrate4_5() throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		updatePotionNames(conn);

		conn.close();
	}

	/**
	 * Converts existing potion names (e.g. "Potion:8193") to human-readable
	 * names (e.g. "Potion of Regeneration").
	 * @param conn
	 * @throws SQLException
	 */
	private static void updatePotionNames(Connection conn) throws SQLException {
		PreparedStatement getItemId = conn.prepareStatement("SELECT id FROM items WHERE Lower(name) = Lower(?)");
		PreparedStatement updateTransactionsToUseNewName = conn.prepareStatement("UPDATE transactions SET item = ? WHERE item = ?");
		PreparedStatement deleteItem = conn.prepareStatement("DELETE FROM items WHERE id = ?");
		PreparedStatement insertItem = conn.prepareStatement("INSERT INTO items (name) VALUES (?)");
		PreparedStatement updateItemName = conn.prepareStatement("UPDATE items SET name = ? WHERE id = ?");

		PotionDirectory potions = PotionDirectory.create();
		for (Map.Entry<String, String> entry : potions.getAllNames().entrySet()) {
			String potionId = entry.getKey();
			String newPotionName = entry.getValue();
			String oldPotionName = "Potion:" + potionId;

			getItemId.setString(1, oldPotionName);
			ResultSet rs = getItemId.executeQuery();
			Integer oldNameId = rs.next() ? rs.getInt("id") : null;

			getItemId.setString(1, newPotionName);
			rs = getItemId.executeQuery();
			Integer newNameId = rs.next() ? rs.getInt("id") : null;

			if (oldNameId == null && newNameId == null) {
				insertItem.setString(1, newPotionName);
				insertItem.executeUpdate();
			} else if (oldNameId == null && newNameId != null) {
				//nothing needs to be changed
			} else if (oldNameId != null && newNameId == null) {
				updateItemName.setString(1, newPotionName);
				updateItemName.setInt(2, oldNameId);
				updateItemName.executeUpdate();
			} else if (oldNameId != null && newNameId != null) {
				//in the unlikely event that both the old and new names exist,
				//make all transactions that use the old name use the new one

				updateTransactionsToUseNewName.setInt(1, newNameId);
				updateTransactionsToUseNewName.setInt(2, oldNameId);
				updateTransactionsToUseNewName.executeUpdate();

				deleteItem.setInt(1, oldNameId);
				deleteItem.executeUpdate();
			}
		}
	}
}
