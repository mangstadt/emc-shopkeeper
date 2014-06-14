package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.GuiUtils.shrinkFont;
import static emcshop.util.NumberFormatter.formatRupees;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;
import static emcshop.util.NumberFormatter.getQuantityColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import net.miginfocom.swing.MigLayout;
import emcshop.AppContext;
import emcshop.ItemIndex;
import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.util.GuiUtils;
import emcshop.util.RelativeDateFormat;
import emcshop.util.UIDefaultsWrapper;

@SuppressWarnings("serial")
public class PaymentsTab extends JPanel {
	private static final AppContext context = AppContext.instance();

	private final MainFrame owner;
	private final DbDao dao = context.get(DbDao.class);
	private boolean stale = true;

	private JButton delete;
	private PaymentsTable paymentsTable;

	public PaymentsTab(MainFrame owner) {
		this.owner = owner;
		setLayout(new MigLayout("fillx, insets 5"));
	}

	public void reset() {
		busyCursor(owner, true);
		try {
			removeAll();
			validate();

			List<PaymentTransaction> pendingPayments = dao.getPendingPaymentTransactions();
			if (pendingPayments.isEmpty()) {
				add(new JLabel("No payment transactions found."), "align center");
			} else {
				JPanel inner = new JPanel(new MigLayout("insets 0"));

				delete = new JButton("Delete");
				delete.setEnabled(false);
				delete.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent event) {
						List<Row> selected = paymentsTable.getSelected();
						if (selected.isEmpty()) {
							return;
						}

						int result = JOptionPane.showConfirmDialog(owner, "Are you sure you want to delete the selected payment transactions?", "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
						if (result != JOptionPane.YES_OPTION) {
							return;
						}

						//update database
						try {
							for (Row row : selected) {
								dao.ignorePaymentTransaction(row.transaction.getId());
							}
							dao.commit();
						} catch (SQLException e) {
							dao.rollback();
							throw new RuntimeException(e);
						}

						//update table
						paymentsTable.model.data.removeAll(selected);
						paymentsTable.model.fireTableDataChanged();

						boolean enabled = paymentsTable.getSelectedCount() > 0;
						delete.setEnabled(enabled);

						owner.updatePaymentsCount();
					}

				});
				shrinkFont(delete);
				inner.add(delete, "wrap");

				paymentsTable = new PaymentsTable(pendingPayments);
				MyJScrollPane scrollPane = new MyJScrollPane(paymentsTable);
				inner.add(scrollPane, "h 100%, w 100%");

				add(inner, "align center, w :650:650, h 100%");
			}

			validate();

