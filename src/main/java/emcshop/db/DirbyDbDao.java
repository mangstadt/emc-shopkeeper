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
import java.util.Collections;
import java.util.Comparator;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.derby.jdbc.EmbeddedDriver;

import com.github.mangstadt.emc.rupees.dto.DailySigninBonus;
import com.github.mangstadt.emc.rupees.dto.EggifyFee;
import com.github.mangstadt.emc.rupees.dto.HorseSummonFee;
import com.github.mangstadt.emc.rupees.dto.LockTransaction;
import com.github.mangstadt.emc.rupees.dto.MailFee;
import com.github.mangstadt.emc.rupees.dto.RupeeTransaction;
import com.github.mangstadt.emc.rupees.dto.VaultFee;
import com.github.mangstadt.emc.rupees.dto.VoteBonus;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import emcshop.ItemIndex;
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
	public static final int schemaVersion = 36;

	protected Connection conn;
	protected String jdbcUrl;
	private Map<Integer, Date[]> firstLastSeenDates = new HashMap<Integer, Date[]>();

	private final Map<Class<? extends RupeeTransaction>, String> bonusFeeColumnNames;
	{
		ImmutableMap.Builder<Class<? extends RupeeTransaction>, String> builder = ImmutableMap.builder();
		builder.put(DailySigninBonus.class, "sign_in");
		builder.put(EggifyFee.class, "eggify");
		builder.put(HorseSummonFee.class, "horse");
		builder.put(LockTransaction.class, "lock");
		builder.put(MailFee.class, "mail");
		builder.put(VaultFee.class, "vault");
		builder.put(VoteBonus.class, "vote");
		bonusFeeColumnNames = builder.build();
	}

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
			throw new SQLException("Database version is newer than DAO verison.");
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

	@Override
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
		class Item {
			Integer id;
			String name;

			Item(Integer id, String name) {
				this.id = id;
				this.name = name;
			}
		}

		int dbVersion = selectDbVersion();
		Multimap<String, String> aliases = ItemIndex.instance().getDisplayNameToEmcNamesMapping();
		for (String displayName : aliases.keySet()) {
			Item itemOfficial = new Item(getItemId(displayName), displayName);

			List<Item> itemAliases = new ArrayList<Item>();
			for (String name : aliases.get(displayName)) {
				Integer id = getItemId(name);
				if (id != null) {
					itemAliases.add(new Item(id, name));
				}
			}

			if (itemAliases.isEmpty()) {
				/*
				 * Nothing needs to be changed because there are no aliases to
				 * worry about.
				 */
				continue;
			}

			/*
			 * Sort by item ID descending. Items with lower IDs are likely to
			 * have been in the database longer and are therefore likely to have
			 * been referenced by more rows throughout the database.
			 */
			Collections.sort(itemAliases, new Comparator<Item>() {
				@Override
				public int compare(Item o1, Item o2) {
					return o1.id - o2.id;
				}
			});

			/*
			 * Determine what row in the "items" table will act as the
			 * "official" row, and which rows need to be reassigned to point to
			 * the "official" row and then deleted.
			 */
			List<Integer> idsToReassign = new ArrayList<Integer>();
			Integer newId;
			if (itemOfficial.id == null) {
				/*
				 * The new item name doesn't exist in the table yet, so just
				 * rename the row that's already there. Rename the row that has
				 * been in the "items" table the longest so that we don't have
				 * to update as many rows to use a new ID.
				 */
				Item oldestItem = itemAliases.get(0);
				updateItemName(oldestItem.id, itemOfficial.name);
				if (itemAliases.size() == 1) {
					//no other aliases to worry about
					continue;
				}

				for (Item item : itemAliases.subList(1, itemAliases.size())) {
					idsToReassign.add(item.id);
				}
				newId = oldestItem.id;
			} else {
				if (itemOfficial.id < itemAliases.get(0).id) {
					/*
					 * If the row with the official name has a lower ID than any
					 * of the aliases, then it has been here the longest. Simply
					 * reassign everything to point to this row.
					 */
					idsToReassign = new ArrayList<Integer>();
					for (Item item : itemAliases) {
						idsToReassign.add(item.id);
					}
					newId = itemOfficial.id;
				} else {
					/*
					 * Rename the row that has been in the database the longest
					 * so that we don't have to update as many rows to use a new
					 * ID. Then, reassign everything to point to this row.
					 */
					Item oldestItem = itemAliases.get(0);
					updateItemName(oldestItem.id, itemOfficial.name);

					for (Item item : itemAliases.subList(1, itemAliases.size())) {
						idsToReassign.add(item.id);
					}
					idsToReassign.add(itemOfficial.id);
					newId = oldestItem.id;
				}
			}

			//reassign IDs to point to the new ID
			updateTransactionItem(idsToReassign, newId);
			if (dbVersion >= 7) {
				updateInventoryItem(idsToReassign, newId);
			}

			//delete the rows from the items table that are now no longer being used
			deleteItems(idsToReassign);
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
		ListMultimap<String, Integer> itemIds = ArrayListMultimap.create();
		try {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Integer id = rs.getInt("id");
				String name = rs.getString("name").toLowerCase();

				itemIds.put(name, id);
			}
		} finally {
			closeStatements(stmt);
		}

		int dbVersion = selectDbVersion();

		//update the transactions to use just one of the item rows, and delete the rest
		for (String itemName : itemIds.keySet()) {
			List<Integer> ids = itemIds.get(itemName);
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
			deleteItems(oldIds);
		}
	}

	private void deleteItems(Collection<Integer> itemIds) throws SQLException {
		if (itemIds.isEmpty()) {
			return;
		}

		PreparedStatement stmt = stmt("DELETE FROM items WHERE id " + in(itemIds.size()));
		try {
			int i = 1;
			for (Integer id : itemIds) {
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
		List<Date> dates = new ArrayList<Date>();
		String[] tables = { "transactions", "payment_transactions" };
		for (String table : tables) {
			PreparedStatement selectStmt = stmt("SELECT Min(ts) FROM " + table);
			try {
				ResultSet rs = selectStmt.executeQuery();
				if (!rs.next()) {
					continue;
				}

				Timestamp ts = rs.getTimestamp(1);
				if (ts == null) {
					continue;
				}

				dates.add(toDate(ts));
			} finally {
				closeStatements(selectStmt);
			}
		}

		if (dates.isEmpty()) {
			return null;
		}

		Collections.sort(dates);
		return dates.get(0);
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
	public void insertTransaction(ShopTransactionDb transaction, boolean updateInventory) throws SQLException {
		String playerName = transaction.getShopCustomer();
		Player player = (playerName == null) ? null : selsertPlayer(playerName);

		String ownerName = transaction.getShopOwner();
		Player owner = (ownerName == null) ? null : selsertPlayer(ownerName);

		Integer itemId = selsertItem(transaction.getItem());
		Date ts = transaction.getTs();

		//keep track of the first/last seen dates so they can be updated (in "commit()")
		//don't record this if this is not a transaction from the player's own shop
		if (player != null) {
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
		}

		//insert transaction
		InsertStatement stmt = new InsertStatement("transactions");
		stmt.setTimestamp("ts", ts);
		if (player != null) {
			stmt.setInt("player", player.getId());
		}
		if (owner != null) {
			stmt.setInt("shop_owner", owner.getId());
		}
		stmt.setInt("item", itemId);
		stmt.setInt("quantity", transaction.getQuantity());
		stmt.setInt("amount", transaction.getAmount());
		stmt.setInt("balance", transaction.getBalance());
		int id = stmt.execute(conn);
		transaction.setId(id);

		if (player != null && updateInventory) {
			addToInventory(itemId, transaction.getQuantity());
		}
	}

	@Override
	public void insertPaymentTransaction(PaymentTransactionDb transaction) throws SQLException {
		InsertStatement stmt = new InsertStatement("payment_transactions");
		Player player = selsertPlayer(transaction.getPlayer());
		insertPaymentTransaction(transaction, player.getId(), stmt);
		int id = stmt.execute(conn);
		transaction.setId(id);
	}

	@Override
	public void deletePaymentTransaction(PaymentTransactionDb transaction) throws SQLException {
		PreparedStatement stmt = stmt("DELETE FROM payment_transactions WHERE id = ?");
		try {
			stmt.setInt(1, transaction.getId());
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void upsertPaymentTransaction(PaymentTransactionDb transaction) throws SQLException {
		Integer id = transaction.getId();
		if (id == null) {
			Player player = selsertPlayer(transaction.getPlayer());

			InsertStatement stmt = new InsertStatement("payment_transactions");
			stmt.setTimestamp("ts", transaction.getTs());
			stmt.setInt("player", player.getId());
			stmt.setInt("amount", transaction.getAmount());
			stmt.setInt("balance", transaction.getBalance());
			stmt.setString("reason", transaction.getReason());

			id = stmt.execute(conn);
			transaction.setId(id);
		} else {
			PreparedStatement stmt = stmt("UPDATE payment_transactions SET amount = ?, balance = ? WHERE id = ?");
			try {
				stmt.setInt(1, transaction.getAmount());
				stmt.setInt(2, transaction.getBalance());
				stmt.setInt(3, id);
				stmt.executeUpdate();
			} finally {
				closeStatements(stmt);
			}
		}
	}

	private void insertPaymentTransaction(PaymentTransactionDb transaction, int playerId, InsertStatement stmt) {
		stmt.setTimestamp("ts", transaction.getTs());
		stmt.setInt("player", playerId);
		stmt.setInt("amount", transaction.getAmount());
		stmt.setInt("balance", transaction.getBalance());
		stmt.setString("reason", transaction.getReason());
	}

	@Override
	public Date getLatestTransactionDate() throws SQLException {
		Date latest = null;
		String queries[] = { "SELECT Max(ts) FROM transactions", "SELECT Max(ts) FROM payment_transactions", "SELECT latest_transaction_ts FROM bonuses_fees" };
		for (String query : queries) {
			Date date;
			PreparedStatement stmt = stmt(query);
			try {
				ResultSet rs = stmt.executeQuery();
				if (!rs.next()) {
					return null;
				}

				Timestamp ts = rs.getTimestamp(1);
				date = toDate(ts);
			} finally {
				closeStatements(stmt);
			}

			if (date != null && (latest == null || date.after(latest))) {
				latest = date;
			}
		}

		return latest;
	}

	@Override
	public List<PaymentTransactionDb> getPendingPaymentTransactions() throws SQLException {
		//@formatter:off
		String sql =
		"SELECT pt.id, pt.ts, pt.amount, pt.balance, pt.reason, p.name AS playerName " +
		"FROM payment_transactions pt " +
		"INNER JOIN players p ON pt.player = p.id " +
		"WHERE pt.\"transaction\" IS NULL " +
		"AND pt.ignore = false " +
		"ORDER BY pt.ts DESC ";
		//@formatter:on

		PreparedStatement selectStmt = stmt(sql);
		try {
			ResultSet rs = selectStmt.executeQuery();
			List<PaymentTransactionDb> transactions = new ArrayList<PaymentTransactionDb>();
			while (rs.next()) {
				PaymentTransactionDb transaction = new PaymentTransactionDb(rs);
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
	public Collection<ItemGroup> getItemGroups(Date from, Date to, ShopTransactionType transactionType) throws SQLException {
		Map<String, ItemGroup> itemGroups = new HashMap<String, ItemGroup>();

		//@formatter:off
		String sql =
		"SELECT Sum(t.amount) AS amountSum, Sum(t.quantity) AS quantitySum, i.name AS itemName " + 
		"FROM transactions t INNER JOIN items i ON t.item = i.id " + 
		"WHERE t.amount > 0 ";
		//@formatter:on

		if (transactionType == ShopTransactionType.MY_SHOP) {
			sql += "AND t.player IS NOT NULL ";
		} else if (transactionType == ShopTransactionType.OTHER_SHOPS) {
			sql += "AND t.shop_owner IS NOT NULL ";
		}

		if (from != null) {
			sql += "AND ts >= ? ";
		}
		if (to != null) {
			sql += "AND ts < ? ";
		}

		sql += "GROUP BY i.name";

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
		//@formatter:on

		if (transactionType == ShopTransactionType.MY_SHOP) {
			sql += "AND t.player IS NOT NULL ";
		} else if (transactionType == ShopTransactionType.OTHER_SHOPS) {
			sql += "AND t.shop_owner IS NOT NULL ";
		}

		if (from != null) {
			sql += "AND ts >= ? ";
		}
		if (to != null) {
			sql += "AND ts < ? ";
		}

		sql += "GROUP BY i.name";

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

	@Override
	public List<ShopTransactionDb> getTransactionsByDate(Date from, Date to, ShopTransactionType transactionType) throws SQLException {
		String sql;
		List<String> where = new ArrayList<String>();
		//@formatter:off
		if (transactionType == ShopTransactionType.MY_SHOP) {
			sql =
			"SELECT t.ts, p.name AS player, i.name AS item, t.amount, t.quantity " + 
			"FROM transactions t INNER JOIN items i ON t.item = i.id " +
			"INNER JOIN players p ON t.player = p.id ";
			
			where.add("t.player IS NOT NULL");
		} else if (transactionType == ShopTransactionType.OTHER_SHOPS) {
			sql =
			"SELECT t.ts, p.name AS shop_owner, i.name AS item, t.amount, t.quantity " + 
			"FROM transactions t INNER JOIN items i ON t.item = i.id " +
			"INNER JOIN players p ON t.shop_owner = p.id ";
			
			where.add("t.shop_owner IS NOT NULL");
		} else {
			sql =
			"SELECT t.ts, i.name AS item, t.amount, t.quantity, t.player, t.shop_owner " + 
			"FROM transactions t INNER JOIN items i ON t.item = i.id ";
		}
		//@formatter:on

		if (from != null) {
			where.add("ts >= ?");
		}
		if (to != null) {
			where.add("ts < ?");
		}

		if (!where.isEmpty()) {
			sql += " WHERE " + StringUtils.join(where, " AND ");
		}
		sql += " ORDER BY t.ts";

		PreparedStatement stmt = stmt(sql);
		List<ShopTransactionDb> transactions = new ArrayList<ShopTransactionDb>();
		Map<String, ShopTransactionDb> lastTransactionByItem = new HashMap<String, ShopTransactionDb>();
		Map<ShopTransactionDb, Date> dateOfLastTransaction = new HashMap<ShopTransactionDb, Date>();
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
				String shopCustomer, shopOwner;
				switch (transactionType) {
				case MY_SHOP:
					shopCustomer = rs.getString("player");
					shopOwner = null;
					break;
				case OTHER_SHOPS:
					shopCustomer = null;
					shopOwner = rs.getString("shop_owner");
					break;
				default:
					Integer id = (Integer) rs.getObject("player");
					if (id == null) {
						id = (Integer) rs.getObject("shop_owner");
						shopCustomer = null;
						shopOwner = getPlayerName(id);
					} else {
						shopCustomer = getPlayerName(id);
						shopOwner = null;
					}
					break;
				}

				String item = rs.getString("item");
				int amount = rs.getInt("amount");
				int quantity = rs.getInt("quantity");

				String key = ((shopCustomer == null) ? shopOwner : shopCustomer) + ":" + item;
				ShopTransactionDb transaction = lastTransactionByItem.get(key);
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

				transaction = new ShopTransactionDb();
				transaction.setTs(ts);
				transaction.setShopCustomer(shopCustomer);
				transaction.setShopOwner(shopOwner);
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
	public Collection<PlayerGroup> getPlayerGroups(Date from, Date to, ShopTransactionType transactionType) throws SQLException {
		Map<String, PlayerGroup> playerGroups = new HashMap<String, PlayerGroup>();

		String sql;
		List<String> where = new ArrayList<String>();
		//@formatter:off
		switch (transactionType){
		case MY_SHOP:
			sql =
			"SELECT t.amount, t.quantity, t.player, p.name AS playerName, p.first_seen, p.last_seen, i.name AS itemName " + 
			"FROM transactions t " +
			"INNER JOIN items i ON t.item = i.id " +
			"INNER JOIN players p ON t.player = p.id ";
			where.add("t.player IS NOT NULL");
			break;
		case OTHER_SHOPS:
			sql =
			"SELECT t.amount, t.quantity, t.shop_owner, p.name AS shopOwnerName, p.first_seen, p.last_seen, i.name AS itemName " + 
			"FROM transactions t " +
			"INNER JOIN items i ON t.item = i.id " +
			"INNER JOIN players p ON t.shop_owner = p.id ";
			where.add("t.shop_owner IS NOT NULL");
			break;
		default:
			sql =
			"SELECT t.amount, t.quantity, t.player, t.shop_owner, i.name AS itemName " + 
			"FROM transactions t " +
			"INNER JOIN items i ON t.item = i.id ";
			break;
		}
		//@formatter:on

		if (from != null) {
			where.add("ts >= ?");
		}
		if (to != null) {
			where.add("ts < ?");
		}

		if (!where.isEmpty()) {
			sql += " WHERE " + StringUtils.join(where, " AND ");
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
			while (rs.next()) {
				String playerName;
				Integer playerId;
				Date firstSeen, lastSeen;
				switch (transactionType) {
				case MY_SHOP:
					playerName = rs.getString("playerName");
					playerId = rs.getInt("player");
					firstSeen = toDate(rs.getTimestamp("first_seen"));
					lastSeen = toDate(rs.getTimestamp("last_seen"));
					break;
				case OTHER_SHOPS:
					playerName = rs.getString("shopOwnerName");
					playerId = rs.getInt("shop_owner");
					firstSeen = toDate(rs.getTimestamp("first_seen"));
					lastSeen = toDate(rs.getTimestamp("last_seen"));
					break;
				default:
					playerId = (Integer) rs.getObject("player");
					if (playerId == null) {
						playerId = (Integer) rs.getObject("shop_owner");
					}

					Player player = getPlayer(playerId);
					playerName = player.getName();
					firstSeen = player.getFirstSeen();
					lastSeen = player.getLastSeen();
					break;
				}

				PlayerGroup playerGroup = playerGroups.get(playerName);
				if (playerGroup == null) {
					playerGroup = new PlayerGroup();

					Player player = new Player();
					player.setId(playerId);
					player.setName(playerName);
					player.setFirstSeen(firstSeen);
					player.setLastSeen(lastSeen);
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

	private String getPlayerName(Integer id) throws SQLException {
		PreparedStatement stmt = stmt("SELECT name FROM players WHERE id = ?");
		try {
			stmt.setInt(1, id);
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getString(1) : null;
		} finally {
			closeStatements(stmt);
		}
	}

	private Player getPlayer(Integer id) throws SQLException {
		PreparedStatement stmt = stmt("SELECT name, first_seen, last_seen FROM players WHERE id = ?");
		try {
			stmt.setInt(1, id);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}

			Player player = new Player();
			player.setId(id);
			player.setName(rs.getString("name"));
			player.setFirstSeen(toDate(rs.getTimestamp("first_seen")));
			player.setLastSeen(toDate(rs.getTimestamp("last_seen")));
			return player;
		} finally {
			closeStatements(stmt);
		}
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
				inv.setLowInStockThreshold(rs.getInt("low_threshold"));
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
	public int upsertInventory(String item, Integer quantity, boolean add) throws SQLException {
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
			return stmt2.execute(conn);
		}

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

		return invId;
	}

	@Override
	public void updateInventoryLowThreshold(String item, int threshold) throws SQLException {
		int itemId = selsertItem(item);

		PreparedStatement stmt = stmt("UPDATE inventory SET low_threshold = ? WHERE item = ?");
		try {
			stmt.setInt(1, threshold);
			stmt.setInt(2, itemId);
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
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
	public boolean isBonusFeeTransaction(RupeeTransaction transaction) {
		return bonusFeeColumnNames.containsKey(transaction.getClass());
	}

	@Override
	public void updateBonusFeeTotals(Map<Class<? extends RupeeTransaction>, MutableInt> totals) throws SQLException {
		if (totals.isEmpty()) {
			return;
		}

		List<Integer> values = new ArrayList<Integer>();
		List<String> assignments = new ArrayList<String>();
		for (Map.Entry<Class<? extends RupeeTransaction>, String> entry : bonusFeeColumnNames.entrySet()) {
			MutableInt value = totals.get(entry.getKey());
			if (value == null) {
				continue;
			}

			values.add(value.getValue());

			String columnName = entry.getValue();
			assignments.add(columnName + " = " + columnName + " + ?");
		}

		PreparedStatement stmt = stmt("UPDATE bonuses_fees SET " + StringUtils.join(assignments, ", "));
		try {
			int i = 1;
			for (Integer value : values) {
				stmt.setInt(i++, value);
			}
			stmt.executeUpdate();
		} finally {
			closeStatements(stmt);
		}
	}

	@Override
	public void updateBonusesFeesLatestTransactionDate(Date newLatest) throws SQLException {
		Date oldLatest = null;
		PreparedStatement selectStmt = stmt("SELECT latest_transaction_ts FROM bonuses_fees");
		try {
			ResultSet rs = selectStmt.executeQuery();
			if (rs.next()) {
				Timestamp ts = rs.getTimestamp(1);
				oldLatest = toDate(ts);
			}
		} finally {
			closeStatements(selectStmt);
		}

		if (oldLatest != null && newLatest.before(oldLatest)) {
			return;
		}

		PreparedStatement updateStmt = stmt("UPDATE bonuses_fees SET latest_transaction_ts = ?");
		try {
			updateStmt.setTimestamp(1, toTimestamp(newLatest));
			updateStmt.executeUpdate();
		} finally {
			closeStatements(updateStmt);
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
			bonusesFees.setMail(rs.getInt("mail"));
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

		//@formatter:off
		String sql =
		"SELECT t.ts, t.amount, i.name AS item " +
		"FROM transactions t " +
		"INNER JOIN items i ON t.item = i.id " +
		"WHERE t.player IS NOT NULL ";
		//@formatter:off
		
		if (from != null) {
			sql += "AND t.ts >= ? ";
		}
		if (to != null) {
			sql += "AND t.ts < ? ";
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
			sb.append('?');
		}

		sb.append(')');
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