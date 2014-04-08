package emcshop.db;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.derby.jdbc.EmbeddedDriver;

import emcshop.ItemIndex;
import emcshop.scraper.BonusFeeTransaction;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.util.ClasspathUtils;

/**
 * Data access object implementation for embedded Derby database.
 * @author Michael Angstadt
 */
public abstract class DirbyDbDao implements DbDao {
	private static final Logger logger = Logger.getLogger(DirbyDbDao.class.getName());

	/**
	 * The current version of the database schema. Do not use this variable
	 * directly. Use {@link #getAppDbVersion()} instead, because this method
	 * gets overridden in unit tests.
	 */
	public static final int schemaVersion = 19;

	protected Connection conn;
	protected String jdbcUrl;
	private Map<Integer, Date[]> firstLastSeenDates = new HashMap<Integer, Date[]>();

	/**
	 * Connects to the database and creates the database from scratch if it
	 * doesn't exist.
	 * @param jdbcUrl the JDBC URL
	 * @param create true to create the database schema, false not to
	 * @param listener
	 * @throws SQLException
	 */
	protected void init(String jdbcUrl, boolean create, DbListener listener) throws SQLException {
		this.jdbcUrl = jdbcUrl;
		logger.info("Starting database...");

		//create shutdown hook to shutdown Derby when the program terminates
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				logger.fine("Shutting down the database...");
				try {
					close();
				} catch (SQLException e) {
					logger.log(Level.SEVERE, "Error stopping database.", e);
				}
			}
		});

		//create the connection
		createConnection(create);

		//create tables if database doesn't exist
		if (create) {
			if (listener != null) {
				listener.onCreate();
			}
			logger.info("Database not found.  Creating the database...");
			createSchema();
		}
	}

	protected void init(Connection connection) {
		conn = connection;
	}

	@Override
	public void reconnect() throws SQLException {
		createConnection(false);
	}

	@Override
	public void updateToLatestVersion(DbListener listener) throws SQLException {
		int curVersion = selectDbVersion();
		int latestVersion = getAppDbVersion();
		if (curVersion == latestVersion) {
			//schema up to date
			return;
		}

		if (curVersion > latestVersion) {
			throw new SQLException("The version of your EMC Shopkeeper database is newer than the EMC Shopkeeper app you are running.  Please download the latest version of EMC Shopkeeper.");
		}

		if (listener != null) {
			listener.onMigrate(curVersion, latestVersion);
		}

		logger.info("Database schema out of date.  Upgrading from version " + curVersion + " to " + latestVersion + ".");
		String sql = null;
		Statement statement = conn.createStatement();
		try {
			while (curVersion < latestVersion) {
				logger.info("Performing schema update from version " + curVersion + " to " + (curVersion + 1) + ".");

				SQLStatementReader in = new SQLStatementReader(getMigrationScript(curVersion));
				try {
					while ((sql = in.readStatement()) != null) {
						statement.execute(sql);
					}
					sql = null;
				} finally {
					IOUtils.closeQuietly(in);
				}

				curVersion++;
			}
			upsertDbVersion(latestVersion);
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
			closeStatements(statement);
		}
		commit();
	}

	/**
	 * Gets the SQL script for upgrading a database from one version to the
	 * next.
	 * @param currentVersion the version to update from
	 * @return the SQL script
	 */
	Reader getMigrationScript(int currentVersion) {
		String script = "migrate-" + currentVersion + "-" + (currentVersion + 1) + ".sql";
		return new InputStreamReader(getClass().getResourceAsStream(script));
	}

	/**
	 * Gets the database version that this DAO is compatible with (as opposed to
	 * {@link #selectDbVersion()}, which retrieves the version of the database
	 * itself).
	 * @return the DAO's database version
	 */
	public int getAppDbVersion() {
		return schemaVersion;
	}

	@Override
	public int selectDbVersion() throws SQLException {
		PreparedStatement stmt = stmt("SELECT db_schema_version FROM meta");
		try {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void upsertDbVersion(int version) throws SQLException {
		PreparedStatement stmt = stmt("UPDATE meta SET db_schema_version = ?");
		try {
			stmt.setInt(1, version);
			int updated = stmt.executeUpdate();
			if (updated > 0) {
				return;
			}
		} finally {
			closeStatements(stmt);
		}

		InsertStatement insertStmt = new InsertStatement("meta");
		insertStmt.setInt("db_schema_version", version);
		insertStmt.execute(conn);
	}

	@Override
	public int selectRupeeBalanceMeta() throws SQLException {
		PreparedStatement stmt = stmt("SELECT rupee_balance FROM meta");
		try {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public Integer selectRupeeBalance() throws SQLException {
		PreparedStatement stmt = stmt("SELECT rupee_balance FROM update_log ORDER BY ts DESC");
		try {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : null;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public Player selsertPlayer(String name) throws SQLException {
		PreparedStatement stmt = stmt("SELECT * FROM players WHERE Lower(name) = Lower(?)");
		try {
			stmt.setString(1, name);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				Player player = new Player();
				player.setId(rs.getInt("id"));
				player.setName(rs.getString("name"));
				player.setFirstSeen(toDate(rs.getTimestamp("first_seen")));
				player.setLastSeen(toDate(rs.getTimestamp("last_seen")));
				return player;
			}
		} finally {
			closeStatements(stmt);
		}

		InsertStatement insertStmt = new InsertStatement("players");
		insertStmt.setString("name", name);
		int id = insertStmt.execute(conn);

		Player player = new Player();
		player.setId(id);
		player.setName(name);
		return player;
	}

	@Override
	public Integer getItemId(String name) throws SQLException {
		PreparedStatement stmt = stmt("SELECT id FROM items WHERE Lower(name) = Lower(?)");
		try {
			stmt.setString(1, name);
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : null;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public int selsertItem(String name) throws SQLException {
		Integer itemId = getItemId(name);
		if (itemId == null) {
			InsertStatement stmt = new InsertStatement("items");
			stmt.setString("name", name);
			itemId = stmt.execute(conn);
		}
		return itemId;
	}

	@Override
	public List<String> getItemNames() throws SQLException {
		PreparedStatement stmt = stmt("SELECT name FROM items ORDER BY Lower(name)");
		try {
			ResultSet rs = stmt.executeQuery();
			List<String> names = new ArrayList<String>();
			while (rs.next()) {
				names.add(rs.getString("name"));
			}
			return names;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void updateItemNamesAndAliases() throws SQLException {
		int dbVersion = selectDbVersion();
		Map<String, List<String>> aliases = ItemIndex.instance().getDisplayNameToEmcNamesMapping();
		for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
			String newName = entry.getKey();
			Integer newNameId = getItemId(newName);

			List<String> oldNames = entry.getValue();
			List<Integer> oldNameIds = new ArrayList<Integer>(oldNames.size());
			for (String oldName : oldNames) {
				Integer oldNameId = getItemId(oldName);
				if (oldNameId != null) {
					oldNameIds.add(oldNameId);
				}
			}

			if (oldNameIds.isEmpty() && newNameId == null) {
				//nothing needs to be changed because neither name exists
				continue;
			}

			if (oldNameIds.isEmpty() && newNameId != null) {
				//nothing needs to be changed because the new name is already in use
				continue;
			}
			if (!oldNameIds.isEmpty() && newNameId == null) {
				//the old name is still being used, so change it to the new name
				newNameId = oldNameIds.get(0);
				oldNameIds = oldNameIds.subList(1, oldNameIds.size());

				//change the name in the "items" table
				updateItemName(newNameId, newName);

				if (!oldNameIds.isEmpty()) {
					if (dbVersion >= 7) {
						updateInventoryItem(oldNameIds, newNameId);
					}
					deleteItems(oldNameIds.toArray(new Integer[0]));
				}
				continue;
			}

			if (!oldNameIds.isEmpty() && newNameId != null) {
				//both the old and new names exist
				//update all transactions that use the old name with the new name, and delete the old name

				updateTransactionItem(oldNameIds, newNameId);

				if (dbVersion >= 7) {
					updateInventoryItem(oldNameIds, newNameId);
				}

				deleteItems(oldNameIds.toArray(new Integer[0]));

				continue;
			}
		}
	}

	private void updateItemName(Integer id, String newName) throws SQLException {
		PreparedStatement stmt = stmt("UPDATE items SET name = ? WHERE id = ?");
		try {
			stmt.setString(1, newName);
			stmt.setInt(2, id);
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void removeDuplicateItems() throws SQLException {
		//get the ID(s) of each item
		PreparedStatement stmt = stmt("SELECT id, name FROM items");
		Map<String, List<Integer>> itemIds = new HashMap<String, List<Integer>>();
		try {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Integer id = rs.getInt("id");
				String name = rs.getString("name").toLowerCase();

				List<Integer> ids = itemIds.get(name);
				if (ids == null) {
					ids = new ArrayList<Integer>();
					itemIds.put(name, ids);
				}
				ids.add(id);
			}
		} finally {
			closeStatements(stmt);
		}

		int dbVersion = selectDbVersion();

		//update the transactions to use just one of the item rows, and delete the rest
		for (List<Integer> ids : itemIds.values()) {
			if (ids.size() <= 1) {
				//there are no duplicates
				continue;
			}

			Integer newId = ids.get(0);
			List<Integer> oldIds = ids.subList(1, ids.size());

			if (dbVersion >= 7) {
				updateInventoryItem(oldIds, newId);
			}
			updateTransactionItem(oldIds, newId);
			deleteItems(oldIds.toArray(new Integer[0]));
		}
	}

	private void deleteItems(Integer... ids) throws SQLException {
		if (ids.length == 0) {
			return;
		}

		PreparedStatement stmt = stmt("DELETE FROM items WHERE id " + in(ids.length));
		try {
			int i = 1;
			for (Integer id : ids) {
				stmt.setInt(i++, id);
			}
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void populateItemsTable() throws SQLException {
		//get all existing item names
		Set<String> existingItemNames = new HashSet<String>();
		for (String itemName : getItemNames()) {
			existingItemNames.add(itemName.toLowerCase());
		}

		//find all items names that aren't in the database
		List<String> toInsert = new ArrayList<String>();
		ItemIndex itemIndex = ItemIndex.instance();
		for (String itemName : itemIndex.getItemNames()) {
			if (existingItemNames.contains(itemName.toLowerCase())) {
				continue;
			}

			toInsert.add(itemName);
		}
		if (toInsert.isEmpty()) {
			//no items to insert
			return;
		}

		//insert the missing item names
		InsertStatement stmt = null;
		for (String name : toInsert) {
			if (stmt == null) {
				stmt = new InsertStatement("items");
			} else {
				stmt.nextRow();
			}
			stmt.setString("name", name);
		}
		stmt.execute(conn);
	}

	@Override
	public Date getEarliestTransactionDate() throws SQLException {
		PreparedStatement stmt = stmt("SELECT Min(ts) FROM transactions");
		try {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? toDate(rs.getTimestamp(1)) : null;
		} finally {
			closeStatements(stmt);
		}
	}

	private void updateTransactionItem(List<Integer> oldItemIds, int newItemId) throws SQLException {
		if (oldItemIds.isEmpty()) {
			return;
		}

		PreparedStatement stmt = stmt("UPDATE transactions SET item = ? WHERE item " + in(oldItemIds.size()));
		try {
			int i = 1;
			stmt.setInt(i++, newItemId);
			for (Integer oldItemId : oldItemIds) {
				stmt.setInt(i++, oldItemId);
			}
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void insertTransaction(ShopTransaction transaction, boolean updateInventory) throws SQLException {
		Player player = selsertPlayer(transaction.getPlayer());
		Integer itemId = selsertItem(transaction.getItem());
		Date ts = transaction.getTs();

		//keep track of the first/last seen dates so they can be updated (in "commit()")
		Date dates[] = firstLastSeenDates.get(player.getId());
		if (dates == null) {
			dates = new Date[] { player.getFirstSeen(), player.getLastSeen() };
			firstLastSeenDates.put(player.getId(), dates);
		}

		Date earliest = dates[0];
		if (earliest == null || ts.getTime() < earliest.getTime()) {
			dates[0] = ts;
		}

		Date latest = dates[1];
		if (latest == null || ts.getTime() > latest.getTime()) {
			dates[1] = ts;
		}

		//insert transaction
		InsertStatement stmt = new InsertStatement("transactions");
		stmt.setTimestamp("ts", ts);
		stmt.setInt("player", player.getId());
		stmt.setInt("item", itemId);
		stmt.setInt("quantity", transaction.getQuantity());
		stmt.setInt("amount", transaction.getAmount());
		stmt.setInt("balance", transaction.getBalance());
		int id = stmt.execute(conn);
		transaction.setId(id);

		if (updateInventory) {
			addToInventory(itemId, transaction.getQuantity());
		}
	}

	@Override
	public void insertPaymentTransactions(Collection<PaymentTransaction> transactions) throws SQLException {
		if (transactions.isEmpty()) {
			return;
		}

		InsertStatement stmt = new InsertStatement("payment_transactions");
		for (PaymentTransaction transaction : transactions) {
			Player player = selsertPlayer(transaction.getPlayer());

			insertPaymentTransaction(transaction, player.getId(), stmt);
			stmt.nextRow();
		}
		stmt.execute(conn);
	}

	@Override
	public void upsertPaymentTransaction(PaymentTransaction transaction) throws SQLException {
		if (transaction.getId() == null) {
			Player player = selsertPlayer(transaction.getPlayer());

			InsertStatement stmt = new InsertStatement("payment_transactions");
			insertPaymentTransaction(transaction, player.getId(), stmt);
			Integer id = stmt.execute(conn);
			transaction.setId(id);
		} else {
			PreparedStatement stmt = stmt("UPDATE payment_transactions SET amount = ?, balance = ? WHERE id = ?");
			try {
				stmt.setInt(1, transaction.getAmount());
				stmt.setInt(2, transaction.getBalance());
				stmt.setInt(3, transaction.getId());
				stmt.executeUpdate();
			} finally {
				closeStatements(stmt);
			}
		}
	}

	private void insertPaymentTransaction(PaymentTransaction transaction, int playerId, InsertStatement stmt) {
		stmt.setTimestamp("ts", transaction.getTs());
		stmt.setInt("player", playerId);
		stmt.setInt("amount", transaction.getAmount());
		stmt.setInt("balance", transaction.getBalance());
	}

	@Override
	public Date getLatestTransactionDate() throws SQLException {
		Date shopTs;
		PreparedStatement selectStmt = stmt("SELECT Max(ts) FROM transactions");
		try {
			ResultSet rs = selectStmt.executeQuery();
			shopTs = rs.next() ? toDate(rs.getTimestamp(1)) : null;
		} finally {
			closeStatements(selectStmt);
		}

		Date paymentTs;
		selectStmt = stmt("SELECT Max(ts) FROM payment_transactions");
		try {
			ResultSet rs = selectStmt.executeQuery();
			paymentTs = rs.next() ? toDate(rs.getTimestamp(1)) : null;
		} finally {
			closeStatements(selectStmt);
		}

		if (shopTs == null && paymentTs == null) {
			return null;
		}
		if (shopTs != null && paymentTs == null) {
			return shopTs;
		}
		if (shopTs == null && paymentTs != null) {
			return paymentTs;
		}
		return (shopTs.compareTo(paymentTs) > 0) ? shopTs : paymentTs;
	}

	@SuppressWarnings("unused")
	private List<ShopTransaction> getTransactions(int limit) throws SQLException {
		//@formatter:off
		String sql =
		"SELECT t.id, t.ts, t.amount, t.balance, t.quantity, p.name AS playerName, i.name AS itemName " +
		"FROM transactions t " +
		"INNER JOIN players p ON t.player = p.id " +
		"INNER JOIN items i ON t.item = i.id " +
		"ORDER BY t.ts DESC";
		//@formatter:on

		PreparedStatement selectStmt = stmt(sql);
		try {
			if (limit > 0) {
				selectStmt.setMaxRows(limit);
			}
			ResultSet rs = selectStmt.executeQuery();
			List<ShopTransaction> transactions = new ArrayList<ShopTransaction>();
			while (rs.next()) {
				ShopTransaction transaction = new ShopTransaction();
				transaction.setId(rs.getInt("id"));
				transaction.setAmount(rs.getInt("amount"));
				transaction.setBalance(rs.getInt("balance"));
				transaction.setItem(rs.getString("itemName"));
				transaction.setPlayer(rs.getString("playerName"));
				transaction.setQuantity(rs.getInt("quantity"));
				transaction.setTs(toDate(rs.getTimestamp("ts")));

				transactions.add(transaction);
			}
			return transactions;
		} finally {
			closeStatements(selectStmt);
		}
	}

	@Override
	public List<PaymentTransaction> getPendingPaymentTransactions() throws SQLException {
		//@formatter:off
		String sql =
		"SELECT pt.id, pt.ts, pt.amount, pt.balance, p.name AS playerName " +
		"FROM payment_transactions pt " +
		"INNER JOIN players p ON pt.player = p.id " +
		"WHERE pt.\"transaction\" IS NULL " +
		"AND pt.ignore = false " +
		"ORDER BY pt.ts DESC ";
		//@formatter:on

		PreparedStatement selectStmt = stmt(sql);
		try {
			ResultSet rs = selectStmt.executeQuery();
			List<PaymentTransaction> transactions = new ArrayList<PaymentTransaction>();
			while (rs.next()) {
				PaymentTransaction transaction = new PaymentTransaction();
				transaction.setId(rs.getInt("id"));
				transaction.setAmount(rs.getInt("amount"));
				transaction.setBalance(rs.getInt("balance"));
				transaction.setPlayer(rs.getString("playerName"));
				transaction.setTs(toDate(rs.getTimestamp("ts")));

				transactions.add(transaction);
			}
			return transactions;
		} finally {
			closeStatements(selectStmt);
		}
	}

	@Override
	public int countPendingPaymentTransactions() throws SQLException {
		//@formatter:off
		String sql =
		"SELECT Count(*) " +
		"FROM payment_transactions " +
		"WHERE \"transaction\" IS NULL " +
		"AND ignore = false";
		//@formatter:on

		PreparedStatement selectStmt = stmt(sql);
		try {
			ResultSet rs = selectStmt.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		} finally {
			closeStatements(selectStmt);
		}
	}

	@Override
	public void ignorePaymentTransaction(Integer id) throws SQLException {
		PreparedStatement stmt = stmt("UPDATE payment_transactions SET ignore = ? WHERE id = ?");
		try {
			stmt.setBoolean(1, true);
			stmt.setInt(2, id);
			stmt.execute();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void assignPaymentTransaction(Integer paymentId, Integer transactionId) throws SQLException {
		PreparedStatement stmt = stmt("UPDATE payment_transactions SET \"transaction\" = ? WHERE id = ?");
		try {
			stmt.setInt(1, transactionId);
			stmt.setInt(2, paymentId);
			stmt.execute();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public Collection<ItemGroup> getItemGroups(Date from, Date to) throws SQLException {
		Map<String, ItemGroup> itemGroups = new HashMap<String, ItemGroup>();

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

		PreparedStatement stmt = stmt(sql);
		try {
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, toTimestamp(from));
			}
			if (to != null) {
				stmt.setTimestamp(index++, toTimestamp(to));
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

		//@formatter:off
		sql =
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

		stmt = stmt(sql);
		try {
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, toTimestamp(from));
			}
			if (to != null) {
				stmt.setTimestamp(index++, toTimestamp(to));
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

		return itemGroups.values();
	}

	public List<ShopTransaction> getTransactionsByDate(Date from, Date to) throws SQLException {
		//@formatter:off
		String sql =
		"SELECT t.ts, p.name AS player, i.name AS item, t.amount, t.quantity " + 
		"FROM transactions t INNER JOIN items i ON t.item = i.id " +
		"INNER JOIN players p ON t.player = p.id ";
		if (from != null) {
			sql += "WHERE ts >= ? ";
		}
		if (to != null) {
			sql += (from == null) ? "WHERE" : "AND";
			sql += " ts < ? ";
		}
		sql += " ORDER BY t.ts";
		//@formatter:on

		PreparedStatement stmt = stmt(sql);
		List<ShopTransaction> transactions = new ArrayList<ShopTransaction>();
		Map<String, ShopTransaction> lastTransactionByItem = new HashMap<String, ShopTransaction>();
		Map<ShopTransaction, Date> dateOfLastTransaction = new HashMap<ShopTransaction, Date>();
		try {
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, toTimestamp(from));
			}
			if (to != null) {
				stmt.setTimestamp(index++, toTimestamp(to));
			}
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Date ts = toDate(rs.getTimestamp("ts"));
				String player = rs.getString("player");
				String item = rs.getString("item");
				int amount = rs.getInt("amount");
				int quantity = rs.getInt("quantity");

				String key = player + ":" + item;
				ShopTransaction transaction = lastTransactionByItem.get(key);
				if (transaction != null) {
					long diff = ts.getTime() - dateOfLastTransaction.get(transaction).getTime();
					if (diff <= 1000 * 60 * 2) {
						//if the transactions occurred within 2 minutes of the last one, then consider it part of the same, consolidated transaction
						transaction.setAmount(transaction.getAmount() + amount);
						transaction.setQuantity(transaction.getQuantity() + quantity);
						dateOfLastTransaction.put(transaction, ts);
						continue;
					}
				}

				transaction = new ShopTransaction();
				transaction.setTs(ts);
				transaction.setPlayer(player);
				transaction.setItem(item);
				transaction.setAmount(amount);
				transaction.setQuantity(quantity);
				lastTransactionByItem.put(key, transaction);
				dateOfLastTransaction.put(transaction, ts);
				transactions.add(transaction);
			}
		} finally {
			closeStatements(stmt);
		}

		return transactions;
	}

	@Override
	public Collection<PlayerGroup> getPlayerGroups(Date from, Date to) throws SQLException {
		Map<String, PlayerGroup> playerGroups = new HashMap<String, PlayerGroup>();

		//@formatter:off
		String sql =
		"SELECT t.amount, t.quantity, t.player, p.name AS playerName, p.first_seen, p.last_seen, i.name AS itemName " + 
		"FROM transactions t " +
		"INNER JOIN items i ON t.item = i.id " +
		"INNER JOIN players p ON t.player = p.id ";
		if (from != null) {
			sql += "WHERE t.ts >= ? ";
		}
		if (to != null) {
			sql += ((from == null) ? "WHERE" : "AND") + " t.ts < ? ";
		}
		//@formatter:on

		PreparedStatement stmt = stmt(sql);
		try {
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, toTimestamp(from));
			}
			if (to != null) {
				stmt.setTimestamp(index++, toTimestamp(to));
			}
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				String playerName = rs.getString("playerName");
				PlayerGroup playerGroup = playerGroups.get(playerName);
				if (playerGroup == null) {
					playerGroup = new PlayerGroup();

					Player player = new Player();
					player.setId(rs.getInt("player"));
					player.setName(playerName);
					player.setFirstSeen(toDate(rs.getTimestamp("first_seen")));
					player.setLastSeen(toDate(rs.getTimestamp("last_seen")));
					playerGroup.setPlayer(player);

					playerGroups.put(playerName, playerGroup);
				}

				String itemName = rs.getString("itemName");
				ItemGroup itemGroup = playerGroup.getItems().get(itemName);
				if (itemGroup == null) {
					itemGroup = new ItemGroup();
					itemGroup.setItem(itemName);
					playerGroup.getItems().put(itemName, itemGroup);
				}

				int amount = rs.getInt("amount");
				if (amount < 0) {
					itemGroup.setBoughtAmount(itemGroup.getBoughtAmount() + amount);
				} else {
					itemGroup.setSoldAmount(itemGroup.getSoldAmount() + amount);
				}

				int quantity = rs.getInt("quantity");
				if (quantity < 0) {
					itemGroup.setSoldQuantity(itemGroup.getSoldQuantity() + quantity);
				} else {
					itemGroup.setBoughtQuantity(itemGroup.getBoughtQuantity() + quantity);
				}
			}
		} finally {
			closeStatements(stmt);
		}

		return playerGroups.values();
	}

	@Override
	public Collection<Inventory> getInventory() throws SQLException {
		PreparedStatement stmt = stmt("SELECT inventory.*, items.name AS item_name FROM inventory INNER JOIN items ON inventory.item = items.id");
		Collection<Inventory> inventory = new ArrayList<Inventory>();

		try {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Inventory inv = new Inventory();
				inv.setId(rs.getInt("id"));
				inv.setItemId(rs.getInt("item"));
				inv.setItem(rs.getString("item_name"));
				inv.setQuantity(rs.getInt("quantity"));
				inventory.add(inv);
			}
			return inventory;
		} finally {
			closeStatements(stmt);
		}
	}

	private void addToInventory(Integer itemId, Integer quantityToAdd) throws SQLException {
		PreparedStatement stmt = stmt("UPDATE inventory SET quantity = quantity + ? WHERE item = ?");

		try {
			stmt.setInt(1, quantityToAdd);
			stmt.setInt(2, itemId);
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	private void updateInventoryItem(List<Integer> oldItemIds, int newItemId) throws SQLException {
		if (oldItemIds.isEmpty()) {
			return;
		}

		List<Integer> allIds = new ArrayList<Integer>(oldItemIds);
		allIds.add(newItemId);

		int totalQuantity = 0;
		PreparedStatement stmt = stmt("SELECT Sum(quantity) FROM inventory WHERE item " + in(allIds.size()));
		try {
			int i = 1;
			for (Integer id : allIds) {
				stmt.setInt(i++, id);
			}

			ResultSet rs = stmt.executeQuery();
			totalQuantity = rs.next() ? rs.getInt(1) : 0;
		} finally {
			closeStatements(stmt);
		}

		//update the quantity
		stmt = stmt("UPDATE inventory SET quantity = ? WHERE item = ?");
		try {
			stmt.setInt(1, totalQuantity);
			stmt.setInt(2, newItemId);
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}

		//delete the old entries
		stmt = stmt("DELETE FROM inventory WHERE item " + in(oldItemIds.size()));
		try {
			int i = 1;
			for (Integer id : oldItemIds) {
				stmt.setInt(i++, id);
			}
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void upsertInventory(Inventory inventory) throws SQLException {
		upsertInventory(inventory.getItem(), inventory.getQuantity(), false);
	}

	@Override
	public void upsertInventory(String item, Integer quantity, boolean add) throws SQLException {
		int itemId = selsertItem(item);

		PreparedStatement stmt = stmt("SELECT id FROM inventory WHERE item = ?");
		Integer invId;
		try {
			stmt.setInt(1, itemId);
			ResultSet rs = stmt.executeQuery();
			invId = rs.next() ? rs.getInt("id") : null;
		} finally {
			closeStatements(stmt);
		}

		if (invId == null) {
			InsertStatement stmt2 = new InsertStatement("inventory");
			stmt2.setInt("item", itemId);
			stmt2.setInt("quantity", quantity);
			stmt2.execute(conn);
		} else {
			String sql;
			if (add) {
				sql = "UPDATE inventory SET quantity = quantity + ? WHERE id = ?";
			} else {
				sql = "UPDATE inventory SET quantity = ? WHERE id = ?";
			}

			PreparedStatement stmt2 = stmt(sql);
			try {
				stmt2.setInt(1, quantity);
				stmt2.setInt(2, invId);
				stmt2.executeUpdate();
			} finally {
				closeStatements(stmt2);
			}
		}
	}

	@Override
	public void deleteInventory(Collection<Integer> ids) throws SQLException {
		if (ids.isEmpty()) {
			return;
		}

		//build SQL
		boolean first = true;
		StringBuilder sb = new StringBuilder("DELETE FROM inventory WHERE");
		for (int i = 0; i < ids.size(); i++) {
			if (!first) {
				sb.append(" OR");
			}
			sb.append(" id = ?");
			first = false;
		}

		//execute statement
		PreparedStatement stmt = stmt(sb.toString());
		try {
			int i = 1;
			for (Integer id : ids) {
				stmt.setInt(i++, id);
			}
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void updateBonusesFees(List<BonusFeeTransaction> transactions) throws SQLException {
		if (transactions.isEmpty()) {
			return;
		}

		//tally up totals
		int horse, lock, eggify, vault, signIn, vote;
		horse = lock = eggify = vault = signIn = vote = 0;
		for (BonusFeeTransaction transaction : transactions) {
			int amount = transaction.getAmount();

			if (transaction.isEggifyFee()) {
				eggify += amount;
				continue;
			}

			if (transaction.isHorseFee()) {
				horse += amount;
				continue;
			}

			if (transaction.isLockFee()) {
				lock += amount;
				continue;
			}

			if (transaction.isSignInBonus()) {
				signIn += amount;
				continue;
			}

			if (transaction.isVaultFee()) {
				vault += amount;
				continue;
			}

			if (transaction.isVoteBonus()) {
				vote += amount;
				continue;
			}
		}

		PreparedStatement stmt = stmt("UPDATE bonuses_fees SET eggify = eggify + ?, horse = horse + ?, lock = lock + ?, sign_in = sign_in + ?, vault = vault + ?, vote = vote + ?");
		try {
			int i = 1;
			stmt.setInt(i++, eggify);
			stmt.setInt(i++, horse);
			stmt.setInt(i++, lock);
			stmt.setInt(i++, signIn);
			stmt.setInt(i++, vault);
			stmt.setInt(i++, vote);
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public BonusFee getBonusesFees() throws SQLException {
		PreparedStatement stmt = stmt("SELECT * FROM bonuses_fees");
		try {
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}

			BonusFee bonusesFees = new BonusFee();
			bonusesFees.setSince(toDate(rs.getTimestamp("since")));
			bonusesFees.setEggify(rs.getInt("eggify"));
			bonusesFees.setHorse(rs.getInt("horse"));
			bonusesFees.setLock(rs.getInt("lock"));
			bonusesFees.setSignIn(rs.getInt("sign_in"));
			bonusesFees.setVault(rs.getInt("vault"));
			bonusesFees.setVote(rs.getInt("vote"));
			return bonusesFees;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void updateBonusesFeesSince(Date since) throws SQLException {
		PreparedStatement stmt = stmt("UPDATE bonuses_fees SET since = ? WHERE since IS NULL");
		try {
			stmt.setTimestamp(1, toTimestamp(since));
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public Map<Date, Profits> getProfitsByDay(Date from, Date to) throws SQLException {
		return getProfits(from, to, true);
	}

	@Override
	public Map<Date, Profits> getProfitsByMonth(Date from, Date to) throws SQLException {
		return getProfits(from, to, false);
	}

	private Map<Date, Profits> getProfits(Date from, Date to, boolean byDay) throws SQLException {
		Map<Date, Profits> profits = new LinkedHashMap<Date, Profits>();

		String sql = "SELECT t.ts, t.amount, i.name AS item FROM transactions t INNER JOIN items i ON t.item = i.id ";
		if (from != null) {
			sql += "WHERE t.ts >= ? ";
		}
		if (to != null) {
			sql += ((from == null) ? "WHERE" : "AND") + " t.ts < ? ";
		}

		PreparedStatement stmt = stmt(sql);
		try {
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, toTimestamp(from));
			}
			if (to != null) {
				stmt.setTimestamp(index++, toTimestamp(to));
			}
			ResultSet rs = stmt.executeQuery();
			Calendar c = Calendar.getInstance();
			while (rs.next()) {
				Date ts = toDate(rs.getTimestamp("ts"));
				c.setTime(ts);
				if (!byDay) {
					c.set(Calendar.DATE, 1);
				}
				c.set(Calendar.HOUR_OF_DAY, 0);
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				c.set(Calendar.MILLISECOND, 0);
				Date date = c.getTime();

				Profits p = profits.get(date);
				if (p == null) {
					p = new Profits();
					profits.put(date, p);
				}

				int amount = rs.getInt("amount");
				String item = rs.getString("item");
				p.addTransaction(item, amount);
			}
		} finally {
			closeStatements(stmt);
		}

		return profits;
	}

	@Override
	public void wipe() throws SQLException, IOException {
		logger.info("Wiping transactions...");
		Statement stmt = conn.createStatement();
		try {
			stmt.execute("DELETE FROM inventory");
			stmt.execute("DELETE FROM payment_transactions");
			stmt.execute("DELETE FROM transactions");
			stmt.execute("DELETE FROM players");
			stmt.execute("DELETE FROM items");
			stmt.execute("DELETE FROM bonuses_fees");
			stmt.execute("DELETE FROM update_log");

			stmt.execute("ALTER TABLE inventory ALTER COLUMN id RESTART WITH 1");
			stmt.execute("ALTER TABLE payment_transactions ALTER COLUMN id RESTART WITH 1");
			stmt.execute("ALTER TABLE transactions ALTER COLUMN id RESTART WITH 1");
			stmt.execute("ALTER TABLE players ALTER COLUMN id RESTART WITH 1");
			stmt.execute("ALTER TABLE items ALTER COLUMN id RESTART WITH 1");
			stmt.execute("ALTER TABLE update_log ALTER COLUMN id RESTART WITH 1");
			stmt.execute("INSERT INTO bonuses_fees (since) VALUES (NULL)");
			populateItemsTable();

			commit();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void commit() throws SQLException {
		//update first/last seen dates if transactions were inserted
		for (Map.Entry<Integer, Date[]> entry : firstLastSeenDates.entrySet()) {
			Integer playerId = entry.getKey();
			Date firstSeen = entry.getValue()[0];
			Date lastSeen = entry.getValue()[1];

			updateFirstLastSeen(playerId, firstSeen, lastSeen);
		}

		conn.commit();

		firstLastSeenDates.clear();
	}

	@Override
	public void rollback() {
		firstLastSeenDates.clear();

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
		firstLastSeenDates.clear();

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

	@Override
	public void calculatePlayersFirstLastSeenDates() throws SQLException {
		//@formatter:off
		String sql =
		"SELECT player, Min(ts) AS firstSeen, Max(ts) AS lastSeen " + 
		"FROM transactions " +
		"GROUP BY player";
		//@formatter:on

		PreparedStatement stmt = stmt(sql);
		try {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				int player = rs.getInt("player");
				Date firstSeen = toDate(rs.getTimestamp("firstSeen"));
				Date lastSeen = toDate(rs.getTimestamp("lastSeen"));
				updateFirstLastSeen(player, firstSeen, lastSeen);
			}
		} finally {
			closeStatements(stmt);
		}
	}

	/**
	 * Updates a player's first/last seen dates.
	 * @param playerId the player ID
	 * @param firstSeen the "first seen" date
	 * @param lastSeen the "last seen" date
	 * @throws SQLException
	 */
	private void updateFirstLastSeen(Integer playerId, Date firstSeen, Date lastSeen) throws SQLException {
		String sql = "UPDATE players SET ";
		if (firstSeen != null) {
			sql += "first_seen = ? ";
		}
		if (lastSeen != null) {
			if (firstSeen != null) {
				sql += ", ";
			}
			sql += "last_seen = ? ";
		}
		sql += "WHERE id = ?";

		PreparedStatement stmt = stmt(sql);
		try {
			int index = 1;
			if (firstSeen != null) {
				stmt.setTimestamp(index++, toTimestamp(firstSeen));
			}
			if (lastSeen != null) {
				stmt.setTimestamp(index++, toTimestamp(lastSeen));
			}
			stmt.setInt(index++, playerId);

			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void insertUpdateLog(Date ts, Integer rupeeBalance, int transactionCount, int paymentTransactionCount, int bonusFeeTransactionCount, long timeTaken) throws SQLException {
		InsertStatement stmt = new InsertStatement("update_log");
		stmt.setTimestamp("ts", toTimestamp(ts));
		if (rupeeBalance == null) {
			rupeeBalance = 0;
		}
		stmt.setInt("rupee_balance", rupeeBalance);
		stmt.setInt("transaction_count", transactionCount);
		stmt.setInt("payment_transaction_count", paymentTransactionCount);
		stmt.setInt("bonus_fee_transaction_count", bonusFeeTransactionCount);
		stmt.setInt("time_taken", (int) timeTaken);
		stmt.execute(conn);
	}

	@Override
	public Date getLatestUpdateDate() throws SQLException {
		PreparedStatement stmt = stmt("SELECT Max(ts) FROM update_log");
		try {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? toDate(rs.getTimestamp(1)) : null;
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public Date getSecondLatestUpdateDate() throws SQLException {
		PreparedStatement stmt = stmt("SELECT ts FROM update_log ORDER BY ts DESC");
		try {
			ResultSet rs = stmt.executeQuery();
			int count = 0;
			while (rs.next()) {
				count++;
				if (count == 2) {
					return toDate(rs.getTimestamp(1));
				}
			}
		} finally {
			closeStatements(stmt);
		}
		return null;
	}

	/**
	 * Converts a {@link Timestamp} to a {@link Date}
	 * @param timestamp the timestamp
	 * @return the date
	 */
	protected Date toDate(Timestamp timestamp) {
		return (timestamp == null) ? null : new Date(timestamp.getTime());
	}

	/**
	 * Converts a {@link Date} to a {@link Timestamp}.
	 * @param date the date
	 * @return the timestamp
	 */
	protected Timestamp toTimestamp(Date date) {
		return (date == null) ? null : new Timestamp(date.getTime());
	}

	/**
	 * Closes {@link Statement} objects.
	 * @param statements the statements to close (nulls are ignored)
	 */
	protected void closeStatements(Statement... statements) {
		for (Statement statement : statements) {
			if (statement == null) {
				continue;
			}

			try {
				statement.close();
			} catch (SQLException e) {
				//ignore
			}
		}
	}

	/**
	 * Creates the database connection.
	 * @param createDb true if the database doesn't exist and needs to be
	 * created, false to connect to an existing database
	 * @throws SQLException
	 */
	protected void createConnection(boolean createDb) throws SQLException {
		//load the driver
		try {
			Class.forName(EmbeddedDriver.class.getName()).newInstance();
		} catch (Exception e) {
			throw new SQLException("Database driver not on classpath.", e);
		}

		//create the connection
		String jdbcUrl = this.jdbcUrl;
		if (createDb) {
			jdbcUrl += ";create=true";
		}
		conn = DriverManager.getConnection(jdbcUrl);
		conn.setAutoCommit(false); // default is true
	}

	/**
	 * Creates the database schema.
	 * @throws SQLException
	 */
	protected void createSchema() throws SQLException {
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
			upsertDbVersion(getAppDbVersion());
		} catch (IOException e) {
			throw new SQLException("Error creating database.", e);
		} catch (SQLException e) {
			if (sql == null) {
				throw e;
			}
			throw new SQLException("Error executing SQL statement: " + sql, e);
		} finally {
			IOUtils.closeQuietly(in);
			closeStatements(statement);
		}
		commit();
	}

	/**
	 * Shorthand for creating a {@link PreparedStatement}.
	 * @param sql the SQL
	 * @return the {@link PreparedStatement} object
	 * @throws SQLException
	 */
	protected PreparedStatement stmt(String sql) throws SQLException {
		return conn.prepareStatement(sql);
	}

	/**
	 * Generates a SQL "IN()" statement.
	 * @param size the number of arguments in the "IN()" call
	 * @return the "IN()" statement (e.g. "IN (?, ?, ?)").
	 */
	protected String in(int size) {
		StringBuilder sb = new StringBuilder();
		sb.append("IN (");

		for (int i = 0; i < size; i++) {
			if (i > 0) {
				sb.append(" ,");
			}
			sb.append("?");
		}

		sb.append(")");
		return sb.toString();
	}

	/**
	 * Gets the JDBC database connection.
	 * @return the database connection
	 */
	public Connection getConnection() {
		return conn;
	}
}