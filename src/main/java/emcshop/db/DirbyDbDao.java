package emcshop.db;

import static emcshop.util.TimeUtils.toLocalDateTime;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
	public static final int schemaVersion = 50;

	protected Connection conn;
	protected String jdbcUrl;
	private Map<Integer, LocalDateTime[]> firstLastSeenDates = new HashMap<>();

	private final Map<Class<? extends RupeeTransaction>, String> bonusFeeColumnNames = ImmutableMap.<Class<? extends RupeeTransaction>, String>builder() //@formatter:off
		.put(DailySigninBonus.class, "sign_in")
		.put(EggifyFee.class, "eggify")
		.put(HorseSummonFee.class, "horse")
		.put(LockTransaction.class, "lock")
		.put(MailFee.class, "mail")
		.put(VaultFee.class, "vault")
		.put(VoteBonus.class, "vote")
	.build(); //@formatter:on

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
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.fine("Shutting down the database...");
			try {
				close();
			} catch (SQLException e) {
				logger.log(Level.SEVERE, "Error stopping database.", e);
			}
		}));

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
			throw new SQLException("Database version is newer than DAO version.");
		}

		if (listener != null) {
			listener.onMigrate(curVersion, latestVersion);
		}

		logger.info("Database schema out of date.  Upgrading from version " + curVersion + " to " + latestVersion + ".");
		String sql = null;
		try (Statement statement = conn.createStatement()) {
			while (curVersion < latestVersion) {
				logger.info("Performing schema update from version " + curVersion + " to " + (curVersion + 1) + ".");

				try (SQLStatementReader in = new SQLStatementReader(getMigrationScript(curVersion))) {
					while ((sql = in.readStatement()) != null) {
						statement.execute(sql);
					}
					sql = null;
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
		try (PreparedStatement stmt = stmt("SELECT db_schema_version FROM meta")) {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	@Override
	public void upsertDbVersion(int version) throws SQLException {
		try (PreparedStatement stmt = stmt("UPDATE meta SET db_schema_version = ?")) {
			stmt.setInt(1, version);
			int updated = stmt.executeUpdate();
			if (updated > 0) {
				return;
			}
		}

		InsertStatement insertStmt = new InsertStatement("meta");
		insertStmt.setInt("db_schema_version", version);
		insertStmt.execute(conn);
	}

	@Override
	public int selectRupeeBalanceMeta() throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT rupee_balance FROM meta")) {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	@Override
	public Integer selectRupeeBalance() throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT rupee_balance FROM update_log ORDER BY ts DESC")) {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : null;
		}
	}

	@Override
	public Player selsertPlayer(String name) throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT * FROM players WHERE Lower(name) = Lower(?)")) {
			stmt.setString(1, name);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				Player player = new Player();
				player.setId(rs.getInt("id"));
				player.setName(rs.getString("name"));
				player.setFirstSeen(toLocalDateTime(rs.getTimestamp("first_seen")));
				player.setLastSeen(toLocalDateTime(rs.getTimestamp("last_seen")));
				return player;
			}
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
		try (PreparedStatement stmt = stmt("SELECT id FROM items WHERE Lower(name) = Lower(?)")) {
			stmt.setString(1, name);
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getInt(1) : null;
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
		try (PreparedStatement stmt = stmt("SELECT name FROM items ORDER BY Lower(name)")) {
			ResultSet rs = stmt.executeQuery();
			List<String> names = new ArrayList<>();
			while (rs.next()) {
				names.add(rs.getString("name"));
			}
			return names;
		}
	}

	@Override
	public void updateItemNamesAndAliases() throws SQLException {
		LocalDateTime now = LocalDateTime.now();
		int dbVersion = selectDbVersion();

		Multimap<String, ItemIndex.EmcName> mappings = ItemIndex.instance().getDisplayNameToEmcNamesMapping();
		for (Map.Entry<String, ItemIndex.EmcName> mapping : mappings.entries()) {
			String officialName = mapping.getKey();
			ItemIndex.EmcName emcName = mapping.getValue();
			if (aliasShouldBeSkipped(officialName, emcName)) {
				continue;
			}


			Integer aliasId = getItemId(emcName.getAlias());
			if (aliasId == null) {
				//alias isn't used anywhere
				continue;
			}

			Integer officialId = selsertItem(officialName);

			updateTransactionItemName(aliasId, officialId, emcName.getTimeFrom(), emcName.getTimeTo());
			if (dbVersion >= 7 && now.isBefore(emcName.getTimeTo()) && now.compareTo(emcName.getTimeFrom()) >= 0) {
				updateInventoryItem(Arrays.asList(aliasId), officialId);
			}
		}

		deleteUnusedItems();
	}

	private boolean aliasShouldBeSkipped(String officialName, ItemIndex.EmcName emcName) {
		/*
		 * Grass Blocks used to be called "Grass" before 1.13.
		 * Since "Grass" is a valid item that existed before 1.13, we do not
		 * want to rename all existing "Grass" transactions that took place
		 * before 1.13 to "Grass Block" (which is how the alias is defined in
		 * items.xml), so do not process this alias.
		 */
		if ("Grass Block".equals(officialName) && "Grass".equals(emcName.getAlias())) {
			return true;
		}

		return false;
	}

	private void updateItemName(Integer id, String newName) throws SQLException {
		try (PreparedStatement stmt = stmt("UPDATE items SET name = ? WHERE id = ?")) {
			stmt.setString(1, newName);
			stmt.setInt(2, id);
			stmt.executeUpdate();
		}
	}

	@Override
	public void updateItemsWhoseOldNamesAreUsedByExistingItems(List<String> oldNames, List<String> newNames, LocalDateTime date) throws SQLException {
		if (oldNames.size() != newNames.size()) {
			throw new IllegalArgumentException("oldNames and newNames lists must be the same size.");
		}

		for (int i = 0; i < oldNames.size(); i++) {
			String oldName = oldNames.get(i);
			String newName = newNames.get(i);
			int oldNameId = selsertItem(oldName); //e.g. smooth sandstone
			int newNameId = selsertItem(newName); //e.g. cut sandstone

			try (PreparedStatement stmt = stmt("UPDATE transactions SET item = ? WHERE item = ? AND ts < ?")) {
				stmt.setInt(1, newNameId);
				stmt.setInt(2, oldNameId);
				stmt.setTimestamp(3, toTimestamp(date));
				stmt.executeUpdate();
			}
		}
	}

	@Override
	public void removeDuplicateItems() throws SQLException {
		//get the ID(s) of each item
		ListMultimap<String, Integer> itemIds = ArrayListMultimap.create();
		try (PreparedStatement stmt = stmt("SELECT id, name FROM items")) {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Integer id = rs.getInt("id");
				String name = rs.getString("name").toLowerCase();

				itemIds.put(name, id);
			}
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

		try (PreparedStatement stmt = stmt("DELETE FROM items WHERE id " + in(itemIds.size()))) {
			int i = 1;
			for (Integer id : itemIds) {
				stmt.setInt(i++, id);
			}
			stmt.executeUpdate();
		}
	}

	private void deleteUnusedItems() throws SQLException {
		Set<Integer> nonStandardItemIds = new HashSet<>();
		Set<String> standardItemNames = ItemIndex.instance().getItemNames().stream() //@formatter:off
			.map(String::toLowerCase)
		.collect(Collectors.toSet()); //@formatter:on
		try (PreparedStatement stmt = stmt("SELECT id, name FROM items")) {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				String name = rs.getString("name");
				if (!standardItemNames.contains(name.toLowerCase())) {
					nonStandardItemIds.add(rs.getInt("id"));
				}
			}
		}

		Set<Integer> nonStandardItemIdsThatAreNotBeingUsed = new HashSet<>(nonStandardItemIds);
		try (PreparedStatement stmt = stmt("SELECT DISTINCT item FROM transactions")) {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				nonStandardItemIdsThatAreNotBeingUsed.remove(rs.getInt("item"));
			}
		}
		try (PreparedStatement stmt = stmt("SELECT DISTINCT item FROM inventory")) {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				nonStandardItemIdsThatAreNotBeingUsed.remove(rs.getInt("item"));
			}
		}

		deleteItems(nonStandardItemIdsThatAreNotBeingUsed);
	}

	@Override
	public void populateItemsTable() throws SQLException {
		//get all existing item names
		Set<String> existingItemNames = new HashSet<>();
		for (String itemName : getItemNames()) {
			existingItemNames.add(itemName.toLowerCase());
		}

		//find all items names that aren't in the database
		List<String> toInsert = new ArrayList<>();
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
	public LocalDateTime getEarliestTransactionDate() throws SQLException {
		List<LocalDateTime> dates = new ArrayList<>();
		String[] tables = { "transactions", "payment_transactions" };
		for (String table : tables) {
			try (PreparedStatement selectStmt = stmt("SELECT Min(ts) FROM " + table)) {
				ResultSet rs = selectStmt.executeQuery();
				if (!rs.next()) {
					continue;
				}

				Timestamp ts = rs.getTimestamp(1);
				if (ts == null) {
					continue;
				}

				dates.add(toLocalDateTime(ts));
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

		try (PreparedStatement stmt = stmt("UPDATE transactions SET item = ? WHERE item " + in(oldItemIds.size()))) {
			int i = 1;
			stmt.setInt(i++, newItemId);
			for (Integer oldItemId : oldItemIds) {
				stmt.setInt(i++, oldItemId);
			}
			stmt.executeUpdate();
		}
	}

	/**
	 * Changes the item that certain transactions are assigned to.
	 * @param oldItemId only update transactions whose item ID matches this
	 * @param newItemId the new item ID to assign to the matched transactions
	 * @param timeFrom only update transactions whose timestamp is &gt;= this value
	 * @param timeTo only update transactions whose timestamp is &lt; this value
	 */
	private void updateTransactionItemName(int oldItemId, int newItemId, LocalDateTime timeFrom, LocalDateTime timeTo) throws SQLException {
		String sql = "UPDATE transactions SET item = ? WHERE item = ?";
		if (timeFrom != LocalDateTime.MIN && timeTo == LocalDateTime.MAX) {
			sql += " AND ts >= ?";
		} else if (timeFrom == LocalDateTime.MIN && timeTo != LocalDateTime.MAX) {
			sql += " AND ts < ?";
		} else if (timeFrom != LocalDateTime.MIN && timeTo != LocalDateTime.MAX) {
			sql += " AND ts < ? AND ts >= ?";
		}
		try (PreparedStatement stmt = stmt(sql)) {
			int i = 1;
			stmt.setInt(i++, newItemId);
			stmt.setInt(i++, oldItemId);
			if (timeTo != LocalDateTime.MAX) {
				stmt.setTimestamp(i++, toTimestamp(timeTo));
			}
			if (timeFrom != LocalDateTime.MIN) {
				stmt.setTimestamp(i++, toTimestamp(timeFrom));
			}
			stmt.executeUpdate();
		}
	}

	@Override
	public void insertTransaction(ShopTransactionDb transaction, boolean updateInventory) throws SQLException {
		String playerName = transaction.getShopCustomer();
		Player player = (playerName == null) ? null : selsertPlayer(playerName);

		String ownerName = transaction.getShopOwner();
		Player owner = (ownerName == null) ? null : selsertPlayer(ownerName);

		Integer itemId = selsertItem(transaction.getItem());
		LocalDateTime ts = transaction.getTs();

		//keep track of the first/last seen dates so they can be updated (in "commit()")
		//don't record this if this is not a transaction from the player's own shop
		if (player != null) {
			LocalDateTime dates[] = firstLastSeenDates.computeIfAbsent(player.getId(), k -> new LocalDateTime[] { player.getFirstSeen(), player.getLastSeen() });

			LocalDateTime earliest = dates[0];
			if (earliest == null || ts.isBefore(earliest)) {
				dates[0] = ts;
			}

			LocalDateTime latest = dates[1];
			if (latest == null || ts.isAfter(latest)) {
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
		try (PreparedStatement stmt = stmt("DELETE FROM payment_transactions WHERE id = ?")) {
			stmt.setInt(1, transaction.getId());
			stmt.executeUpdate();
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
			try (PreparedStatement stmt = stmt("UPDATE payment_transactions SET amount = ?, balance = ? WHERE id = ?")) {
				stmt.setInt(1, transaction.getAmount());
				stmt.setInt(2, transaction.getBalance());
				stmt.setInt(3, id);
				stmt.executeUpdate();
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
	public LocalDateTime getLatestTransactionDate() throws SQLException {
		LocalDateTime latest = null;
		String queries[] = { "SELECT Max(ts) FROM transactions", "SELECT Max(ts) FROM payment_transactions", "SELECT latest_transaction_ts FROM bonuses_fees" };
		for (String query : queries) {
			LocalDateTime date;
			try (PreparedStatement stmt = stmt(query)) {
				ResultSet rs = stmt.executeQuery();
				if (!rs.next()) {
					continue;
				}

				Timestamp ts = rs.getTimestamp(1);
				if (ts == null) {
					continue;
				}

				date = toLocalDateTime(ts);
			}

			if (latest == null || date.isAfter(latest)) {
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

		try (PreparedStatement selectStmt = stmt(sql)) {
			ResultSet rs = selectStmt.executeQuery();
			List<PaymentTransactionDb> transactions = new ArrayList<>();
			while (rs.next()) {
				PaymentTransactionDb transaction = new PaymentTransactionDb(rs);
				transactions.add(transaction);
			}
			return transactions;
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

		try (PreparedStatement selectStmt = stmt(sql)) {
			ResultSet rs = selectStmt.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	@Override
	public void ignorePaymentTransaction(Integer id) throws SQLException {
		try (PreparedStatement stmt = stmt("UPDATE payment_transactions SET ignore = ? WHERE id = ?")) {
			stmt.setBoolean(1, true);
			stmt.setInt(2, id);
			stmt.execute();
		}
	}

	@Override
	public void assignPaymentTransaction(Integer paymentId, Integer transactionId) throws SQLException {
		try (PreparedStatement stmt = stmt("UPDATE payment_transactions SET \"transaction\" = ? WHERE id = ?")) {
			stmt.setInt(1, transactionId);
			stmt.setInt(2, paymentId);
			stmt.execute();
		}
	}

	@Override
	public Collection<ItemGroup> getItemGroups(LocalDateTime from, LocalDateTime to, ShopTransactionType transactionType) throws SQLException {
		Map<String, ItemGroup> itemGroups = new HashMap<>();

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

		try (PreparedStatement stmt = stmt(sql)) {
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

		try (PreparedStatement stmt = stmt(sql)) {
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
		}

		return itemGroups.values();
	}

	@Override
	public List<ShopTransactionDb> getTransactionsByDate(LocalDateTime from, LocalDateTime to, ShopTransactionType transactionType) throws SQLException {
		String sql;
		List<String> where = new ArrayList<>();
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
			sql += " WHERE " + String.join(" AND ", where);
		}
		sql += " ORDER BY t.ts";

		List<ShopTransactionDb> transactions = new ArrayList<>();
		Map<String, ShopTransactionDb> lastTransactionByItem = new HashMap<>();
		Map<ShopTransactionDb, LocalDateTime> dateOfLastTransaction = new HashMap<>();
		try (PreparedStatement stmt = stmt(sql)) {
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, toTimestamp(from));
			}
			if (to != null) {
				stmt.setTimestamp(index++, toTimestamp(to));
			}
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				LocalDateTime ts = toLocalDateTime(rs.getTimestamp("ts"));
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
					/*
					 * If the transactions occurred within 2 minutes of the last
					 * one, then consider it part of the same, consolidated
					 * transaction.
					 * 
					 * Note: "diff.toMinutes() <= 2" will return true for values
					 * such as 2:10.
					 */
					Duration diff = Duration.between(dateOfLastTransaction.get(transaction), ts);
					if (diff.toMinutes() < 2) {
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
		}

		return transactions;
	}

	@Override
	public Collection<PlayerGroup> getPlayerGroups(LocalDateTime from, LocalDateTime to, ShopTransactionType transactionType) throws SQLException {
		Map<String, PlayerGroup> playerGroups = new HashMap<>();

		String sql;
		List<String> where = new ArrayList<>();
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
			sql += " WHERE " + String.join(" AND ", where);
		}

		try (PreparedStatement stmt = stmt(sql)) {
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
				LocalDateTime firstSeen, lastSeen;
				switch (transactionType) {
				case MY_SHOP:
					playerName = rs.getString("playerName");
					playerId = rs.getInt("player");
					firstSeen = toLocalDateTime(rs.getTimestamp("first_seen"));
					lastSeen = toLocalDateTime(rs.getTimestamp("last_seen"));
					break;
				case OTHER_SHOPS:
					playerName = rs.getString("shopOwnerName");
					playerId = rs.getInt("shop_owner");
					firstSeen = toLocalDateTime(rs.getTimestamp("first_seen"));
					lastSeen = toLocalDateTime(rs.getTimestamp("last_seen"));
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
		}

		return playerGroups.values();
	}

	private String getPlayerName(Integer id) throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT name FROM players WHERE id = ?")) {
			stmt.setInt(1, id);
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? rs.getString(1) : null;
		}
	}

	private Player getPlayer(Integer id) throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT name, first_seen, last_seen FROM players WHERE id = ?")) {
			stmt.setInt(1, id);
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}

			Player player = new Player();
			player.setId(id);
			player.setName(rs.getString("name"));
			player.setFirstSeen(toLocalDateTime(rs.getTimestamp("first_seen")));
			player.setLastSeen(toLocalDateTime(rs.getTimestamp("last_seen")));
			return player;
		}
	}

	@Override
	public Collection<Inventory> getInventory() throws SQLException {
		Collection<Inventory> inventory = new ArrayList<>();

		try (PreparedStatement stmt = stmt("SELECT inventory.*, items.name AS item_name FROM inventory INNER JOIN items ON inventory.item = items.id")) {
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
		}

		return inventory;
	}

	private void addToInventory(Integer itemId, Integer quantityToAdd) throws SQLException {
		try (PreparedStatement stmt = stmt("UPDATE inventory SET quantity = quantity + ? WHERE item = ?")) {
			stmt.setInt(1, quantityToAdd);
			stmt.setInt(2, itemId);
			stmt.executeUpdate();
		}
	}

	private void updateInventoryItem(List<Integer> oldItemIds, int newItemId) throws SQLException {
		if (oldItemIds.isEmpty()) {
			return;
		}

		List<Integer> allIds = new ArrayList<>(oldItemIds);
		allIds.add(newItemId);

		int totalQuantity = 0;
		try (PreparedStatement stmt = stmt("SELECT Sum(quantity) FROM inventory WHERE item " + in(allIds.size()))) {
			int i = 1;
			for (Integer id : allIds) {
				stmt.setInt(i++, id);
			}

			ResultSet rs = stmt.executeQuery();
			totalQuantity = rs.next() ? rs.getInt(1) : 0;
		}

		//update the quantity
		try (PreparedStatement stmt = stmt("UPDATE inventory SET quantity = ? WHERE item = ?")) {
			stmt.setInt(1, totalQuantity);
			stmt.setInt(2, newItemId);
			stmt.executeUpdate();
		}

		//delete the old entries
		try (PreparedStatement stmt = stmt("DELETE FROM inventory WHERE item " + in(oldItemIds.size()))) {
			int i = 1;
			for (Integer id : oldItemIds) {
				stmt.setInt(i++, id);
			}
			stmt.executeUpdate();
		}
	}

	@Override
	public void upsertInventory(Inventory inventory) throws SQLException {
		upsertInventory(inventory.getItem(), inventory.getQuantity(), false);
	}

	@Override
	public int upsertInventory(String item, Integer quantity, boolean add) throws SQLException {
		int itemId = selsertItem(item);

		Integer invId;
		try (PreparedStatement stmt = stmt("SELECT id FROM inventory WHERE item = ?")) {
			stmt.setInt(1, itemId);
			ResultSet rs = stmt.executeQuery();
			invId = rs.next() ? rs.getInt("id") : null;
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

		try (PreparedStatement stmt = stmt(sql)) {
			stmt.setInt(1, quantity);
			stmt.setInt(2, invId);
			stmt.executeUpdate();
		}

		return invId;
	}

	@Override
	public void updateInventoryLowThreshold(String item, int threshold) throws SQLException {
		int itemId = selsertItem(item);

		try (PreparedStatement stmt = stmt("UPDATE inventory SET low_threshold = ? WHERE item = ?")) {
			stmt.setInt(1, threshold);
			stmt.setInt(2, itemId);
			stmt.executeUpdate();
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
		try (PreparedStatement stmt = stmt(sb.toString())) {
			int i = 1;
			for (Integer id : ids) {
				stmt.setInt(i++, id);
			}
			stmt.executeUpdate();
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

		List<Integer> values = new ArrayList<>();
		List<String> assignments = new ArrayList<>();
		for (Map.Entry<Class<? extends RupeeTransaction>, String> entry : bonusFeeColumnNames.entrySet()) {
			MutableInt value = totals.get(entry.getKey());
			if (value == null) {
				continue;
			}

			values.add(value.getValue());

			String columnName = entry.getValue();
			assignments.add(columnName + " = " + columnName + " + ?");
		}

		try (PreparedStatement stmt = stmt("UPDATE bonuses_fees SET " + String.join(", ", assignments))) {
			int i = 1;
			for (Integer value : values) {
				stmt.setInt(i++, value);
			}
			stmt.executeUpdate();
		}
	}

	@Override
	public void updateBonusesFeesLatestTransactionDate(LocalDateTime newLatest) throws SQLException {
		LocalDateTime oldLatest = null;
		try (PreparedStatement stmt = stmt("SELECT latest_transaction_ts FROM bonuses_fees")) {
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				Timestamp ts = rs.getTimestamp(1);
				oldLatest = toLocalDateTime(ts);
			}
		}

		if (oldLatest != null && newLatest.isBefore(oldLatest)) {
			return;
		}

		try (PreparedStatement stmt = stmt("UPDATE bonuses_fees SET latest_transaction_ts = ?")) {
			stmt.setTimestamp(1, toTimestamp(newLatest));
			stmt.executeUpdate();
		}
	}

	@Override
	public BonusFee getBonusesFees() throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT * FROM bonuses_fees")) {
			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				return null;
			}

			BonusFee bonusesFees = new BonusFee();
			bonusesFees.setSince(toLocalDateTime(rs.getTimestamp("since")));
			bonusesFees.setEggify(rs.getInt("eggify"));
			bonusesFees.setHorse(rs.getInt("horse"));
			bonusesFees.setLock(rs.getInt("lock"));
			bonusesFees.setSignIn(rs.getInt("sign_in"));
			bonusesFees.setVault(rs.getInt("vault"));
			bonusesFees.setVote(rs.getInt("vote"));
			bonusesFees.setMail(rs.getInt("mail"));
			bonusesFees.setHighestBalance(rs.getInt("highest_balance"));
			bonusesFees.setHighestBalanceTs(toLocalDateTime(rs.getTimestamp("highest_balance_ts")));

			return bonusesFees;
		}
	}

	@Override
	public void updateBonusesFeesSince(LocalDateTime since) throws SQLException {
		try (PreparedStatement stmt = stmt("UPDATE bonuses_fees SET since = ? WHERE since IS NULL")) {
			stmt.setTimestamp(1, toTimestamp(since));
			stmt.executeUpdate();
		}
	}

	@Override
	public void updateBonusesFeesHighestBalance(RupeeTransaction transaction) throws SQLException {
		int highestBalance = 0;
		try (PreparedStatement stmt = stmt("SELECT highest_balance FROM bonuses_fees")) {
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				highestBalance = rs.getInt(1);
			}
		}

		if (transaction.getBalance() <= highestBalance) {
			return;
		}

		try (PreparedStatement stmt = stmt("UPDATE bonuses_fees SET highest_balance = ?, highest_balance_ts = ?")) {
			stmt.setInt(1, transaction.getBalance());
			stmt.setTimestamp(2, toTimestamp(transaction.getTs()));
			stmt.executeUpdate();
		}
	}

	@Override
	public Map<LocalDate, Profits> getProfitsByDay(LocalDate from, LocalDate to) throws SQLException {
		return getProfits(from, to, true);
	}

	@Override
	public Map<LocalDate, Profits> getProfitsByMonth(LocalDate from, LocalDate to) throws SQLException {
		return getProfits(from, to, false);
	}

	private Map<LocalDate, Profits> getProfits(LocalDate from, LocalDate to, boolean byDay) throws SQLException {
		Map<LocalDate, Profits> profits = new LinkedHashMap<>();

		//@formatter:off
		String sql =
		"SELECT t.ts, t.amount, t.balance, t.player, i.name AS item " +
		"FROM transactions t " +
		"INNER JOIN items i ON t.item = i.id ";
		//@formatter:on

		if (from != null && to != null) {
			sql += "WHERE t.ts >= ? AND t.ts < ?";
		} else if (from != null) {
			sql += "WHERE t.ts >= ?";
		} else if (to != null) {
			sql += "WHERE t.ts < ?";
		}

		try (PreparedStatement stmt = stmt(sql)) {
			int index = 1;
			if (from != null) {
				stmt.setTimestamp(index++, toTimestamp(from));
			}
			if (to != null) {
				stmt.setTimestamp(index++, toTimestamp(to));
			}

			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				LocalDate date = toLocalDateTime(rs.getTimestamp("ts")).toLocalDate();
				if (!byDay) {
					date = date.withDayOfMonth(1);
				}

				Profits profit = profits.get(date);
				if (profit == null) {
					profit = new Profits();
					profits.put(date, profit);
				}

				boolean isShopTransaction = (rs.getObject("player") != null);
				if (isShopTransaction) {
					int amount = rs.getInt("amount");
					String item = rs.getString("item");
					profit.addTransaction(item, amount);
				}

				int balance = rs.getInt("balance");
				if (balance > profit.getBalance()) {
					profit.setBalance(balance);
				}
			}
		}

		return profits;
	}

	@Override
	public void wipe() throws SQLException {
		logger.info("Wiping transactions...");

		try (Statement stmt = conn.createStatement()) {
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
		}
	}

	@Override
	public void commit() throws SQLException {
		//update first/last seen dates if transactions were inserted
		for (Map.Entry<Integer, LocalDateTime[]> entry : firstLastSeenDates.entrySet()) {
			Integer playerId = entry.getKey();
			LocalDateTime firstSeen = entry.getValue()[0];
			LocalDateTime lastSeen = entry.getValue()[1];

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
			/*
			 * The exception is caught here instead of being thrown because
			 * every time I catch it when calling this method, I just log it and
			 * continue on. Plus, the exception is unlikely to be thrown anyway.
			 */
			logger.log(Level.WARNING, "Problem rolling back transaction.", e);
		}
	}

	@Override
	public void close() throws SQLException {
		firstLastSeenDates.clear();

		try {
			logger.info("Closing database.");
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		} catch (SQLException e) {
			if (e.getErrorCode() == 50000 && "XJ015".equals(e.getSQLState())) {
				//we got the expected exception
			} else if (e.getErrorCode() == 45000 && "08006".equals(e.getSQLState())) {
				//we got the expected exception for single database shutdown
			} else {
				//if the error code or SQLState is different, the shutdown operation may have failed
				throw e;
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

		try (PreparedStatement stmt = stmt(sql)) {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				int player = rs.getInt("player");
				LocalDateTime firstSeen = toLocalDateTime(rs.getTimestamp("firstSeen"));
				LocalDateTime lastSeen = toLocalDateTime(rs.getTimestamp("lastSeen"));
				updateFirstLastSeen(player, firstSeen, lastSeen);
			}
		}
	}

	@Override
	public void findHighestBalance() throws SQLException {
		int highestBalance = 0;
		Timestamp highestBalanceTs = null;
		try (PreparedStatement stmt = stmt("SELECT ts, balance FROM transactions")) {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				int balance = rs.getInt("balance");
				if (balance > highestBalance) {
					highestBalance = balance;
					highestBalanceTs = rs.getTimestamp("ts");
				}
			}
		}

		try (PreparedStatement stmt = stmt("UPDATE bonuses_fees SET highest_balance = ?, highest_balance_ts = ?")) {
			stmt.setInt(1, highestBalance);
			stmt.setTimestamp(2, highestBalanceTs);
			stmt.executeUpdate();
		}
	}

	/**
	 * Updates a player's first/last seen dates.
	 * @param playerId the player ID
	 * @param firstSeen the "first seen" date
	 * @param lastSeen the "last seen" date
	 * @throws SQLException
	 */
	private void updateFirstLastSeen(Integer playerId, LocalDateTime firstSeen, LocalDateTime lastSeen) throws SQLException {
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

		try (PreparedStatement stmt = stmt(sql)) {
			int index = 1;
			if (firstSeen != null) {
				stmt.setTimestamp(index++, toTimestamp(firstSeen));
			}
			if (lastSeen != null) {
				stmt.setTimestamp(index++, toTimestamp(lastSeen));
			}
			stmt.setInt(index++, playerId);

			stmt.executeUpdate();
		}
	}

	@Override
	public void insertUpdateLog(LocalDateTime ts, Integer rupeeBalance, int transactionCount, int paymentTransactionCount, int bonusFeeTransactionCount, Duration timeTaken) throws SQLException {
		InsertStatement stmt = new InsertStatement("update_log");
		stmt.setTimestamp("ts", ts);
		if (rupeeBalance == null) {
			rupeeBalance = 0;
		}
		stmt.setInt("rupee_balance", rupeeBalance);
		stmt.setInt("transaction_count", transactionCount);
		stmt.setInt("payment_transaction_count", paymentTransactionCount);
		stmt.setInt("bonus_fee_transaction_count", bonusFeeTransactionCount);
		stmt.setInt("time_taken", (int) timeTaken.toMillis());
		stmt.execute(conn);
	}

	@Override
	public LocalDateTime getLatestUpdateDate() throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT Max(ts) FROM update_log")) {
			ResultSet rs = stmt.executeQuery();
			return rs.next() ? toLocalDateTime(rs.getTimestamp(1)) : null;
		}
	}

	@Override
	public LocalDateTime getSecondLatestUpdateDate() throws SQLException {
		try (PreparedStatement stmt = stmt("SELECT ts FROM update_log ORDER BY ts DESC")) {
			ResultSet rs = stmt.executeQuery();
			int count = 0;
			while (rs.next()) {
				count++;
				if (count == 2) {
					return toLocalDateTime(rs.getTimestamp(1));
				}
			}
		}
		return null;
	}

	/**
	 * Converts a {@link LocalDate} to a {@link Timestamp}.
	 * @param date the date
	 * @return the timestamp
	 */
	protected Timestamp toTimestamp(LocalDate date) {
		return (date == null) ? null : Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	/**
	 * Converts a {@link LocalDateTime} to a {@link Timestamp}.
	 * @param date the date
	 * @return the timestamp
	 */
	protected Timestamp toTimestamp(LocalDateTime date) {
		return (date == null) ? null : Timestamp.from(date.atZone(ZoneId.systemDefault()).toInstant());
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
			Class.forName(EmbeddedDriver.class.getName()).getDeclaredConstructor().newInstance();
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
		String schemaFileName = "schema.sql";
		try (
			Statement statement = conn.createStatement();
			SQLStatementReader in = new SQLStatementReader(new InputStreamReader(ClasspathUtils.getResourceAsStream(schemaFileName, getClass())))
		) {
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
