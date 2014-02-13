package emcshop.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.ShopTransaction;

public interface DbDao {
	/**
	 * Updates the database schema to the latest version if the schema is out of
	 * date.
	 * @param listener
	 * @throws SQLException
	 */
	void updateToLatestVersion(DbListener listener) throws SQLException;

	/**
	 * Gets the database schema version.
	 * @return the version number
	 * @throws SQLException
	 */
	int selectDbVersion() throws SQLException;

	/**
	 * Sets the database schema version.
	 * @param version the new version number
	 * @throws SQLException
	 */
	void upsertDbVersion(int version) throws SQLException;

	/**
	 * Searches for a player, inserting the player if it doesn't exist.
	 * @param name the player name, case in-sensitive (e.g. "notch")
	 * @return the player
	 * @throws SQLException
	 */
	Player selsertPlayer(String name) throws SQLException;

	/**
	 * Gets the date of the earliest transaction.
	 * @return the date
	 * @throws SQLException
	 */
	Date getEarliestTransactionDate() throws SQLException;

	/**
	 * Gets the ID of an item.
	 * @param name the item name, case in-sensitive (e.g. "apple")
	 * @return the item ID or null if not found
	 * @throws SQLException
	 */
	Integer getItemId(String name) throws SQLException;

	/**
	 * Gets the ID of an item, inserting the item if it doesn't exist.
	 * @param name the item name, case in-sensitive (e.g. "apple")
	 * @return the item ID
	 * @throws SQLException
	 */
	int upsertItem(String name) throws SQLException;

	/**
	 * Gets the names of all items in the database sorted alphabetically.
	 * @return all item names
	 * @throws SQLException
	 */
	List<String> getItemNames() throws SQLException;

	/**
	 * Changes the item that a transaction is associated with.
	 * @param oldItemIds the old item ID(s)
	 * @param newItemId the new item ID
	 * @throws SQLException
	 */
	void updateTransactionItem(List<Integer> oldItemIds, int newItemId) throws SQLException;

	/**
	 * Changes the name of an item.
	 * @param id the item ID
	 * @param newName the new name
	 * @throws SQLException
	 */
	void updateItemName(Integer id, String newName) throws SQLException;

	/**
	 * Deletes one or more items.
	 * @param id the item ID(s)
	 * @throws SQLException
	 */
	void deleteItems(Integer... ids) throws SQLException;

	/**
	 * Seeds the items table with all known items;
	 * @throws SQLException
	 */
	void populateItemsTable() throws SQLException;

	/**
	 * Removes items that have identical names, ensuring that a single item ID
	 * is used for each item throughout the database.
	 * @throws SQLException
	 */
	void removeDuplicateItems() throws SQLException;

	/**
	 * Inserts a transaction.
	 * @param transaction
	 * @param updateInventory true to update the inventory, false not to
	 * @throws SQLException
	 */
	void insertTransaction(ShopTransaction transaction, boolean updateInventory) throws SQLException;

	/**
	 * Inserts multiple payment transactions.
	 * @param transactions
	 * @throws SQLException
	 */
	void insertPaymentTransactions(Collection<PaymentTransaction> transactions) throws SQLException;

	/**
	 * Updates or inserts a payment transaction.
	 * @param transaction the payment transaction
	 * @throws SQLException
	 */
	void upsertPaymentTransaction(PaymentTransaction transaction) throws SQLException;

	/**
	 * Gets all unhandled payment transactions from the database.
	 * @return the transactions
	 * @throws SQLException
	 */
	List<PaymentTransaction> getPendingPaymentTransactions() throws SQLException;

	/**
	 * Ignores a pending payment transaction.
	 * @param id the payment transaction ID
	 * @throws SQLException
	 */
	void ignorePaymentTransaction(Integer id) throws SQLException;

	/**
	 * Assigns a pending payment transaction to a shop transaction.
	 * @param paymentId the payment transaction ID
	 * @param transactionId the shop transaction ID
	 * @throws SQLException
	 */
	void assignPaymentTransaction(Integer paymentId, Integer transactionId) throws SQLException;

	/**
	 * Counts the number of pending payment transactions.
	 * @return the count
	 * @throws SQLException
	 */
	int countPendingPaymentTransactions() throws SQLException;

