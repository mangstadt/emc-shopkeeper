package emcshop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emcshop.ItemIndex;

/**
 * Contains stored procedure code for calling from the SQL scripts.
 * @author Michael Angstadt
 * @see "http://wiki.apache.org/db-derby/DerbySQLroutines"
 */
public class MigrationSprocs {
	/**
	 * Ensures that the "items" table contains the names of all items. This
	 * method is meant to be called as a stored procedure.
	 * @throws SQLException
	 */
	public static void populateItemsTable() throws SQLException {
		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.populateItemsTable();
		} finally {
			conn.close();
		}
	}

	/**
	 * Updates the names of all items that have display names which differ from
	 * the names on the transaction page. For example, converts "Potion:8193" to
	 * "Potion of Regeneration".
	 * @param conn
	 * @throws SQLException
	 */
	public static void updateItemNames() throws SQLException {
		Connection conn = conn();
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			//map each display name to its list of EMC aliases
			ItemIndex itemIndex = ItemIndex.instance();
			Map<String, List<String>> displayNameToEmcNames = new HashMap<String, List<String>>();
			for (Map.Entry<String, String> entry : itemIndex.getEmcNameToDisplayNameMapping().entrySet()) {
				String emcName = entry.getKey();
				String displayName = entry.getValue();

				List<String> emcNames = displayNameToEmcNames.get(displayName);
				if (emcNames == null) {
					emcNames = new ArrayList<String>();
					displayNameToEmcNames.put(displayName, emcNames);
				}
				emcNames.add(emcName);
			}

			int dbVersion = dao.selectDbVersion();
			for (Map.Entry<String, List<String>> entry : displayNameToEmcNames.entrySet()) {
				String newName = entry.getKey();
				Integer newNameId = dao.getItemId(newName);

				List<String> oldNames = entry.getValue();
				List<Integer> oldNameIds = new ArrayList<Integer>(oldNames.size());
				for (String oldName : oldNames) {
					Integer oldNameId = dao.getItemId(oldName);
					if (oldNameId != null) {
						oldNameIds.add(oldNameId);
					}
				}

				if (oldNameIds.isEmpty() && newNameId == null) {
					//nothing needs to be changed because neither name exists
				} else if (oldNameIds.isEmpty() && newNameId != null) {
					//nothing needs to be changed because the new name is already in use
				} else if (!oldNameIds.isEmpty() && newNameId == null) {
					//the old name is still being used, so change it to the new name
					newNameId = oldNameIds.get(0);
					oldNameIds = oldNameIds.subList(1, oldNameIds.size());

					//change the name in the "items" table
					dao.updateItemName(newNameId, newName);

					if (!oldNameIds.isEmpty()) {
						if (dbVersion >= 7) {
							dao.updateInventoryItem(oldNameIds, newNameId);
						}
						dao.deleteItems(oldNameIds.toArray(new Integer[0]));
					}
				} else if (!oldNameIds.isEmpty() && newNameId != null) {
					//both the old and new names exist
					//update all transactions that use the old name with the new name, and delete the old name

					dao.updateTransactionItem(oldNameIds, newNameId);

					if (dbVersion >= 7) {
						dao.updateInventoryItem(oldNameIds, newNameId);
					}

					dao.deleteItems(oldNameIds.toArray(new Integer[0]));
				}
			}
		} finally {
			conn.close();
		}
	}

	/**
	 * Re-calculates each player's first/last seen dates.
	 * @throws SQLException
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
	 * @throws SQLException
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
	 * @throws SQLException
	 */
	private static Connection conn() throws SQLException {
		return DriverManager.getConnection("jdbc:default:connection");
	}

	private MigrationSprocs() {
		//hide
	}
}
