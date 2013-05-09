package emcshop.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import emcshop.PaymentTransaction;
import emcshop.ShopTransaction;

public interface DbDao {
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
	 * Inserts an item.
	 * @param name the item name
	 * @return the ID of the inserted item
	 * @throws SQLException
	 */
	int insertItem(String name) throws SQLException;

	/**
	 * Inserts a transaction.
	 * @param transaction
	 * @throws SQLException
	 */
	void insertTransaction(ShopTransaction transaction) throws SQLException;

	/**
	 * Inserts multiple transactions.
	 * @param transactions
	 * @throws SQLException
	 */
	void insertTransactions(Collection<ShopTransaction> transactions) throws SQLException;

	/**
	 * Inserts multiple payment transactions.
	 * @param transactions
	 * @throws SQLException
	 */
	void insertPaymentTransactions(Collection<PaymentTransaction> transactions) throws SQLException;

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
	List<PaymentTransaction> getUnhandledPaymentTransactions() throws SQLException;

	/**
	 * Gets the latest transaction from the database.
	 * @return the latest transaction or null if there are no transactions
	 * @throws SQLException
	 */
	ShopTransaction getLatestTransaction() throws SQLException;

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
