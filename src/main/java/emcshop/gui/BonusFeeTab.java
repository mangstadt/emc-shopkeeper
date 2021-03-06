package emcshop.gui;

import java.awt.Color;
import java.awt.Component;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import emcshop.db.BonusFee;
import emcshop.db.DbDao;
import emcshop.util.RelativeDateFormat;
import emcshop.util.RupeeFormatter;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class BonusFeeTab extends JPanel {
	private final DbDao dao;
	private final DateTimeFormatter df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

	private final JLabel since;
	private final BonusFeeTable table;
	private final MyJScrollPane tableScrollPane;

	/**
	 * Defines all of the columns in this table. The order in which the enums
	 * are defined is the order that they will appear in the table.
	 */
	private enum Column {
		DESCRIPTION("Description"), TOTAL("Total");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public BonusFeeTab(DbDao dao) {
		this.dao = dao;

		table = new BonusFeeTable();
		since = new JLabel();

		///////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		add(new JLabel("Data collection start date:"), "split 2, align center");
		add(since, "wrap");

		tableScrollPane = new MyJScrollPane(table);
		add(tableScrollPane, "w 100%, align center");

		refresh();
	}

	public void refresh() {
		BonusFee bonusFee;
		try {
			bonusFee = dao.getBonusesFees();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		LocalDateTime sinceDate = bonusFee.getSince();
		since.setText("<html><b><i><font color=navy>" + ((sinceDate == null) ? "never" : df.format(sinceDate)) + "</font></i></b></html>");

		table.setData(bonusFee);
	}

	private static class Row {
		private final String description, text;
		private final int total;

		public Row(String description, int total, String text) {
			this.description = description;
			this.total = total;
			this.text = text;
		}
	}

	private static class BonusFeeTable extends JTable {
		private final Column columns[] = Column.values();
		private final Model model;

		public BonusFeeTable() {
			getTableHeader().setReorderingAllowed(false);
			setColumnSelectionAllowed(false);
			setRowSelectionAllowed(false);
			setCellSelectionEnabled(false);
			setRowHeight(24);

			setDefaultRenderer(Row.class, new TableCellRenderer() {
				private final Color evenRowColor = new Color(255, 255, 255);
				private final Color oddRowColor = new Color(240, 240, 240);

				private final JLabel label = new JLabel();
				{
					label.setOpaque(true);
					label.setBorder(new EmptyBorder(4, 4, 4, 4));
				}

				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
					if (value == null) {
						return null;
					}

					Row rowObj = (Row) value;
					Column column = columns[col];

					switch (column) {
					case DESCRIPTION:
						label.setText(rowObj.description);
						label.setHorizontalAlignment(SwingConstants.RIGHT);
						break;

					case TOTAL:
						label.setText("<html>" + rowObj.text + "</html>");
						label.setHorizontalAlignment(SwingConstants.LEFT);
						break;
					}

					//set the background color of the row
					Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
					label.setBackground(color);

					return label;
				}
			});

			model = new Model();
			setModel(model);

			setRowSorter(createRowSorter());
		}

		private TableRowSorter<Model> createRowSorter() {
			TableRowSorter<Model> rowSorter = new TableRowSorter<>(model);

			rowSorter.setComparator(Column.DESCRIPTION.ordinal(), (Row one, Row two) -> one.description.compareToIgnoreCase(two.description));
			rowSorter.setComparator(Column.TOTAL.ordinal(), Comparator.comparingInt((Row r) -> r.total));
			rowSorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(Column.DESCRIPTION.ordinal(), SortOrder.ASCENDING)));
			rowSorter.setSortsOnUpdates(true);

			return rowSorter;
		}

		private void setData(BonusFee bonusFee) {
			model.data.clear();

			RupeeFormatter rf = new RupeeFormatter();
			rf.setPlus(true);
			rf.setColor(true);

			model.data.add(new Row("Horse Summoning", bonusFee.getHorse(), rf.format(bonusFee.getHorse())));
			model.data.add(new Row("Chest Locking", bonusFee.getLock(), rf.format(bonusFee.getLock())));
			model.data.add(new Row("Eggifying animals", bonusFee.getEggify(), rf.format(bonusFee.getEggify())));
			model.data.add(new Row("Vault fees", bonusFee.getVault(), rf.format(bonusFee.getVault())));
			model.data.add(new Row("Sign-in bonuses", bonusFee.getSignIn(), rf.format(bonusFee.getSignIn())));
			model.data.add(new Row("Voting bonuses", bonusFee.getVote(), rf.format(bonusFee.getVote())));
			model.data.add(new Row("Mail fees", bonusFee.getMail(), rf.format(bonusFee.getMail())));

			rf.setPlus(false);
			rf.setColor(false);
			String text = rf.format(bonusFee.getHighestBalance());
			LocalDateTime highestBalanceTs = bonusFee.getHighestBalanceTs();
			if (highestBalanceTs != null) {
				RelativeDateFormat df = RelativeDateFormat.instance();
				text += " (" + df.format(highestBalanceTs) + ")";
			}

			model.data.add(new Row("Highest Balance", bonusFee.getHighestBalance(), text));

			model.fireTableDataChanged();
		}

		private class Model extends AbstractTableModel {
			private final List<Row> data = new ArrayList<>();

			@Override
			public int getColumnCount() {
				return columns.length;
			}

			@Override
			public String getColumnName(int index) {
				Column column = columns[index];
				return column.getName();
			}

			@Override
			public int getRowCount() {
				return data.size();
			}

			@Override
			public Object getValueAt(int row, int col) {
				return data.get(row);
			}

			@Override
			public Class<?> getColumnClass(int col) {
				return Row.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				return false;
			}
		}
	}
}
