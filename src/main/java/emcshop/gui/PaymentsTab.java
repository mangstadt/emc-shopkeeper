package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.GuiUtils.shrinkFont;
import static emcshop.util.NumberFormatter.formatRupees;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;
import static emcshop.util.NumberFormatter.getQuantityColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
import emcshop.gui.images.Images;
import emcshop.gui.lib.GroupPanel;
import emcshop.model.ChatLogViewerModelImpl;
import emcshop.model.IChatLogViewerModel;
import emcshop.presenter.ChatLogViewerPresenter;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.PlayerProfile;
import emcshop.scraper.ShopTransaction;
import emcshop.util.GuiUtils;
import emcshop.util.RelativeDateFormat;
import emcshop.util.UIDefaultsWrapper;
import emcshop.view.ChatLogViewerViewImpl;
import emcshop.view.IChatLogViewerView;

@SuppressWarnings("serial")
public class PaymentsTab extends JPanel {
	private static final AppContext context = AppContext.instance();

	private final MainFrame owner;
	private final DbDao dao = context.get(DbDao.class);
	private boolean stale = true;

	private JButton delete, merge;
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

						delete.setEnabled(false);

						owner.updatePaymentsCount();
					}

				});
				shrinkFont(delete);
				inner.add(delete, "split 2");

				merge = new JButton("Merge");
				merge.setEnabled(false);
				merge.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent event) {
						//at least two rows must be selected
						List<Row> selected = paymentsTable.getSelected();
						if (selected.size() < 2) {
							return;
						}

						//are all the player names the same?
						String name = null;
						for (Row row : selected) {
							if (name == null) {
								name = row.transaction.getPlayer();
							} else if (!name.equalsIgnoreCase(row.transaction.getPlayer())) {
								return;
							}
						}

						int result = JOptionPane.showConfirmDialog(owner, "Are you sure you want to merge these payment transactions?", "Confirm Merge", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
						if (result != JOptionPane.YES_OPTION) {
							return;
						}

						Row latest = null;
						int amountTotal = 0;
						for (Row row : selected) {
							amountTotal += row.transaction.getAmount();
							if (latest == null || row.transaction.getTs().after(latest.transaction.getTs())) {
								latest = row;
							}
						}
						latest.transaction.setAmount(amountTotal);

						List<Row> toDelete = new ArrayList<Row>(selected);
						toDelete.remove(latest);

						try {
							//update the merged transaction
							dao.upsertPaymentTransaction(latest.transaction);

							//delete the other transactions
							for (Row row : toDelete) {
								dao.deletePaymentTransaction(row.transaction);
							}

							dao.commit();
						} catch (SQLException e) {
							dao.rollback();
							throw new RuntimeException(e);
						}

						//update table
						paymentsTable.model.data.removeAll(toDelete);
						paymentsTable.model.fireTableDataChanged();

						merge.setEnabled(false);

						owner.updatePaymentsCount();
					}
				});
				shrinkFont(merge);
				inner.add(merge, "wrap");

				paymentsTable = new PaymentsTable(pendingPayments);
				MyJScrollPane scrollPane = new MyJScrollPane(paymentsTable);
				inner.add(scrollPane, "h 100%, w 100%");

				add(inner, "align center, w :700:700, h 100%");
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
		CHECKBOX(""), SPLIT("Split"), ASSIGN("Assign"), CHAT_LOG("Log"), TIME("Time"), PLAYER("Player"), AMOUNT("Amount");

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

			//change to the "hand" cursor when the user mouses-over the "split" or "assign" columns
			addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent event) {
					int index = convertColumnIndexToModel(columnAtPoint(event.getPoint()));
					if (index < 0) {
						return;
					}

					Cursor cursor;
					Column column = columns[index];
					switch (column) {
					case SPLIT:
					case ASSIGN:
					case CHAT_LOG:
						cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
						break;
					default:
						cursor = Cursor.getDefaultCursor();
						break;
					}

					setCursor(cursor);
				}
			});
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

		private TableRowSorter<Model> createRowSorter() {
			TableRowSorter<Model> rowSorter = new TableRowSorter<Model>(model);

			rowSorter.setSortable(Column.CHECKBOX.ordinal(), false);
			rowSorter.setSortable(Column.SPLIT.ordinal(), false);
			rowSorter.setSortable(Column.ASSIGN.ordinal(), false);
			rowSorter.setSortable(Column.CHAT_LOG.ordinal(), false);
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

			private final ImageIcon assignIcon = Images.ASSIGN;
			private final ImageIcon splitIcon = Images.SPLIT;
			private final ImageIcon chatIcon = Images.CHAT;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, final int col) {
				if (value == null) {
					return null;
				}

				final Row rowObj = (Row) value;
				PaymentTransaction transaction = rowObj.transaction;
				Column column = columns[col];
				resetComponents();

				JComponent component = null;
				switch (column) {
				case SPLIT:
					component = label;

					label.setIcon(splitIcon);
					label.setHorizontalAlignment(SwingConstants.CENTER);
					break;

				case ASSIGN:
					component = label;

					label.setIcon(assignIcon);
					label.setHorizontalAlignment(SwingConstants.CENTER);
					break;

				case CHAT_LOG:
					component = label;

					label.setIcon(chatIcon);
					label.setHorizontalAlignment(SwingConstants.CENTER);
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

					final String playerName = transaction.getPlayer();
					playerPanel.setPlayer(playerName, new ProfileDownloadedListener() {
						@Override
						public void onProfileDownloaded(PlayerProfile profile) {
							//re-render all cells with this player name when the profile is downloaded
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									for (int i = 0; i < model.getRowCount(); i++) {
										Row r = model.data.get(i);
										String name = r.transaction.getPlayer();
										if (playerName.equalsIgnoreCase(name)) {
											model.fireTableCellUpdated(i, col);
										}
									}
								}
							});
						}
					});
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
				label.setIcon(null);
				label.setText(null);
				label.setHorizontalAlignment(SwingConstants.LEFT);

				playerPanel.setForeground(UIDefaultsWrapper.getLabelForeground());
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

			case CHAT_LOG:
				showChatLog(row);
				break;

			default:
				Row rowObj = model.data.get(row);
				rowObj.selected = !rowObj.selected;

				//re-render table row
				model.fireTableRowsUpdated(row, row);
			}

			List<Row> selected = getSelected();

			boolean enableMerge = true;
			if (selected.size() < 2) {
				//at least two rows must be selected
				enableMerge = false;
			} else {
				//are all the player names the same?
				String name = null;
				for (Row r : selected) {
					if (name == null) {
						name = r.transaction.getPlayer();
					} else if (!name.equalsIgnoreCase(r.transaction.getPlayer())) {
						enableMerge = false;
						break;
					}
				}
			}
			merge.setEnabled(enableMerge);

			boolean enableDelete = !selected.isEmpty();
			delete.setEnabled(enableDelete);
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

			TableColumn chatColumn = getColumn(Column.CHAT_LOG);
			chatColumn.setMaxWidth(50);
			chatColumn.setResizable(false);

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
			String player = transaction.getPlayer();
			if (result.isMyShop()) {
				shopTransaction.setPlayer(player);
			} else {
				shopTransaction.setShopOwner(player);
			}
			shopTransaction.setAmount(transaction.getAmount());
			shopTransaction.setBalance(transaction.getBalance());
			shopTransaction.setItem(result.getItemName());

			int quantity = result.getQuantity();
			if (transaction.getAmount() > 0) {
				//negate quantity if it's a sale
				quantity *= -1;
			}
			shopTransaction.setQuantity(quantity);

			boolean updateInventory = result.isMyShop() && result.isUpdateInventory();

			try {
				dao.insertTransaction(shopTransaction, updateInventory);
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

		private void showChatLog(int row) {
			PaymentTransaction transaction = model.data.get(row).transaction;
			IChatLogViewerView view = new ChatLogViewerViewImpl(owner);
			IChatLogViewerModel model = new ChatLogViewerModelImpl(transaction);
			new ChatLogViewerPresenter(view, model);
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
		private final RelativeDateFormat df = new RelativeDateFormat();
		private final ItemSuggestField item;
		private final QuantityTextField quantity;
		private final JButton ok, cancel;
		private final JCheckBox updateInventory;
		private final JLabel updateInventoryHelp;
		private final TransactionTypeComboBox transactionType;
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

			ok = new JButton("OK");
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
			updateInventoryHelp = new HelpLabel("", "Check this box to apply this transaction to your shop's inventory.");

			transactionType = new TransactionTypeComboBox();
			transactionType.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent e) {
					boolean visible = transactionType.isMyShopSelected();
					updateInventory.setVisible(visible);
					updateInventoryHelp.setVisible(visible);
				}
			});

			JLabel quantityLabel = new HelpLabel("Qty:", "In addition to specifying the exact number of items, you can also specify the quantity in stacks.\n\n<b>Examples</b>:\n\"264\" (264 items total)\n\"4/10\" (4 stacks, plus 10 more)\n\"4/\" (4 stacks)\n\nNote that <b>stack size varies depending on the item</b>!  Most items can hold 64 in a stack, but some can only hold 16 (like Signs) and others are not stackable at all (like armor)!");

			/////////////////////////

			setLayout(new MigLayout());

			JPanel top = new GroupPanel("");
			top.setBackground(UIDefaultsWrapper.getListBackgroundUnselected());
			top.add(new JLabel("<html><b>Time:"));
			top.add(new JLabel("<html><b>Player:"));
			top.add(new JLabel("<html><b>Amount:"), "wrap");

			top.add(new JLabel(df.format(paymentTransaction.getTs())), "gapright 10");
			PlayerCellPanel p = new PlayerCellPanel();
			p.setPlayer(paymentTransaction.getPlayer());
			p.setOpaque(false);
			top.add(p, "gapright 10");
			top.add(new JLabel("<html>" + formatRupeesWithColor(paymentTransaction.getAmount())));

			add(top, "wrap");

			JPanel bottom = new JPanel(new MigLayout("insets 0"));

			bottom.add(new JLabel("Item:"));
			bottom.add(quantityLabel, "wrap");
			bottom.add(item, "w 70%");
			bottom.add(quantity, "w 75:30%:, wrap");
			add(bottom, "gaptop 20, w 100%, wrap");

			JPanel bottom2 = new JPanel(new MigLayout("insets 0"));
			bottom2.add(new JLabel("Transaction Type:"), "wrap");
			bottom2.add(transactionType, "split 3");
			bottom2.add(updateInventory);
			bottom2.add(updateInventoryHelp);
			add(bottom2, "wrap");

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

			return new Result(itemName, quantity, dialog.transactionType.isMyShopSelected(), dialog.updateInventory.isSelected());
		}

		public static class Result {
			private final String itemName;
			private final Integer quantity;
			private final boolean myShop, updateInventory;

			public Result(String itemName, Integer quantity, boolean myShop, boolean updateInventory) {
				this.itemName = itemName;
				this.quantity = quantity;
				this.myShop = myShop;
				this.updateInventory = updateInventory;
			}

			public String getItemName() {
				return itemName;
			}

			public Integer getQuantity() {
				return quantity;
			}

			public boolean isMyShop() {
				return myShop;
			}

			public boolean isUpdateInventory() {
				return updateInventory;
			}
		}

		private static class TransactionTypeComboBox extends JComboBox {
			private static final String MY_SHOP = "My Shop";
			private static final String OTHER_SHOP = "Other Shop";

			public TransactionTypeComboBox() {
				super(new String[] { MY_SHOP, OTHER_SHOP });
			}

			public boolean isMyShopSelected() {
				return getSelectedItem() == MY_SHOP;
			}
		}
	}
}
