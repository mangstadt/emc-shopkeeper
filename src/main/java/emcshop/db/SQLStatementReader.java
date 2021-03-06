package emcshop.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Statement;

/**
 * Reads SQL statements one at a time from a .sql file, so they can be executed
 * via JDBC with {@link Statement#execute}.
 * @author Michael Angstadt
 */
public class SQLStatementReader extends BufferedReader {
	public SQLStatementReader(Reader in) {
		super(in);
	}

	/**
	 * Reads the next SQL statement.
	 * @return the next SQL statement or null if EOF
	 * @throws IOException if there's a problem reading the file
	 */
	public String readStatement() throws IOException {
		StringBuilder sb = new StringBuilder();

		boolean allWhitespace = true;
		int i;
		while ((i = read()) != -1) {
			if (i == ';') {
				if (!allWhitespace) {
					break;
				}
			} else {
				if (allWhitespace && !Character.isWhitespace(i)) {
					allWhitespace = false;
				}
				sb.append((char) i);
			}
		}

		return allWhitespace ? null : sb.toString().trim();
	}
}
