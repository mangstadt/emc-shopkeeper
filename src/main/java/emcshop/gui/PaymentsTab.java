package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import emcshop.PaymentTransaction;
import emcshop.ShopTransaction;
import emcshop.db.DbDao;
import emcshop.gui.lib.JNumberTextField;

@SuppressWarnings("serial")
public class PaymentsTab extends JPanel {
	private final MainFrame owner;
	private final DbDao dao;
	private final DateFormat df = new SimpleDateFormat("MMM dd yyyy @ HH:mm");
	private boolean stale = true;
	private final Color erroredFieldBgColor = new Color(255, 192, 192);

	private final JButton save, reset;
	private PaymentsPanel paymentsPanel;

	public PaymentsTab(final MainFrame owner, final DbDao dao) {
		this.owner = owner;
		this.dao = dao;

		save = new JButton("Save");
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				for (RowGroup group : paymentsPanel.rowGroups) {
					if (!group.validate()) {
						JOptionPane.showMessageDialog(owner, "One or more input fields are empty.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
				}

				try {
					//upsert any split payment transactions
					List<PaymentTransaction> toUpsert = paymentsPanel.getPaymentTransactionsToUpsert();
					for (PaymentTransaction t : toUpsert) {
						dao.upsertPaymentTransaction(t);
					}

					//create new shop transactions
					Map<PaymentTransaction, ShopTransaction> toAdd = paymentsPanel.getShopTransactionsToAdd();
					for (Map.Entry<PaymentTransaction, ShopTransaction> entry : toAdd.entrySet()) {
						PaymentTransaction payment = entry.getKey();
						ShopTransaction shop = entry.getValue();
						dao.insertTransaction(shop);
						dao.assignPaymentTransaction(payment.getId(), shop.getId());
					}

					//ignore payment transactions
					List<PaymentTransaction> toIgnore = paymentsPanel.getPaymentTransactionsToIgnore();
					for (PaymentTransaction t : toIgnore) {
						dao.ignorePaymentTransaction(t.getId());
					}

					dao.commit();

					reset();
					owner.updatePaymentsCount(); //update the tab title
					owner.updateInventoryTab(); //update the inventory tab
				} catch (SQLException e) {
					dao.rollback();
					ErrorDialog.show(owner, "Problem updating payment transactions in the database.", e);
				}
			}
		});