			stale = false;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			busyCursor(owner, false);
		}
	}

	public boolean isStale() {
		return stale;
	}

	public void setStale(boolean stale) {
		this.stale = stale;
	}

	/**
	 * Defines all of the columns in this table. The order in which the enums
	 * are defined is the order that they will appear in the table.
	 */
	private enum Column {
		CHECKBOX(""), SPLIT("Split"), ASSIGN("Assign"), TIME("Time"), PLAYER("Player"), AMOUNT("Amount");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private static class Row {
		private final PaymentTransaction transaction;
		private boolean selected = false;

		public Row(PaymentTransaction transaction) {
			this.transaction = transaction;
		}
	}

	private class PaymentsTable extends JTable {
		private final Column columns[] = Column.values();
		private final Model model;
		private final TableRowSorter<Model> rowSorter;

		public PaymentsTable(List<PaymentTransaction> rows) {
			setRowHeight(24);

			setDefaultRenderer(Row.class, new Renderer());

			model = new Model(rows);
			setModel(model);

			rowSorter = createRowSorter();
			setRowSorter(rowSorter);

			setColumns();

			setSelectionModel();
		}

		public List<Row> getSelected() {
			List<Row> selected = new ArrayList<Row>();
			for (Row row : model.data) {
				if (row.selected) {
					selected.add(row);
				}
			}
			return selected;
		}

		public int getSelectedCount() {
			int count = 0;
			for (Row row : model.data) {
				if (row.selected) {
					count++;
				}
			}
			return count;
		}

		private TableRowSorter<Model> createRowSorter() {
			TableRowSorter<Model> rowSorter = new TableRowSorter<Model>(model);

			rowSorter.setSortable(Column.CHECKBOX.ordinal(), false);
			rowSorter.setSortable(Column.SPLIT.ordinal(), false);
			rowSorter.setSortable(Column.ASSIGN.ordinal(), false);
			rowSorter.setComparator(Column.TIME.ordinal(), new Comparator<Row>() {
				@Override
				public int compare(Row one, Row two) {
					return one.transaction.getTs().compareTo(two.transaction.getTs());
				}
			});
			rowSorter.setComparator(Column.PLAYER.ordinal(), new Comparator<Row>() {
				@Override
				public int compare(Row one, Row two) {
					return one.transaction.getPlayer().compareToIgnoreCase(two.transaction.getPlayer());
				}
			});
			rowSorter.setComparator(Column.AMOUNT.ordinal(), new Comparator<Row>() {
				@Override
				public int compare(Row one, Row two) {
					return one.transaction.getAmount() - two.transaction.getAmount();
				}
			});
			rowSorter.setSortsOnUpdates(true);
			rowSorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(Column.TIME.ordinal(), SortOrder.DESCENDING)));

			return rowSorter;
		}

		private class Renderer implements TableCellRenderer {
			private final RelativeDateFormat df = new RelativeDateFormat();
			private final Color evenRowColor = new Color(255, 255, 255);
			private final Color oddRowColor = new Color(240, 240, 240);

			private final JCheckBox checkbox = new JCheckBox();
			{
				checkbox.setOpaque(true);
			}

			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(4, 4, 4, 4));
			}

			private final PlayerCellPanel playerPanel = new PlayerCellPanel();

			private final JButton assignButton = new JButton(ImageManager.getImageIcon("assign.png"));
			private final JButton splitButton = new JButton(ImageManager.getImageIcon("split.png"));

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, final int col) {
				if (value == null) {
					return null;
				}

				Row rowObj = (Row) value;
				PaymentTransaction transaction = rowObj.transaction;
				Column column = columns[col];
				resetComponents();

				JComponent component = null;
				switch (column) {
				case SPLIT:
					component = splitButton;
					break;

				case ASSIGN:
					component = assignButton;
					break;

				case CHECKBOX:
					component = checkbox;

					checkbox.setSelected(rowObj.selected);
					break;

				case TIME:
					component = label;

					label.setText(df.format(transaction.getTs()));
					break;

				case PLAYER:
					component = playerPanel;

					String playerName = transaction.getPlayer();
					playerPanel.setPlayer(playerName, row, col, model);
					break;

				case AMOUNT:
					component = label;

					int amount = transaction.getAmount();
					Color color = getQuantityColor(amount);
					if (color != null) {
						label.setForeground(color);
					}
					label.setText(formatRupees(transaction.getAmount()));
					break;
				}

				//set the background color of the row
				if (rowObj.selected) {
					UIDefaultsWrapper.assignListFormats(component, true);
				} else {
					Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
					component.setBackground(color);
				}

				return component;
			}

			private void resetComponents() {
				label.setForeground(UIDefaultsWrapper.getLabelForeground());
			}
		}

		private class Model extends AbstractTableModel {
			private final List<Row> data;

			public Model(List<PaymentTransaction> data) {
				this.data = new ArrayList<Row>(data.size());
				for (PaymentTransaction transaction : data) {
					Row row = new Row(transaction);
					this.data.add(row);
				}
			}

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

		private void clickCell(int rowView, int colView) {
			int row = convertRowIndexToModel(rowView);
			int col = convertColumnIndexToModel(colView);
			Column column = columns[col];

			switch (column) {
			case SPLIT:
				splitRow(row);
				break;

			case ASSIGN:
				assignRow(row);
				break;

			default:
				Row rowObj = model.data.get(row);
				rowObj.selected = !rowObj.selected;

				//re-render table row
				model.fireTableRowsUpdated(row, row);
			}

			boolean enable = getSelectedCount() > 0;
			delete.setEnabled(enable);
		}

		private void setSelectionModel() {
			addMouseListener(new MouseAdapter() {
				private int mousePressedCol, mousePressedRow;

				@Override
				public void mousePressed(MouseEvent event) {
					int colView = columnAtPoint(event.getPoint());
					int rowView = rowAtPoint(event.getPoint());
					if (colView < 0 || rowView < 0) {
						return;
					}

					mousePressedCol = colView;
					mousePressedRow = rowView;
				}

				@Override
				public void mouseReleased(MouseEvent event) {
					int colView = columnAtPoint(event.getPoint());
					int rowView = rowAtPoint(event.getPoint());
					if (colView < 0 || rowView < 0) {
						return;
					}

					if (colView == mousePressedCol && rowView == mousePressedRow) {
						clickCell(rowView, colView);
					}
				}
			});
		}

		private TableColumn getColumn(Column column) {
			return columnModel.getColumn(column.ordinal());
		}

		private void setColumns() {
			TableColumn deleteColumn = getColumn(Column.CHECKBOX);
			deleteColumn.setMaxWidth(30);
			deleteColumn.setResizable(false);

			TableColumn assignColumn = getColumn(Column.ASSIGN);
			assignColumn.setMaxWidth(50);
			assignColumn.setResizable(false);

			TableColumn splitColumn = getColumn(Column.SPLIT);
			splitColumn.setMaxWidth(50);
			splitColumn.setResizable(false);

			TableColumn timeColumn = getColumn(Column.TIME);
			timeColumn.setPreferredWidth(200);

			TableColumn playerColumn = getColumn(Column.PLAYER);
			playerColumn.setPreferredWidth(200);

			TableColumn amountColumn = getColumn(Column.AMOUNT);
			amountColumn.setPreferredWidth(100);

			getTableHeader().setReorderingAllowed(false);
		}

		private void assignRow(int row) {
			PaymentTransaction transaction = model.data.get(row).transaction;

			AssignDialog.Result result = AssignDialog.show(owner, transaction);
			if (result == null) {
				return;
			}

			ShopTransaction shopTransaction = new ShopTransaction();
			shopTransaction.setTs(transaction.getTs());
			shopTransaction.setPlayer(transaction.getPlayer());
			shopTransaction.setAmount(transaction.getAmount());
			shopTransaction.setBalance(transaction.getBalance());
			shopTransaction.setItem(result.getItemName());

			int quantity = result.getQuantity();
			if (transaction.getAmount() > 0) {
				//negate quantity if it's a sale
				quantity *= -1;
			}
			shopTransaction.setQuantity(quantity);

			try {
				dao.insertTransaction(shopTransaction, result.isUpdateInventory());
				dao.assignPaymentTransaction(transaction.getId(), shopTransaction.getId());
				dao.commit();
			} catch (SQLException e) {
				dao.rollback();
				throw new RuntimeException(e);
			}

			owner.updateInventoryTab();
			owner.updatePaymentsCount();

			model.data.remove(row);
			model.fireTableRowsDeleted(row, row);
		}

		private void splitRow(int row) {
			PaymentTransaction transaction = model.data.get(row).transaction;

			Integer splitAmount = showSplitDialog(transaction);
			if (splitAmount == null) {
				//user canceled the dialog
				return;
			}
			if (transaction.getAmount() < 0) {
				splitAmount *= -1;
			}

			//update the existing payment transaction
			int origBalance = transaction.getBalance() - transaction.getAmount();
			transaction.setAmount(transaction.getAmount() - splitAmount);
			transaction.setBalance(origBalance + transaction.getAmount());

			//create the new payment transaction
			PaymentTransaction splitTransaction = new PaymentTransaction();
			splitTransaction.setAmount(splitAmount);
			splitTransaction.setBalance(transaction.getBalance() + splitAmount);
			splitTransaction.setPlayer(transaction.getPlayer());
			splitTransaction.setTs(transaction.getTs());

			try {
				dao.upsertPaymentTransaction(transaction);
				dao.upsertPaymentTransaction(splitTransaction);
				dao.commit();
			} catch (SQLException e) {
				dao.rollback();
				throw new RuntimeException(e);
			}

			owner.updatePaymentsCount();

			int insertIndex = row + 1;
			model.data.add(insertIndex, new Row(splitTransaction));
			model.fireTableRowsInserted(insertIndex, insertIndex);
		}
	}

	private Integer showSplitDialog(PaymentTransaction transaction) {
		int origAmount = Math.abs(transaction.getAmount());
		do {
			String amountStr = JOptionPane.showInputDialog(owner, "Enter the number of rupees you'd like to subtract\nfrom this payment transaction.  A new payment transaction\nwill then be created with the value you enter.\n\nEnter a value between 1 and " + (origAmount - 1) + ":", "Split Payment Transaction", JOptionPane.QUESTION_MESSAGE);
			if (amountStr == null) {
				//user canceled dialog
				return null;
			}

			Integer amountInt;
			try {
				amountInt = Integer.valueOf(amountStr);
			} catch (NumberFormatException e) {
				JOptionPane.showMessageDialog(owner, "Invalid number.", "Error", JOptionPane.ERROR_MESSAGE);
				continue;
			}

			if (amountInt <= 0 || amountInt >= origAmount) {
				JOptionPane.showMessageDialog(owner, "Amount must be between 1 and " + (origAmount - 1) + ".", "Error", JOptionPane.ERROR_MESSAGE);
				continue;
			}

			return amountInt;
		} while (true);
	}

	private static class AssignDialog extends JDialog {
		private final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		private final ItemSuggestField item;
		private final QuantityTextField quantity;
		private final JButton ok, cancel;
		private final JCheckBox updateInventory;
		private boolean canceled = false;

		public AssignDialog(Window owner, final PaymentTransaction paymentTransaction) {
			super(owner, "Assign Payment Transaction");
			setModal(true);
			setResizable(false);

			//cancel when the window is closed
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent arg0) {
					cancel();
				}
			});

			//cancel when escape is pressed
			GuiUtils.onEscapeKeyPress(this, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					cancel();
				}
			});

			item = new ItemSuggestField(this);
			quantity = new QuantityTextField();
			quantity.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ok.doClick();
				}
			});

			ok = new JButton("Ok");
			ok.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if (item.getText().isEmpty()) {
						showError("No item specified.");
						return;
					}

					if (quantity.getText().isEmpty()) {
						showError("No quantity specified.");
						return;
					}

					if (!quantity.hasValidValue()) {
						showError("Invalid quantity value.");
						return;
					}

					dispose();
				}

				private void showError(String message) {
					JOptionPane.showMessageDialog(AssignDialog.this, message, "Error", JOptionPane.ERROR_MESSAGE);
				}
			});

			cancel = new JButton("Cancel");
			cancel.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					cancel();
				}
			});

			updateInventory = new JCheckBox("Apply to Inventory", true);

			JLabel quantityLabel = new HelpLabel("Qty:", "In addition to specifying the exact number of items, you can also specify the quantity in stacks.\n\n<b>Examples</b>:\n\"264\" (264 items total)\n\"4/10\" (4 stacks, plus 10 more)\n\"4/\" (4 stacks)\n\nNote that <b>stack size varies depending on the item</b>!  Most items can hold 64 in a stack, but some can only hold 16 (like Signs) and others are not stackable at all (like armor)!");

			/////////////////////////

			setLayout(new MigLayout());

			JPanel top = new JPanel(new MigLayout("insets 0"));

			top.add(new JLabel("Time:"));
			top.add(new JLabel("Player:"));
			top.add(new JLabel("Amount:"), "wrap");

			top.add(new JLabel(df.format(paymentTransaction.getTs())), "gapright 10");
			top.add(new JLabel(paymentTransaction.getPlayer()), "gapright 10");
			top.add(new JLabel("<html>" + formatRupeesWithColor(paymentTransaction.getAmount()) + "</html>"));

			add(top, "wrap");

			JPanel bottom = new JPanel(new MigLayout("insets 0"));

			bottom.add(new JLabel("Item:"));
			bottom.add(quantityLabel, "wrap");
			bottom.add(item, "w 175");
			bottom.add(quantity, "w 100, wrap");
			add(bottom, "gaptop 20, wrap");

			add(new HelpLabel("", "Check this box to apply this transaction to your shop's inventory."), "split 2");
			add(updateInventory, "wrap");

			add(ok, "split 2, align center");
			add(cancel);

			pack();
			setLocationRelativeTo(owner);
		}

		private void cancel() {
			canceled = true;
			dispose();
		}

		public static Result show(Window owner, PaymentTransaction paymentTransaction) {
			AssignDialog dialog = new AssignDialog(owner, paymentTransaction);
			dialog.setVisible(true);
			if (dialog.canceled) {
				return null;
			}

			String itemName = dialog.item.getText();
			int quantity = dialog.quantity.getQuantity(ItemIndex.instance().getStackSize(itemName));
			quantity = Math.abs(quantity); //incase the user enters a negative value

			return new Result(itemName, quantity, dialog.updateInventory.isSelected());
		}

		public static class Result {
			private final String itemName;
			private final Integer quantity;
			private final boolean updateInventory;

			public Result(String itemName, Integer quantity, boolean updateInventory) {
				this.itemName = itemName;
				this.quantity = quantity;
				this.updateInventory = updateInventory;
			}

			public String getItemName() {
				return itemName;
			}

			public Integer getQuantity() {
				return quantity;
			}

			public boolean isUpdateInventory() {
				return updateInventory;
			}
		}
	}
}
