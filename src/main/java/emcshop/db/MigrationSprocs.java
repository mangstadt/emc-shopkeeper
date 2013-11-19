package emcshop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import emcshop.ItemIndex;

/**
 * Contains stored procedure code for calling from the SQL scripts.
 * @author Michael Angstadt
 * @see "http://wiki.apache.org/db-derby/DerbySQLroutines"
 */
public class MigrationSprocs {
	/**
	 * Ensures that the "items" table contains the names of all items.
	 * @throws SQLException
	 */
	public static void populateItemsTable() throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		try {
			//get all existing item names
			Set<String> existingItemNames = new HashSet<String>();
			PreparedStatement getItemNames = conn.prepareStatement("SELECT name FROM items");
			ResultSet rs = getItemNames.executeQuery();
			while (rs.next()) {
				existingItemNames.add(rs.getString(1).toLowerCase());
			}

			//insert all items names that aren't in the database
			InsertStatement insertItems = null;
			ItemIndex itemIndex = ItemIndex.instance();
			for (String itemName : itemIndex.getItemNames()) {
				if (existingItemNames.contains(itemName.toLowerCase())) {
					continue;
				}

				if (insertItems == null) {
					insertItems = new InsertStatement("items");
				} else {
					insertItems.nextRow();
				}
				insertItems.setString("name", itemName);
			}
			if (insertItems != null) {
				insertItems.execute(conn);
			}
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

		try {
			PreparedStatement getItemId = conn.prepareStatement("SELECT id FROM items WHERE Lower(name) = Lower(?)");
			PreparedStatement updateTransactionsWithNewName = conn.prepareStatement("UPDATE transactions SET item = ? WHERE item = ?");
			PreparedStatement deleteItem = conn.prepareStatement("DELETE FROM items WHERE id = ?");
			PreparedStatement updateItemName = conn.prepareStatement("UPDATE items SET name = ? WHERE id = ?");

			//convert any old item names to the new names (e.g. "Potion:8193" to "Potion of Regeneration")
			ItemIndex itemIndex = ItemIndex.instance();
			for (Map.Entry<String, String> entry : itemIndex.getEmcNameToDisplayNameMapping().entrySet()) {
				String oldName = entry.getKey();
				String newName = entry.getValue();

				getItemId.setString(1, oldName);
				ResultSet rs = getItemId.executeQuery();
				Integer oldNameId = rs.next() ? rs.getInt("id") : null;

				getItemId.setString(1, newName);
				rs = getItemId.executeQuery();
				Integer newNameId = rs.next() ? rs.getInt("id") : null;

				if (oldNameId == null && newNameId == null) {
					//nothing needs to be changed because neither name exists
				} else if (oldNameId == null && newNameId != null) {
					//nothing needs to be changed because the new name is already in use
				} else if (oldNameId != null && newNameId == null) {
					//the old name is still being used, so change it to the new name
					updateItemName.setString(1, newName);
					updateItemName.setInt(2, oldNameId);
					updateItemName.executeUpdate();
				} else if (oldNameId != null && newNameId != null) {
					//both the old and new names exist
					//update all transactions that use the old name with the new name, and delete the old name

					updateTransactionsWithNewName.setInt(1, newNameId);
					updateTransactionsWithNewName.setInt(2, oldNameId);
					updateTransactionsWithNewName.executeUpdate();

					deleteItem.setInt(1, oldNameId);
					deleteItem.executeUpdate();
				}
			}
		} finally {
			conn.close();
		}
	}
}
