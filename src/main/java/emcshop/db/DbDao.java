package emcshop.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;

import com.github.mangstadt.emc.rupees.dto.RupeeTransaction;

public interface DbDao {
	/**
	 * Re-creates the database connection after {@link #close} was called.
	 * @throws SQLException
	 */
	void reconnect() throws SQLException;

	/**
	 * Updates the database schema to the latest version if the schema is out of
	 * date.
	 * @param listener
	 * @throws SQLException
	 */
	void updateToLatestVersion(DbListener listener) throws SQLException;

	/**
	 * Gets the database version that this DAO is compatible with (as opposed to
	 * {@link #selectDbVersion()}, which retrieves the version of the database
	 * itself).
	 * @return the DAO's database version
	 */
	int getAppDbVersion();

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
	 * Gets the player's rupee balance.
	 * @return the rupee balance
	 * @throws SQLException
	 */
	Integer selectRupeeBalance() throws SQLException;

	/**
	 * Gets the player's rupee balance from its old location in the "meta"
	 * table.
	 * @return the rupee balance
	 * @throws SQLException
	 */
	int selectRupeeBalanceMeta() throws SQLException;

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
	int selsertItem(String name) throws SQLException;

	/**
	 * Gets the names of all items in the database sorted alphabetically.
	 * @return all item names
	 * @throws SQLException
	 */
	List<String> getItemNames() throws SQLException;

	/**
	 * Syncs the list of item names and their alias with the database.
	 * @throws SQLException
	 */
	void updateItemNamesAndAliases() throws SQLException;

	/**
	 * Seeds the items table with all known items. Only item names that don't
	 * exist in the table are inserted.
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
	void insertTransaction(ShopTransactionDb transaction, boolean updateInventory) throws SQLException;

	/**
	 * Inserts a payment transaction.
	 * @param transaction the transaction
	 * @throws SQLException
	 */
	void insertPaymentTransaction(PaymentTransactionDb transaction) throws SQLException;

	/**
	 * Deletes a payment transaction.
	 * @param transaction the transaction to delete
	 * @throws SQLException
	 */
	void deletePaymentTransaction(PaymentTransactionDb transaction) throws SQLException;

	/**
	 * Updates or inserts a payment transaction.
	 * @param transaction the payment transaction
	 * @throws SQLException
	 */
	void upsertPaymentTransaction(PaymentTransactionDb transaction) throws SQLException;

	/**
	 * Gets all unhandled payment transactions from the database.
	 * @return the transactions
	 * @throws SQLException
	 */
	List<PaymentTransactionDb> getPendingPaymentTransactions() throws SQLException;

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
	 * Gets the date of the latest parsed transaction (includes shop, payment,
	 * and bonus/fee).
	 * @return the date of the latest transaction or null if there are no
	 * transactions
	 * @throws SQLException
	 */
	Date getLatestTransactionDate() throws SQLException;

	/**
	 * Computes the net gains/losses for each item over a date range.
	 * @param from the start date or null to start at the first transaction
	 * @param to the end date or null to end at the last transaction
	 * @param transactionType the kind of shop transactions to query for
	 * @return the net gains/losses for each item
	 * @throws SQLException
	 */
	Collection<ItemGroup> getItemGroups(Date from, Date to, ShopTransactionType transactionType) throws SQLException;

	/**
	 * Gets all transactions by date, consolidating them so consecutive
	 * purchases are combined into a single transaction.
	 * @param from the start date
	 * @param to the end date
	 * @param transactionType the kind of shop transactions to query for
	 * @return the transactions
	 * @throws SQLException
	 */
	List<ShopTransactionDb> getTransactionsByDate(Date from, Date to, ShopTransactionType transactionType) throws SQLException;

	/**
	 * Computes what each player bought/sold over a date range.
	 * @param from the start date
	 * @param to the end date
	 * @param transactionType the kind of shop transactions to query for
	 * @return the player activity
	 * @throws SQLException
	 */
	Collection<PlayerGroup> getPlayerGroups(Date from, Date to, ShopTransactionType transactionType) throws SQLException;

	/**
	 * Gets the player's shop inventory.
	 * @return the inventory
	 * @throws SQLException
	 */
	Collection<Inventory> getInventory() throws SQLException;

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
	 * @return the ID of the updated record
	 * @throws SQLException
	 */
	int upsertInventory(String item, Integer quantity, boolean add) throws SQLException;

	/**
	 * Updates the "low in stock" threshold for an inventory item.
	 * @param item the item name
	 * @param threshold the new threshold
	 * @throws SQLException
	 */
	void updateInventoryLowThreshold(String item, int threshold) throws SQLException;

	/**
	 * Deletes one or more inventory items.
	 * @param ids the inventory IDs to delete
	 * @throws SQLException
	 */
	void deleteInventory(Collection<Integer> ids) throws SQLException;

	/**
	 * Updates the bonus/fee totals.
	 * @param totals the totals
	 * @throws SQLException
	 */
	void updateBonusFeeTotals(Map<Class<? extends RupeeTransaction>, MutableInt> totals) throws SQLException;

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
	 * Updates the date of the latest bonus/fee transaction.
	 * @param latestParsedBonusFeeDate the date
	 * @throws SQLException
	 */
	void updateBonusesFeesLatestTransactionDate(Date latestParsedBonusFeeDate) throws SQLException;

	/**
	 * Updates the player's highest rupee balance after an update operation.
	 * @param transaction the transaction with the highest rupee balance from
	 * the update operation that was just run (this may or may not be higher
	 * than the rupee balance in the bonus_fees table)
	 * @throws SQLException
	 */
	void updateBonusesFeesHighestBalance(RupeeTransaction transaction) throws SQLException;

	/**
	 * Determines if this DAO considers the given rupee transaction to be a
	 * "bonus/fee" transaction.
	 * @param transaction the rupee transaction
	 * @return true if it's a "bonus/fee" transaction, false if not
	 */
	boolean isBonusFeeTransaction(RupeeTransaction transaction);

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
	 * Finds the highest rupee balance the player has ever had, and saves that
	 * information to the bonuses_fees table.
	 * @throws SQLException
	 */
	void findHighestBalance() throws SQLException;

	/**
	 * Logs an update operation.
	 * @param ts the time the update started
	 * @param rupeeBalance the player's rupee balance
	 * @param transactionCount the number of transactions that were parsed
	 * @param paymentTransactionCount the number of payment transactions that
	 * were parsed
	 * @param bonusFeeTransactionCount the number of bonus fee transactions that
	 * were parsed
	 * @param timeTaken the time the update took in milliseconds
	 * @throws SQLException
	 */
	void insertUpdateLog(Date ts, Integer rupeeBalance, int transactionCount, int paymentTransactionCount, int bonusFeeTransactionCount, long timeTaken) throws SQLException;

	/**
	 * Gets the timestamp of the most recent update.
	 * @return the timestamp or null if the update log is empty
	 * @throws SQLException
	 */
	Date getLatestUpdateDate() throws SQLException;

	/**
	 * Gets the timestamp of the second most recent update.
	 * @return the timestamp or null if the update log is empty
	 * @throws SQLException
	 */
	Date getSecondLatestUpdateDate() throws SQLException;

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
