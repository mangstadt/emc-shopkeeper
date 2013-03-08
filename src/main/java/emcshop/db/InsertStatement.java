package emcshop.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents an INSERT statement.
 * @author Michael Angstadt
 */
public class InsertStatement {
	private final String tableName;
	private final Set<String> columnNames = new LinkedHashSet<String>();
	private final List<Map<String, SqlColumn>> rows = new ArrayList<Map<String, SqlColumn>>();
	private Map<String, SqlColumn> currentRow;

	/**
	 * @param tableName the name of the table
	 */
	public InsertStatement(String tableName) {
		this.tableName = tableName;
		nextRow();
	}

	/**
	 * Adds column with a string value to the statement.
	 * @param columnName the column name
	 * @param value the column value
	 * @return this
	 */
	public InsertStatement setString(String columnName, String value) {
		return set(columnName, value, Types.VARCHAR);
	}

	/**
	 * Adds column with an integer value to the statement.
	 * @param columnName the column name
	 * @param value the column value
	 * @return this
	 */
	public InsertStatement setInt(String columnName, Integer value) {
		return set(columnName, value, Types.INTEGER);
	}

	/**
	 * Adds column with a date value to the statement.
	 * @param columnName the column name
	 * @param value the column value
	 * @return this
	 */
	public InsertStatement setDate(String columnName, Date value) {
		return set(columnName, value, Types.DATE);
	}

	/**
	 * Adds column with a timestamp value to the statement.
	 * @param columnName the column name
	 * @param value the column value
	 * @return this
	 */
	public InsertStatement setTimestamp(String columnName, Date value) {
		return set(columnName, value, Types.TIMESTAMP);
	}

	/**
	 * Adds a column
	 * @param columnName the column name
	 * @param value the column value
	 * @param sqlType the data type
	 * @return this
	 */
	private InsertStatement set(String columnName, Object value, int sqlType) {
		String columnNameLowerCase = columnName.toLowerCase();
		columnNames.add(columnNameLowerCase);
		currentRow.put(columnNameLowerCase, new SqlColumn(value, sqlType));
		return this;
	}

	/**
	 * Prepares for a new row to be added to the statement. All subsequent
	 * setter calls will be applied to this new row.
	 * @return this
	 */
	public InsertStatement nextRow() {
		currentRow = new HashMap<String, SqlColumn>();
		rows.add(currentRow);
		return this;
	}

	/**
	 * Generates the SQL command for this insert statement.
	 * @return the SQL command
	 * @throws IllegalStateException if no columns were added (via the "set"
	 * methods)
	 */
	public String toSql() {
		if (columnNames.isEmpty()) {
			throw new IllegalStateException("Cannot generate SQL: no columns were added.");
		}

		StringBuilder sql = new StringBuilder();
		sql.append("INSERT INTO ").append(tableName).append(" (");

		int i = 0;
		for (String columnName : columnNames) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(columnName);
			i++;
		}

		sql.append(") VALUES ");

		int nonEmptyRows = 0;
		for (Map<String, SqlColumn> row : rows) {
			if (row.isEmpty()) {
				//ignore any extra "nextRow()" calls the user made
				continue;
			}

			if (nonEmptyRows > 0) {
				sql.append(",\n");
			}

			sql.append('(');
			for (int colCount = 0; colCount < columnNames.size(); colCount++) {
				if (colCount > 0) {
					sql.append(", ");
				}
				sql.append('?');
			}
			sql.append(')');

			nonEmptyRows++;
		}

		return sql.toString();
	}

	/**
	 * Generates the {@link PreparedStatement} object for this insert statement.
	 * @param conn the database connection
	 * @return the statement object
	 * @throws IllegalStateException if no columns were added (via the "set"
	 * methods)
	 * @throws SQLException if there was a problem creating the statement
	 */
	public PreparedStatement toStatement(Connection conn) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement(toSql(), Statement.RETURN_GENERATED_KEYS);
		int index = 1;
		int supportedTypes[] = new int[] { Types.INTEGER, Types.VARCHAR, Types.DATE, Types.TIMESTAMP };
		for (Map<String, SqlColumn> row : rows) {
			if (row.isEmpty()) {
				//ignore any extra "nextRow()" calls the user made
				continue;
			}

			for (String columnName : columnNames) {
				SqlColumn column = row.get(columnName);
				if (column == null) {
					//set the column value to "null"
					for (int type : supportedTypes) {
						try {
							stmt.setNull(index, type);
						} catch (SQLDataException e) {
							//try the next type
						}
					}
				} else {
					Object value = column.getValue();
					int sqlType = column.getSqlType();

					if (value == null) {
						stmt.setNull(index, sqlType);
					} else {
						switch (sqlType) {
						case Types.INTEGER:
							stmt.setInt(index, (Integer) value);
							break;
						case Types.VARCHAR:
							stmt.setString(index, (String) value);
							break;
						case Types.DATE:
							Date date = (Date) value;
							stmt.setDate(index, new java.sql.Date(date.getTime()));
							break;
						case Types.TIMESTAMP:
							Date timestamp = (Date) value;
							stmt.setTimestamp(index, new Timestamp(timestamp.getTime()));
							break;
						default:
							throw new UnsupportedOperationException("Unable to handle SQL type " + sqlType + ".");
						}
					}
				}
				index++;
			}
		}
		return stmt;
	}

	/**
	 * Executes the insert statement.
	 * @param conn the database connection
	 * @return the generated ID or null if no ID was generated
	 * @throws IllegalStateException if no columns were added (via the "set"
	 * methods)
	 * @throws SQLException if there was a problem executing the query
	 */
	public Integer execute(Connection conn) throws SQLException {
		PreparedStatement stmt = null;
		try {
			stmt = toStatement(conn);
			stmt.execute();

			ResultSet rs = stmt.getGeneratedKeys();
			return rs.next() ? rs.getInt(1) : null; //this only returns one ID, even if multiple rows are inserted
		} finally {
			if (stmt != null) {
				stmt.close();
			}
		}
	}

	private static class SqlColumn {
		private final Object value;
		private final int sqlType;

		public SqlColumn(Object value, int sqlType) {
			this.value = value;
			this.sqlType = sqlType;
		}

		public Object getValue() {
			return value;
		}

		public int getSqlType() {
			return sqlType;
		}
	}
}
