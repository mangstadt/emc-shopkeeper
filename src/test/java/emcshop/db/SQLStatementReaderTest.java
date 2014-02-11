package emcshop.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.StringReader;

import org.junit.Test;

public class SQLStatementReaderTest {
	@Test
	public void readStatement() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n);\ninsert into myTable (foo, bar) values (1, 'bar');");
		SQLStatementReader in = new SQLStatementReader(sr);
		assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		assertEquals("insert into myTable (foo, bar) values (1, 'bar')", in.readStatement());
		assertNull(in.readStatement());
		in.close();
	}

	@Test
	public void readStatement_no_ending_semicolon() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n)");
		SQLStatementReader in = new SQLStatementReader(sr);
		assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		assertNull(in.readStatement());
		in.close();
	}

	@Test
	public void readStatement_trailing_newline() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n);\n");
		SQLStatementReader in = new SQLStatementReader(sr);
		assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		assertNull(in.readStatement());
		in.close();
	}

	@Test
	public void readStatement_two_statements_no_ending_semicolon() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n);\ninsert into myTable (foo, bar) values (1, 'bar')");
		SQLStatementReader in = new SQLStatementReader(sr);
		assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		assertEquals("insert into myTable (foo, bar) values (1, 'bar')", in.readStatement());
		assertNull(in.readStatement());
		in.close();
	}

	@Test
	public void readStatement_whitespace_after_last_semicolon() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n);  ");
		SQLStatementReader in = new SQLStatementReader(sr);
		assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		assertNull(in.readStatement());
		in.close();
	}

	@Test
	public void readStatement_consecutive_semicolons() throws Exception {
		StringReader sr = new StringReader("; \t;create table myTable(\nfoo int,\nbar varchar(40)\n);  ;  ;\ninsert into myTable (foo, bar) values (1, 'bar');\t;  \n ;; ");
		SQLStatementReader in = new SQLStatementReader(sr);
		assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		assertEquals("insert into myTable (foo, bar) values (1, 'bar')", in.readStatement());
		assertNull(in.readStatement());
		in.close();
	}
}
