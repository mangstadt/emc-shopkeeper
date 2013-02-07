package emcshop.db;

import java.sql.SQLException;
import java.util.List;

import emcshop.Transaction;

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
	 * Gets the ID of a player, inserting the player if it doesn't exist.
	 * @param name the player name, case in-sensitive (e.g. "notch")
	 * @return the player ID
	 * @throws SQLException
	 */
	Integer getPlayerId(String name) throws SQLException;

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
	void insertTransaction(Transaction transaction) throws SQLException;

	/**
	 * Gets all transactions in the database.
	 * @return the transactions
	 * @throws SQLException
	 */
	List<Transaction> getTransactions() throws SQLException;

	/**
	 * Gets the latest transaction from the database.
	 * @return the latest transaction or null if there are no transactions
	 * @throws SQLException
	 */
	Transaction getLatestTransaction() throws SQLException;

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