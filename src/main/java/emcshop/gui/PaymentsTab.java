package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;
import emcshop.ItemIndex;
import emcshop.db.DbDao;
import emcshop.gui.ProfileImageLoader.ImageDownloadedListener;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.ButtonColumn;
import emcshop.scraper.PaymentTransaction;
import emcshop.scraper.ShopTransaction;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class PaymentsTab extends JPanel {
	private final MainFrame owner;
	private final DbDao dao;
	private final ProfileImageLoader profileImageLoader;
	private final ItemIndex index = ItemIndex.instance();
	private final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
	private boolean stale = true;

	private final Icon deleteIcon = ImageManager.getImageIcon("delete.png");
	private final Icon assignIcon = ImageManager.getImageIcon("assign.png");
	private final Icon splitIcon = ImageManager.getImageIcon("split.png");

	private PaymentsTable paymentsTable;

	public PaymentsTab(final MainFrame owner, final DbDao dao, final ProfileImageLoader profileImageLoader) {
		this.owner = owner;
		this.dao = dao;
		this.profileImageLoader = profileImageLoader;

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
		private final List<PaymentTransaction> rows;
		private Column prevColumnClicked;
		private boolean ascending;

		private final Color evenRowColor = new Color(255, 255, 255);
		private final Color oddRowColor = new Color(240, 240, 240);

		public PaymentsTable(final List<PaymentTransaction> rows) {
			this.rows = rows;
			prevColumnClicked = Column.TIME;
			ascending = false;

			setModel();
			setColumns();

			getTableHeader().setReorderingAllowed(false);
			setColumnSelectionAllowed(false);
			setRowSelectionAllowed(false);
			setCellSelectionEnabled(false);
			setRowHeight(24);

			//allow columns to be sorted by clicking on the headers
			getTableHeader().addMouseListener(new MouseAdapter() {
				private final Column columns[] = Column.values();

				@Override
				public void mouseClicked(MouseEvent e) {
					int index = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
					if (index < 0) {
						return;
					}

					Column column = columns[index];
					if (column == Column.DELETE || column == Column.ASSIGN || column == Column.SPLIT) {
						return;
					}

					if (column == prevColumnClicked) {
						ascending = !ascending;
					} else {
						prevColumnClicked = column;
						ascending = true;
					}

					sortData();
				}
			});

			setDefaultRenderer(PaymentTransaction.class, new TableCellRenderer() {
				private final Column columns[] = Column.values();

				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, final int col) {
					PaymentTransaction transaction = (PaymentTransaction) value;
					Column column = columns[col];

					JLabel label = null;
					switch (column) {
					case TIME:
						label = new JLabel("<html>" + df.format(transaction.getTs()) + "</html>");
						break;
					case PLAYER:
						label = new JLabel("<html>" + transaction.getPlayer() + "</html>"); //add player's profile image
						profileImageLoader.load(transaction.getPlayer(), label, 16, new ImageDownloadedListener() {
							@Override
							public void onImageDownloaded(JLabel label) {
								AbstractTableModel model = (AbstractTableModel) getModel();
								model.fireTableCellUpdated(row, col);
							}
						});
						break;
					case AMOUNT:
						label = new JLabel("<html>" + formatRupeesWithColor(transaction.getAmount()) + "</html>");
						break;
					default:
						label = new JLabel();
					}

					//set the background color of the row
					Color color = (row % 2 == 0) ? evenRowColor : oddRowColor;
					label.setOpaque(true);
					label.setBackground(color);

					return label;
				}
			});
		}

		private void sortData() {
			Collections.sort(rows, new Comparator<PaymentTransaction>() {
				@Override
				public int compare(PaymentTransaction a, PaymentTransaction b) {
					if (!ascending) {
						PaymentTransaction temp = a;
						a = b;
						b = temp;
					}

					switch (prevColumnClicked) {
					case TIME:
						return a.getTs().compareTo(b.getTs());
					case PLAYER:
						return a.getPlayer().compareToIgnoreCase(b.getPlayer());
					case AMOUNT:
						return a.getAmount() - b.getAmount();
					default:
						return 0;
					}
				}
			});
			refresh();
		}

		private void refresh() {
			//AbstractTableModel model = (AbstractTableModel) getModel();
			//model.fireTableStructureChanged();

			setModel();
			setColumns();
		}

		private void setModel() {
			setModel(new AbstractTableModel() {
				private final Column columns[] = Column.values();

				@Override
				public int getColumnCount() {
					return columns.length;
				}

				@Override
				public String getColumnName(int index) {
					Column column = columns[index];

					String text = column.getName();
					if (prevColumnClicked == column) {
						String arrow = (ascending) ? "\u25bc" : "\u25b2";
						text = arrow + " " + text;
					}
					return text;
				}

				@Override
				public int getRowCount() {
					return rows.size();
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
						return rows.get(row);
					}
				}

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
			});
		}

		private void setColumns() {
			new ButtonColumn(this, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent event) {
					int result = JOptionPane.showConfirmDialog(owner, "Are you sure you want to delete this payment transaction?", "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
					if (result != JOptionPane.YES_OPTION) {
						return;
					}

					int row = Integer.valueOf(event.getActionCommand());
					PaymentTransaction transaction = rows.get(row);

					try {
						dao.ignorePaymentTransaction(transaction.getId());
						dao.commit();
					} catch (SQLException e) {
						dao.rollback();
						throw new RuntimeException(e);
					}

					removeRow(transaction);
					owner.updatePaymentsCount();
					refresh();
				}
			}, Column.DELETE.ordinal());

			new ButtonColumn(this, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent event) {
					int row = Integer.valueOf(event.getActionCommand());
					PaymentTransaction transaction = rows.get(row);

					AssignDialog dialog = new AssignDialog(transaction);
					dialog.setVisible(true);
				}
			}, Column.ASSIGN.ordinal());

			new ButtonColumn(this, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent event) {
					int row = Integer.valueOf(event.getActionCommand());
					PaymentTransaction transaction = rows.get(row);

					Integer splitAmount = getSplitAmount(transaction);
					if (splitAmount == null) {
						//user canceled the dialog
						return;
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

					//add row to table
					rows.add(row + 1, splitTransaction);

					owner.updatePaymentsCount();
					refresh();
				}

				private Integer getSplitAmount(PaymentTransaction transaction) {
					int origAmount = transaction.getAmount();
					int origAmountAbs = Math.abs(origAmount);
					do {
						String amountStr = JOptionPane.showInputDialog(owner, "Enter the number of rupees you'd like to subtract\nfrom this payment transaction.  A new payment transaction\nwill then be created with the value you enter.\n\nEnter a value between 1 and " + (origAmountAbs - 1) + ":", "Split Payment Transaction", JOptionPane.QUESTION_MESSAGE);
						if (amountStr == null) {
							//user canceled dialog
							return null;
						}

						String error = null;
						try {
							Integer amountInt = Integer.valueOf(amountStr);
							if (amountInt <= 0 || amountInt >= origAmountAbs) {
								error = "Amount must be between 1 and " + (origAmountAbs - 1) + ".";
							} else {
								if (origAmount < 0) {
									amountInt *= -1;
								}
								return amountInt;
							}
						} catch (NumberFormatException e) {
							error = "Invalid number.";
						}
						JOptionPane.showMessageDialog(owner, error, "Error", JOptionPane.ERROR_MESSAGE);
					} while (true);
				}
			}, Column.SPLIT.ordinal());

			columnModel.getColumn(Column.DELETE.ordinal()).setMaxWidth(50);
			columnModel.getColumn(Column.ASSIGN.ordinal()).setMaxWidth(50);
			columnModel.getColumn(Column.SPLIT.ordinal()).setMaxWidth(50);
		}

		private void removeRow(PaymentTransaction transaction) {
			//do not use rows.remove() because PaymentTransaction has an equals() method which could cause it to remove the wrong row
			for (int i = 0; i < rows.size(); i++) {
				PaymentTransaction t = rows.get(i);
				if (t == transaction) {
					rows.remove(i);
					break;
				}
			}
		}
	}

	private class AssignDialog extends JDialog {
		private final ItemSuggestField item;
		private final QuantityTextField quantity;
		private final JButton ok, cancel;
		private final JCheckBox updateInventory;

		public AssignDialog(final PaymentTransaction paymentTransaction) {
			super(owner, "Assign Payment Transaction", true);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			setResizable(false);
			GuiUtils.closeOnEscapeKeyPress(this);

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
						JOptionPane.showMessageDialog(AssignDialog.this, "No item specified.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					if (quantity.getText().isEmpty()) {
						JOptionPane.showMessageDialog(AssignDialog.this, "No quantity specified.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					String itemName = item.getText();
					Integer qty;
					try {
						qty = quantity.getQuantity(index.getStackSize(itemName));
						qty = Math.abs(qty); //incase the user enters a negative value
					} catch (NumberFormatException e) {
						JOptionPane.showMessageDialog(AssignDialog.this, "Invalid quantity value.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					ShopTransaction shopTransaction = new ShopTransaction();
					shopTransaction.setTs(paymentTransaction.getTs());
					shopTransaction.setPlayer(paymentTransaction.getPlayer());
					shopTransaction.setAmount(paymentTransaction.getAmount());
					shopTransaction.setBalance(paymentTransaction.getBalance());
					shopTransaction.setItem(itemName);

					if (paymentTransaction.getAmount() > 0) {
						//negate quantity if it's a sale
						qty *= -1;
					}
					shopTransaction.setQuantity(qty);

					try {
						dao.insertTransaction(shopTransaction, updateInventory.isSelected());
						dao.assignPaymentTransaction(paymentTransaction.getId(), shopTransaction.getId());
						dao.commit();
					} catch (SQLException e) {
						dao.rollback();
						throw new RuntimeException(e);
					}

					paymentsTable.removeRow(paymentTransaction);
					paymentsTable.refresh();
					owner.updateInventoryTab();
					owner.updatePaymentsCount();

					dispose();
				}
			});

			cancel = new JButton("Cancel");
			cancel.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					dispose();
				}
			});

			updateInventory = new JCheckBox("Apply to Inventory", true);

			JLabel quantityLabel = new HelpLabel("Qty:", "You can specify the quantity in stacks instead of having to specify the exact number.\n\n<b>Examples</b>:\n\"264\" (264 items total)\n\"4/10\" (4 stacks, plus 10 more)\n\"4/\" (4 stacks)\n\nNote that <b>stack size varies depending on the item</b>!  Most items can hold 64 in a stack, but some can only hold 16 (like Signs) and others are not stackable at all (like armor)!");

			/////////////////////////

			setLayout(new MigLayout("insets 0"));

			JPanel top = new JPanel(new MigLayout());

			top.add(new JLabel("Time:"));
			top.add(new JLabel("Player:"));
			top.add(new JLabel("Amount:"), "wrap");

			top.add(new JLabel(df.format(paymentTransaction.getTs())), "gapright 10");
			top.add(new JLabel(paymentTransaction.getPlayer()), "gapright 10");
			top.add(new JLabel("<html>" + formatRupeesWithColor(paymentTransaction.getAmount()) + "</html>"));

			add(top, "wrap");

			JPanel bottom = new JPanel(new MigLayout());

			bottom.add(new JLabel("Item:"));
			bottom.add(quantityLabel, "wrap");
			bottom.add(item, "w 175");
			bottom.add(quantity, "w 100, wrap");

			add(bottom, "wrap");

			bottom.add(updateInventory, "split 2");
			bottom.add(new HelpLabel("", "Check this box to apply this transaction to your shop's inventory."), "wrap");
			add(ok, "split 2, align center");
			add(cancel);

			pack();
			setLocationRelativeTo(owner);
		}
	}
}
