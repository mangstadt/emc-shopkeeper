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
	 * Updates the database schema version.
	 * @param version the new version number
	 * @throws SQLException
	 */
	void updateDbVersion(int version) throws SQLException;

	/**
	 * Sets the database schema version, inserting the necessary row in the
	 * table.
	 * @param version the version number
	 * @throws SQLException
	 */
	void insertDbVersion(int version) throws SQLException;

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
	 * Inserts a player.
	 * @param name the player name
	 * @return the ID of the inserted player
	 * @throws SQLException
	 */
	int insertPlayer(String name) throws SQLException;

	/**
	 * Gets the ID of an item, inserting the item if it doesn't exist.
	 * @param name the item name, case in-sensitive (e.g. "apple")
	 * @return the item ID
	 * @throws SQLException
	 */
	Integer getItemId(String name) throws SQLException;

	/**
	 * Gets the names of all items in the database sorted alphabetically.
	 * @return all item names
	 * @throws SQLException
	 */
	List<String> getItemNames() throws SQLException;

	/**
	 * Inserts an item.
	 * @param name the item name
	 * @return the ID of the inserted item
	 * @throws SQLException
	 */
	int insertItem(String name) throws SQLException;

	/**
	 * Inserts a transaction.
	 * @param transaction
	 * @param updateInventory true to update the inventory, false not to
	 * @throws SQLException
	 */
	void insertTransaction(ShopTransaction transaction, boolean updateInventory) throws SQLException;

	/**
	 * Inserts multiple transactions.
	 * @param transactions
	 * @param updateInventory true to update the inventory, false not to
	 * @throws SQLException
	 */
	void insertTransactions(Collection<ShopTransaction> transactions, boolean updateInventory) throws SQLException;

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
	 * Gets all transactions in the database.
	 * @return the transactions
	 * @throws SQLException
	 */
	List<ShopTransaction> getTransactions() throws SQLException;

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
	 * Gets the latest transaction from the database.
	 * @return the latest transaction or null if there are no transactions
	 * @throws SQLException
	 */
	ShopTransaction getLatestTransaction() throws SQLException;

	/**
	 * Gets the date of the latest transaction from the database.
	 * @return the date of the latest transaction or null if there are no
	 * transactions
	 * @throws SQLException
	 */
	Date getLatestTransactionDate() throws SQLException;

	/**
	 * Computes the net gains/losses for each item.
	 * @return the net gains/losses for each item
	 * @throws SQLException
	 */
	Map<String, ItemGroup> getItemGroups() throws SQLException;

	/**
	 * Computes the net gains/losses for each item over a date range.
	 * @param from the start date
	 * @param to the end date
	 * @return the net gains/losses for each item
	 * @throws SQLException
	 */
	Map<String, ItemGroup> getItemGroups(Date from, Date to) throws SQLException;

	/**
	 * Computes what each player bought/sold over a date range.
	 * @param from the start date
	 * @param to the end date
	 * @return the player activity
	 * @throws SQLException
	 */
	Map<String, PlayerGroup> getPlayerGroups(Date from, Date to) throws SQLException;

	/**
	 * Gets a player name.
	 * @param id the ID
	 * @return the player name or null if not found
	 * @throws SQLException
	 */
	String getPlayerName(int id) throws SQLException;

	/**
	 * Gets the player's shop inventory.
	 * @return the inventory
	 * @throws SQLException
	 */
	List<Inventory> getInventory() throws SQLException;

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