	/**
	 * Gets the date of the latest transaction from the database.
	 * @return the date of the latest transaction or null if there are no
	 * transactions
	 * @throws SQLException
	 */
	Date getLatestTransactionDate() throws SQLException;

	/**
	 * Computes the net gains/losses for each item over a date range.
	 * @param from the start date or null to start at the first transaction
	 * @param to the end date or null to end at the last transaction
	 * @return the net gains/losses for each item
	 * @throws SQLException
	 */
	Map<String, ItemGroup> getItemGroups(Date from, Date to) throws SQLException;

	/**
	 * Gets all transactions by date, consolidating them so consecutive
	 * purchases are combined into a single transaction.
	 * @param from the start date
	 * @param to the end date
	 * @return the transactions
	 * @throws SQLException
	 */
	List<ShopTransaction> getTransactionsByDate(Date from, Date to) throws SQLException;

	/**
	 * Computes what each player bought/sold over a date range.
	 * @param from the start date
	 * @param to the end date
	 * @return the player activity
	 * @throws SQLException
	 */
	Map<String, PlayerGroup> getPlayerGroups(Date from, Date to) throws SQLException;

	/**
	 * Gets the player's shop inventory.
	 * @return the inventory
	 * @throws SQLException
	 */
	List<Inventory> getInventory() throws SQLException;

	/**
	 * Changes the item that an inventory entry is associated with.
	 * @param oldItemIds the old item ID(s)
	 * @param newItemId the new item ID
	 * @throws SQLException
	 */
	void updateInventoryItem(List<Integer> oldItemIds, int newItemId) throws SQLException;

	/**
	 * Updates or inserts an inventory item.
	 * @param inventory the inventory item
	 * @throws SQLException
	 */
	void upsertInventory(Inventory inventory) throws SQLException;

	/**
	 * Updates or inserts an inventory item.
	 * @param item the item name
	 * @param quantity the new quantity
	 * @param add true to add the given quantity to the existing total, false to
	 * overwrite it
	 * @throws SQLException
	 */
	void upsertInventory(String item, Integer quantity, boolean add) throws SQLException;

	/**
	 * Deletes one or more inventory items.
	 * @param ids the inventory IDs to delete
	 * @throws SQLException
	 */
	void deleteInventory(Collection<Integer> ids) throws SQLException;

	/**
	 * Updates the bonus/fee totals.
	 * @param transactions the bonus/fee transactions
	 * @throws SQLException
	 */
	void updateBonusesFees(List<BonusFeeTransaction> transactions) throws SQLException;

	/**
	 * Gets the bonus/fee totals.
	 * @return the totals
	 * @throws SQLException
	 */
	BonusFee getBonusesFees() throws SQLException;

	/**
	 * Updates the date that the bonus/fee tally began (only if the date has not
	 * been set yet).
	 * @param since the date
	 * @throws SQLException
	 */
	void updateBonusesFeesSince(Date since) throws SQLException;

	/**
	 * Tallies up the profits by day.
	 * @param from the start date
	 * @param to the end date
	 * @return the profits
	 * @throws SQLException
	 */
	Map<Date, Profits> getProfitsByDay(Date from, Date to) throws SQLException;

	/**
	 * Tallies up the profits by month.
	 * @param from the start date
	 * @param to the end date
	 * @return the profits
	 * @throws SQLException
	 */
	Map<Date, Profits> getProfitsByMonth(Date from, Date to) throws SQLException;

	/**
	 * Re-calculates the "first_seen" and "last_seen" dates of all players.
	 * @throws SQLException
	 */
	void calculatePlayersFirstLastSeenDates() throws SQLException;

	/**
	 * Deletes all data in the database.
	 * @throws IOException
	 * @throws SQLException
	 */
	void wipe() throws IOException, SQLException;

	/**
	 * Commits the current database transaction.
	 * @throws SQLException
	 */
	void commit() throws SQLException;

	/**
	 * Rollsback the current database transaction.
	 */
	void rollback();

	/**
	 * Closes the database connection.
	 * @throws SQLException
	 */
	void close() throws SQLException;
}