		reset = new JButton("Reset");
		reset.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				reset();
			}
		});

		setLayout(new MigLayout("fillx, insets 5"));
	}

	private class PaymentsPanel extends JPanel {
		private final List<RowGroup> rowGroups;

		public PaymentsPanel(List<PaymentTransaction> transactions) {
			setLayout(new MigLayout("fillx, insets 0"));

			rowGroups = new ArrayList<RowGroup>();
			for (PaymentTransaction transaction : transactions) {
				rowGroups.add(new RowGroup(transaction, this));
			}
			refresh();
		}

		private void insertRowGroup(RowGroup origGroup, RowGroup newGroup) {
			int index = rowGroups.indexOf(origGroup);
			rowGroups.add(index + 1, newGroup);
			refresh();
		}

		private void refresh() {
			removeAll();
			for (RowGroup group : rowGroups) {
				add(group.description);

				add(group.ignore, "split 3");
				add(group.assign);
				add(group.split, "wrap");
				add(new JLabel(""));
				add(group.assignPanel, "wrap");
			}
			validate();
		}

		private Map<PaymentTransaction, ShopTransaction> getShopTransactionsToAdd() {
			Map<PaymentTransaction, ShopTransaction> toAdd = new HashMap<PaymentTransaction, ShopTransaction>();

			for (RowGroup rowGroup : rowGroups) {
				if (!rowGroup.assignToShopTransaction) {
					continue;
				}

				String item = rowGroup.item.getText();
				if (item.isEmpty()) {
					continue;
				}

				Integer quantity = rowGroup.quantity.getInteger();
				if (quantity == null) {
					continue;
				}

				ShopTransaction shop = new ShopTransaction();
				shop.setTs(rowGroup.transaction.getTs());
				shop.setPlayer(rowGroup.transaction.getPlayer());
				shop.setAmount(rowGroup.transaction.getAmount());
				shop.setBalance(rowGroup.transaction.getBalance());
				shop.setItem(item);

				if (rowGroup.transaction.getAmount() > 0) {
					//negate quantity if it's a sale
					quantity *= -1;
				}
				shop.setQuantity(quantity);

				toAdd.put(rowGroup.transaction, shop);
			}

			return toAdd;
		}

		private List<PaymentTransaction> getPaymentTransactionsToIgnore() {
			List<PaymentTransaction> toIgnore = new ArrayList<PaymentTransaction>();

			for (RowGroup rowGroup : rowGroups) {
				if (rowGroup.ignorePaymentTransaction) {
					toIgnore.add(rowGroup.transaction);
				}
			}

			return toIgnore;
		}

		private List<PaymentTransaction> getPaymentTransactionsToUpsert() {
			List<PaymentTransaction> toUpsert = new ArrayList<PaymentTransaction>();

			for (RowGroup rowGroup : rowGroups) {
				if (rowGroup.upsertPaymentTransaction) {
					toUpsert.add(rowGroup.transaction);
				}
			}

			return toUpsert;
		}
	}

	private class RowGroup {
		private final PaymentTransaction transaction;
		private boolean ignorePaymentTransaction = false;
		private boolean assignToShopTransaction = false;
		private boolean upsertPaymentTransaction = false;

		private final JLabel description;
		private final JButton ignore, assign, split;
		private final JPanel assignPanel, innerAssignPanel;
		private final JNumberTextField quantity;
		private final ItemSuggestField item;

		public RowGroup(final PaymentTransaction transaction, final PaymentsPanel parent) {
			this.transaction = transaction;

			description = new JLabel();

			ignore = new JButton();
			ignore.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ignorePaymentTransaction = !ignorePaymentTransaction;
					ignore(ignorePaymentTransaction);
					if (assignToShopTransaction) {
						assignToShopTransaction = false;
						assign(assignToShopTransaction);
					}
					split.setEnabled(!ignorePaymentTransaction);
				}
			});

			assign = new JButton();
			assign.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					assignToShopTransaction = !assignToShopTransaction;
					assign(assignToShopTransaction);
					if (ignorePaymentTransaction) {
						ignorePaymentTransaction = false;
						ignore(ignorePaymentTransaction);
					}
					split.setEnabled(!assignToShopTransaction);
				}
			});

			split = new JButton("Split");
			split.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Integer splitAmount = getSplitAmount();
					if (splitAmount == null) {
						//user canceled dialog
						return;
					}

					//update the existing payment transaction
					int origBalance = transaction.getBalance() - transaction.getAmount();
					transaction.setAmount(transaction.getAmount() - splitAmount);
					transaction.setBalance(origBalance + transaction.getAmount());
					upsertPaymentTransaction = true;
					updateDescription(ignorePaymentTransaction);

					//create the new payment transaction
					PaymentTransaction splitTransaction = new PaymentTransaction();
					splitTransaction.setAmount(splitAmount);
					splitTransaction.setBalance(transaction.getBalance() + splitAmount);
					splitTransaction.setPlayer(transaction.getPlayer());
					splitTransaction.setTs(transaction.getTs());

					RowGroup splitGroup = new RowGroup(splitTransaction, parent);
					splitGroup.upsertPaymentTransaction = true;
					parent.insertRowGroup(RowGroup.this, splitGroup);
				}

				private Integer getSplitAmount() {
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
			});

			item = new ItemSuggestField(owner);
			final Color origTextFieldBg = item.getBackground();
			item.addFocusListener(new FocusListener() {
				@Override
				public void focusGained(FocusEvent arg0) {
					//empty
				}

				@Override
				public void focusLost(FocusEvent arg0) {
					item.setBackground(origTextFieldBg);
				}
			});

			quantity = new JNumberTextField();
			quantity.setFormat(JNumberTextField.NUMERIC);
			quantity.setAllowNegative(false);
			quantity.addFocusListener(new FocusListener() {
				@Override
				public void focusGained(FocusEvent arg0) {
					//empty
				}

				@Override
				public void focusLost(FocusEvent arg0) {
					quantity.setBackground(origTextFieldBg);
				}
			});

			assignPanel = new JPanel(new MigLayout("insets 0"));

			innerAssignPanel = new JPanel(new MigLayout("insets 0"));
			innerAssignPanel.add(new JLabel("Item"));
			innerAssignPanel.add(item, "w 150!, wrap");
			innerAssignPanel.add(new JLabel("Qty"));
			innerAssignPanel.add(quantity, "w 50!");

			ignore(false);
			assign(false);
		}

		private void ignore(boolean ignore) {
			this.ignore.setText(ignore ? "Unignore" : "Ignore");
			updateDescription(ignore);
		}

		private void assign(boolean assign) {
			if (assign) {
				this.assign.setText("Cancel");
				item.setText("");
				quantity.setText("");
				assignPanel.add(innerAssignPanel);
			} else {
				this.assign.setText("Assign");
				assignPanel.remove(innerAssignPanel);
			}
			assignPanel.validate();
		}

		/**
		 * Validates the data.
		 * @return true if the data is valid, false if not
		 */
		private boolean validate() {
			boolean valid = true;
			if (assignToShopTransaction) {
				if (item.getText().isEmpty()) {
					item.setBackground(erroredFieldBgColor);
					valid = false;
				}
				if (quantity.getText().isEmpty()) {
					quantity.setBackground(erroredFieldBgColor);
					valid = false;
				}
			}
			return valid;
		}

		private void updateDescription(boolean ignore) {
			String strikeStart, strikeEnd;
			if (ignore) {
				strikeStart = "<strike>";
				strikeEnd = "</strike>";
			} else {
				strikeStart = strikeEnd = "";
			}

			int amount = transaction.getAmount();
			String fromTo = (amount < 0) ? "to" : "from";
			description.setText("<html>" + strikeStart + df.format(transaction.getTs()) + " | Payment " + fromTo + " <b>" + transaction.getPlayer() + "</b>: " + formatRupeesWithColor(amount) + strikeEnd + "</html>");
		}
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
				paymentsPanel = new PaymentsPanel(pendingPayments);

				add(save, "split 2");
				add(reset, "wrap");
				MyJScrollPane scrollPane = new MyJScrollPane(paymentsPanel);
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
}
