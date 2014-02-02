package emcshop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.populateItemsTable();
		} finally {
			conn.close();
		}
	}

	/**
	 * Updates the names of all items that have display names which differ from
	 * the names on the transaction page.
	 * @param conn
	 * @throws SQLException
	 */
	public static void updateItemNames() throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			int dbVersion = dao.selectDbVersion();

			//convert any old item names to the new names (e.g. "Potion:8193" to "Potion of Regeneration")
			ItemIndex itemIndex = ItemIndex.instance();
			for (Map.Entry<String, String> entry : itemIndex.getEmcNameToDisplayNameMapping().entrySet()) {
				String oldName = entry.getKey();
				String newName = entry.getValue();

				Integer oldNameId = dao.getItemId(oldName);
				Integer newNameId = dao.getItemId(newName);

				if (oldNameId == null && newNameId == null) {
					//nothing needs to be changed because neither name exists
				} else if (oldNameId == null && newNameId != null) {
					//nothing needs to be changed because the new name is already in use
				} else if (oldNameId != null && newNameId == null) {
					//the old name is still being used, so change it to the new name
					dao.updateItemName(oldNameId, newName);
				} else if (oldNameId != null && newNameId != null) {
					//both the old and new names exist
					//update all transactions that use the old name with the new name, and delete the old name

					dao.updateTransactionItem(oldNameId, newNameId);

					if (dbVersion >= 7) {
						dao.updateInventoryItem(oldNameId, newNameId);
					}

					dao.deleteItem(oldNameId);
				}
			}
		} finally {
			conn.close();
		}
	}

	/**
	 * Removes duplicate item names in the "items" table.
	 * @throws SQLException
	 */
	public static void removeDuplicateItemNames() throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		DbDao dao = new DirbyEmbeddedDbDao(conn);

		try {
			dao.removeDuplicateItems();
		} finally {
			conn.close();
		}
	}
}
