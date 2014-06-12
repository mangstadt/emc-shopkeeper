package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import emcshop.gui.ProfileLoader.ProfileDownloadedListener;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.ButtonColumn;
import emcshop.scraper.EmcServer;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.util.GuiUtils;
import emcshop.util.RelativeDateFormat;

@SuppressWarnings("serial")
public class PaymentsTab extends JPanel {
	private static final AppContext context = AppContext.instance();

	private final MainFrame owner;
	private final DbDao dao;
	private final ProfileLoader profileLoader;
	private final OnlinePlayersMonitor onlinePlayersMonitor;
	private boolean stale = true;
	private PaymentsTable paymentsTable;

	public PaymentsTab(MainFrame owner) {
		this.owner = owner;
		dao = context.get(DbDao.class);
		profileLoader = context.get(ProfileLoader.class);
		onlinePlayersMonitor = context.get(OnlinePlayersMonitor.class);

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
				paymentsTable = new PaymentsTable(pendingPayments);
				MyJScrollPane scrollPane = new MyJScrollPane(paymentsTable);
				add(scrollPane, "grow, w 100%, h 100%, wrap");
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
		DELETE("Delete"), SPLIT("Split"), ASSIGN("Assign"), TIME("Time"), PLAYER("Player"), AMOUNT("Amount");

		private final String name;

		private Column(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private class PaymentsTable extends JTable {
		private final Column columns[] = Column.values();
		private final Model model;
		private final TableRowSorter<Model> rowSorter;

		public PaymentsTable(List<PaymentTransaction> rows) {
			setRowHeight(24);

			setDefaultRenderer(PaymentTransaction.class, new Renderer());

			model = new Model(rows);
			setModel(model);

			rowSorter = createRowSorter();
			setRowSorter(rowSorter);

			setColumns();

			setSelectionModel();
		}

		private TableRowSorter<Model> createRowSorter() {
			TableRowSorter<Model> rowSorter = new TableRowSorter<Model>(model);

			rowSorter.setSortable(Column.ASSIGN.ordinal(), false);
			rowSorter.setSortable(Column.DELETE.ordinal(), false);
			rowSorter.setSortable(Column.SPLIT.ordinal(), false);
			rowSorter.setComparator(Column.PLAYER.ordinal(), new Comparator<PaymentTransaction>() {
				@Override
				public int compare(PaymentTransaction one, PaymentTransaction two) {
					return one.getPlayer().compareToIgnoreCase(two.getPlayer());
				}
			});
			rowSorter.setComparator(Column.TIME.ordinal(), new Comparator<PaymentTransaction>() {
				@Override
				public int compare(PaymentTransaction one, PaymentTransaction two) {
					return one.getTs().compareTo(two.getTs());
				}
			});
			rowSorter.setComparator(Column.AMOUNT.ordinal(), new Comparator<PaymentTransaction>() {
				@Override
				public int compare(PaymentTransaction one, PaymentTransaction two) {
					return one.getAmount() - two.getAmount();
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

			private final JLabel label = new JLabel();
			{
				label.setOpaque(true);
				label.setBorder(new EmptyBorder(4, 4, 4, 4));
			}

			private final JLabel playerLabel = new JLabel();
			private final JLabel serverLabel = new JLabel();
			private final JPanel playerPanel = new JPanel(new MigLayout("insets 2"));
			{
				playerPanel.setOpaque(true);
				playerPanel.add(playerLabel);
				playerPanel.add(serverLabel);
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, final int col) {
				if (value == null) {
					return null;
				}

				PaymentTransaction transaction = (PaymentTransaction) value;
				Column column = columns[col];
				resetComponents();

				Component component = null;
				switch (column) {
				case TIME:
					component = label;

					label.setText(df.format(transaction.getTs()));
					break;

				case PLAYER:
					component = playerPanel;

					String playerName = transaction.getPlayer();
					playerLabel.setText(playerName);

					ImageIcon portrait = profileLoader.getPortraitFromCache(playerName);
					if (portrait == null) {
						portrait = ImageManager.getUnknown();
					}
					portrait = ImageManager.scale(portrait, 16);
					playerLabel.setIcon(portrait);

					if (!profileLoader.wasDownloaded(playerName)) {
						profileLoader.queueProfileForDownload(playerName, new ProfileDownloadedListener() {
							@Override
							public void onProfileDownloaded(JLabel label) {
								//re-render the cell when the profile is downloaded
								AbstractTableModel model = (AbstractTableModel) getModel();
								model.fireTableCellUpdated(row, col);
							}
						});
					}

					EmcServer server = onlinePlayersMonitor.getPlayerServer(playerName);
					if (server != null) {
						serverLabel.setIcon(ImageManager.getOnline(server, 12));
					}
					break;

				case AMOUNT:
					component = label;

					label.setText("<html>" + formatRupeesWithColor(transaction.getAmount()) + "</html>");
					break;
				default:
					component = label;

					label.setText("");
					break;
				}

				//set the background color of the row
				Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
				component.setBackground(color);

				return component;
			}

			private void resetComponents() {
				serverLabel.setIcon(null);
			}
		}

		private class Model extends AbstractTableModel {
			private final Icon deleteIcon = ImageManager.getImageIcon("delete.png");
			private final Icon assignIcon = ImageManager.getImageIcon("assign.png");
			private final Icon splitIcon = ImageManager.getImageIcon("split.png");
			private final List<PaymentTransaction> data;

			public Model(List<PaymentTransaction> data) {
				this.data = data;
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
				Column column = columns[col];
				switch (column) {
				case DELETE:
					return deleteIcon;
				case ASSIGN:
					return assignIcon;
				case SPLIT:
					return splitIcon;
				default:
					return data.get(row);
				}
			}

			@Override
			public Class<?> getColumnClass(int col) {
				Column column = columns[col];
				switch (column) {
				case DELETE:
				case ASSIGN:
				case SPLIT:
					return Icon.class;
				default:
					return PaymentTransaction.class;
				}
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				Column column = columns[col];
				switch (column) {
				case DELETE:
				case ASSIGN:
				case SPLIT:
					//allows the buttons to be clicked
					return true;
				default:
					return false;
				}
			}
		}

		private void setSelectionModel() {
			setColumnSelectionAllowed(false);
			setRowSelectionAllowed(false);
			setCellSelectionEnabled(false);
		}

		private void setColumns() {
			new ButtonColumn(this, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent event) {
					int row = Integer.valueOf(event.getActionCommand());
					deleteRow(row);
				}
			}, Column.DELETE.ordinal());

			new ButtonColumn(this, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent event) {
					int row = Integer.valueOf(event.getActionCommand());
					assignRow(row);
				}
			}, Column.ASSIGN.ordinal());

			new ButtonColumn(this, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent event) {
					int row = Integer.valueOf(event.getActionCommand());
					splitRow(row);
				}
			}, Column.SPLIT.ordinal());

			TableColumn deleteColumn = columnModel.getColumn(Column.DELETE.ordinal());
			deleteColumn.setMaxWidth(50);
			deleteColumn.setResizable(false);

			TableColumn assignColumn = columnModel.getColumn(Column.ASSIGN.ordinal());
			assignColumn.setMaxWidth(50);
			assignColumn.setResizable(false);

			TableColumn splitColumn = columnModel.getColumn(Column.SPLIT.ordinal());
			splitColumn.setMaxWidth(50);
			splitColumn.setResizable(false);

			getTableHeader().setReorderingAllowed(false);
		}

		private void deleteRow(int row) {
			int result = JOptionPane.showConfirmDialog(owner, "Are you sure you want to delete this payment transaction?", "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
			if (result != JOptionPane.YES_OPTION) {
				return;
			}

			PaymentTransaction transaction = model.data.get(row);

			try {
				dao.ignorePaymentTransaction(transaction.getId());
				dao.commit();
			} catch (SQLException e) {
				dao.rollback();
				throw new RuntimeException(e);
			}

			owner.updatePaymentsCount();

			model.data.remove(row);
			model.fireTableRowsDeleted(row, row);
		}

		private void assignRow(int row) {
			PaymentTransaction transaction = model.data.get(row);

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

		public void splitRow(int row) {
			PaymentTransaction transaction = model.data.get(row);

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
			model.data.add(insertIndex, splitTransaction);
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
