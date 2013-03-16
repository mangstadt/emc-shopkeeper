package emcshop.db;

import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.derby.jdbc.EmbeddedDriver;

import emcshop.ShopTransaction;
import emcshop.util.ClasspathUtils;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public abstract class DirbyDbDao implements DbDao {
	private static final Logger logger = Logger.getLogger(DirbyDbDao.class.getName());

	/**
	 * The current version of the database schema.
	 */
	private static final int schemaVersion = 1;

	/**
	 * The database connection.
	 */
	protected Connection conn;

	/**
	 * Connects to the database and creates the database from scratch if it
	 * doesn't exist.
	 * @param jdbcUrl the JDBC URL
	 * @param create true to create the database schema, false not to
	 * @param listener
	 * @throws SQLException
	 */
	protected void init(String jdbcUrl, boolean create, DbListener listener) throws SQLException {
		logger.info("Starting database...");

		//shutdown Derby when the program terminates
		//if the Dirby database is not shutdown, then changes to it will be lost
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				logger.info("Shutting down the database...");
				try {
					close();
				} catch (SQLException e) {
					logger.log(Level.SEVERE, "Error stopping database.", e);
				}
			}
		});

		//load the driver
		try {
			Class.forName(EmbeddedDriver.class.getName());
		} catch (ClassNotFoundException e) {
			throw new SQLException("Database driver not on classpath.", e);
		}

		//create the connection
		if (create) {
			jdbcUrl += ";create=true";
		}
		conn = DriverManager.getConnection(jdbcUrl);
		conn.setAutoCommit(false); // default is true

		//create tables if database doesn't exist
		if (create) {
			if (listener != null) {
				listener.onCreate();
			}
			logger.info("Database not found.  Creating the database...");
			String sql = null;
			SQLStatementReader in = null;
			String schemaFileName = "schema.sql";
			Statement statement = null;
			try {
				in = new SQLStatementReader(new InputStreamReader(ClasspathUtils.getResourceAsStream(schemaFileName, getClass())));
				statement = conn.createStatement();
				while ((sql = in.readStatement()) != null) {
					statement.execute(sql);
				}
				sql = null;
				insertDbVersion(schemaVersion);
			} catch (IOException e) {
				throw new SQLException("Error creating database.", e);
			} catch (SQLException e) {
				if (sql == null) {
					throw e;
				}
				throw new SQLException("Error executing SQL statement: " + sql, e);
			} finally {
				IOUtils.closeQuietly(in);
				if (statement != null) {
					try {
						statement.close();
					} catch (SQLException e) {
						//ignore
					}
				}
			}
			commit();
		} else {
			//update the database schema if it's not up to date
			int version = selectDbVersion();
			if (version < schemaVersion) {
				if (listener != null) {
					listener.onMigrate(version, schemaVersion);
				}

				logger.info("Database schema out of date.  Upgrading from version " + version + " to " + schemaVersion + ".");
				String sql = null;
				Statement statement = null;
				try {
					statement = conn.createStatement();
					while (version < schemaVersion) {
						logger.info("Performing schema update from version " + version + " to " + (version + 1) + ".");

						String script = "migrate-" + version + "-" + (version + 1) + ".sql";
						SQLStatementReader in = null;
						try {
							in = new SQLStatementReader(new InputStreamReader(ClasspathUtils.getResourceAsStream(script, getClass())));
							while ((sql = in.readStatement()) != null) {
								statement.execute(sql);
							}
							sql = null;
						} finally {
							IOUtils.closeQuietly(in);
						}

						version++;
					}
					updateDbVersion(schemaVersion);
				} catch (IOException e) {
					rollback();
					throw new SQLException("Error updating database schema.", e);
				} catch (SQLException e) {
					rollback();
					if (sql == null) {
						throw e;
					}
					throw new SQLException("Error executing SQL statement during schema update: " + sql, e);
				} finally {
					if (statement != null) {
						try {
							statement.close();
						} catch (SQLException e) {
							//ignore
						}
					}
				}
				commit();
			}
		}
	}

	@Override
	public int selectDbVersion() throws SQLException {
		PreparedStatement stmt = null;
		try {
			stmt = conn.prepareStatement("SELECT db_schema_version FROM meta");
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt("db_schema_version") : 0;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void updateDbVersion(int version) throws SQLException {
		PreparedStatement stmt = null;
		try {
			stmt = conn.prepareStatement("UPDATE meta SET db_schema_version = ?");
			stmt.setInt(1, version);
			stmt.execute();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void insertDbVersion(int version) throws SQLException {
		InsertStatement stmt = new InsertStatement("meta");
		stmt.setInt("db_schema_version", version);
		stmt.execute(conn);
	}

	@Override
	public Integer getPlayerId(String name) throws SQLException {
		PreparedStatement stmt = null;
		String nameLowerCase = name.toLowerCase();

		try {
			stmt = conn.prepareStatement("SELECT id FROM players WHERE Lower(name) = ?");
			stmt.setString(1, nameLowerCase);
			ResultSet rs = stmt.executeQuery();
			Integer playerId;
			if (rs.next()) {
				playerId = rs.getInt("id");
			} else {
				playerId = insertPlayer(name);
			}
			return playerId;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public int insertPlayer(String name) throws SQLException {
		InsertStatement stmt = new InsertStatement("players");
		stmt.setString("name", name);
		return stmt.execute(conn);
	}

	@Override
	public Integer getItemId(String name) throws SQLException {
		PreparedStatement stmt = null;
		String nameLowerCase = name.toLowerCase();

		try {
			stmt = conn.prepareStatement("SELECT id FROM items WHERE Lower(name) = ?");
			stmt.setString(1, nameLowerCase);
			ResultSet rs = stmt.executeQuery();
			Integer itemId;
			if (rs.next()) {
				itemId = rs.getInt("id");
			} else {
				itemId = insertItem(name);
			}
			return itemId;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public int insertItem(String name) throws SQLException {
		InsertStatement stmt = new InsertStatement("items");
		stmt.setString("name", name);
		return stmt.execute(conn);
	}

	@Override
	public void insertTransaction(ShopTransaction transaction) throws SQLException {
		insertTransactions(Arrays.asList(transaction));
	}

	@Override
	public void insertTransactions(Collection<ShopTransaction> transactions) throws SQLException {
		if (transactions.isEmpty()) {
			return;
		}

		InsertStatement stmt = new InsertStatement("transactions");
		for (ShopTransaction transaction : transactions) {
			Integer playerId = getPlayerId(transaction.getPlayer());
			Integer itemId = getItemId(transaction.getItem());

			stmt.setTimestamp("ts", transaction.getTs());
			stmt.setInt("player", playerId);
			stmt.setInt("item", itemId);
			stmt.setInt("quantity", transaction.getQuantity());
			stmt.setInt("amount", transaction.getAmount());
			stmt.setInt("balance", transaction.getBalance());
			stmt.nextRow();
		}
		stmt.execute(conn);
	}

	@Override
	public List<ShopTransaction> getTransactions() throws SQLException {
		return getTransactions(-1);
	}

	@Override
	public ShopTransaction getLatestTransaction() throws SQLException {
		List<ShopTransaction> transactions = getTransactions(1);
		return transactions.isEmpty() ? null : transactions.get(0);
	}

	public List<ShopTransaction> getTransactions(int limit) throws SQLException {
		//@formatter:off
		String sql =
		"SELECT t.ts, t.amount, t.balance, t.quantity, p.name AS playerName, i.name AS itemName " +
		"FROM transactions t " +
		"INNER JOIN players p ON t.player = p.id " +
		"INNER JOIN items i ON t.item = i.id " +
		"ORDER BY t.ts DESC";
		//@formatter:on

		PreparedStatement selectStmt = null;
		try {
			selectStmt = conn.prepareStatement(sql);
			if (limit > 0) {
				selectStmt.setMaxRows(limit);
			}
			ResultSet rs = selectStmt.executeQuery();
			List<ShopTransaction> transactions = new ArrayList<ShopTransaction>();
			while (rs.next()) {
				ShopTransaction transaction = new ShopTransaction();
				transaction.setAmount(rs.getInt("amount"));
				transaction.setBalance(rs.getInt("balance"));
				transaction.setItem(rs.getString("itemName"));
				transaction.setPlayer(rs.getString("playerName"));
				transaction.setQuantity(rs.getInt("quantity"));
				transaction.setTs(new Date(rs.getTimestamp("ts").getTime()));

				transactions.add(transaction);
			}
			return transactions;
		} finally {
			closeStatements(selectStmt);
		}
	}

	@Override
	public Map<String, ItemGroup> getItemGroups() throws SQLException {
		return getItemGroups(null, null);
	}

	@Override
	public Map<String, ItemGroup> getItemGroups(Date from, Date to) throws SQLException {
		PreparedStatement stmt = null;
		Map<String, ItemGroup> itemGroups = new TreeMap<String, ItemGroup>();

		try {
			//@formatter:off
			String sql =
			"SELECT Sum(t.amount) AS amountSum, Sum(t.quantity) AS quantitySum, i.name AS itemName " + 
			"FROM transactions t INNER JOIN items i ON t.item = i.id " + 
			"WHERE t.amount > 0 ";
			if (from != null) {
				sql += "AND ts >= ? ";
			}
			if (to != null) {
				sql += "AND ts < ? ";
			}
			sql += "GROUP BY i.name";
			//@formatter:on

			stmt = conn.prepareStatement(sql);
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, new java.sql.Timestamp(from.getTime()));
			}
			if (to != null) {
				stmt.setTimestamp(index++, new java.sql.Timestamp(to.getTime()));
			}
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				ItemGroup itemGroup = new ItemGroup();
				itemGroup.setItem(rs.getString("itemName"));
				itemGroup.setSoldAmount(rs.getInt("amountSum"));
				itemGroup.setSoldQuantity(rs.getInt("quantitySum"));

				itemGroups.put(itemGroup.getItem(), itemGroup);
			}
		} finally {
			closeStatements(stmt);
		}

		try {
			//@formatter:off
			String sql =
			"SELECT Sum(t.amount) AS amountSum, Sum(t.quantity) AS quantitySum, i.name AS itemName " + 
			"FROM transactions t INNER JOIN items i ON t.item = i.id " + 
			"WHERE t.amount < 0 ";
			if (from != null) {
				sql += "AND ts >= ? ";
			}
			if (to != null) {
				sql += "AND ts < ? ";
			}
			sql += "GROUP BY i.name";
			//@formatter:on

			stmt = conn.prepareStatement(sql);
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, new java.sql.Timestamp(from.getTime()));
			}
			if (to != null) {
				stmt.setTimestamp(index++, new java.sql.Timestamp(to.getTime()));
			}
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				String itemName = rs.getString("itemName");
				ItemGroup itemGroup = itemGroups.get(itemName);
				if (itemGroup == null) {
					itemGroup = new ItemGroup();
					itemGroup.setItem(itemName);
					itemGroups.put(itemGroup.getItem(), itemGroup);
				}
				itemGroup.setBoughtAmount(rs.getInt("amountSum"));
				itemGroup.setBoughtQuantity(rs.getInt("quantitySum"));
			}
		} finally {
			closeStatements(stmt);
		}

		return itemGroups;
	}

	@Override
	public void commit() throws SQLException {
		conn.commit();
	}

	@Override
	public void rollback() {
		try {
			conn.rollback();
		} catch (SQLException e) {
			//exception is caught here instead of being thrown because ever time I catch the exception for rollback(), I just log the exception and continue on
			//plus, an exception is unlikely to be thrown here anyway
			logger.log(Level.WARNING, "Problem rolling back transaction.", e);
		}
	}

	@Override
	public void close() throws SQLException {
		try {
			logger.info("Closing database.");
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		} catch (SQLException se) {
			if (se.getErrorCode() == 50000 && "XJ015".equals(se.getSQLState())) {
				// we got the expected exception
			} else if (se.getErrorCode() == 45000 && "08006".equals(se.getSQLState())) {
				// we got the expected exception for single database shutdown
			} else {
				// if the error code or SQLState is different, we have
				// an unexpected exception (shutdown failed)
				throw se;
			}
		}
	}

	/**
	 * Closes a list of Statements.
	 * @param statements the statements to close (nulls are ignored)
	 */
	protected void closeStatements(Statement... statements) {
		for (Statement statement : statements) {
			if (statement != null) {
				try {
					statement.close();
				} catch (SQLException e) {
					//ignore
				}
			}
		}
	}
}