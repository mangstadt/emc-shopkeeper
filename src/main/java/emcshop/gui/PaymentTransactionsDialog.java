package emcshop.gui;

import static emcshop.util.MiscUtils.toolTipText;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
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
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;
import emcshop.PaymentTransaction;
import emcshop.ShopTransaction;
import emcshop.db.DbDao;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.JNumberTextField;
import emcshop.gui.lib.suggest.ContainsMatcher;
import emcshop.gui.lib.suggest.JSuggestField;

/**
 * Displays the pending payment transactions.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class PaymentTransactionsDialog extends JDialog {
	private final Vector<String> itemNames;
	private final Map<String, JLabel> itemIconLabels = new HashMap<String, JLabel>();
	private final DateFormat df = new SimpleDateFormat("MMM dd yyyy @ HH:mm");
	private final Color selectedItemBgColor = new Color(192, 192, 192);
	private final Color erroredFieldBgColor = new Color(255, 192, 192);

	private PaymentTransactionsDialog(Window owner, final DbDao dao) throws SQLException {
		super(owner, "Payment Transactions");
		setModalityType(ModalityType.DOCUMENT_MODAL); //go on top of all windows
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		//@formatter:off
		JLabel description = new JLabel(
		"<html>" +
			"<div width=100%>" +
				"The following payment transactions are awaiting your review." +
			"</div>" +
		"</html>"
		);
		//@formatter:on

		JLabel help = new JLabel(ImageManager.getHelpIcon());
		//@formatter:off
		help.setToolTipText(toolTipText(
		"<b>Payment Transactions</b><br>" +
		"<br>" +
		"This screen allows you to specify whether any payment transactions were shop-related (for example, selling someone items in bulk).<br>" +
		"<br>" +
		"A <b>payment transaction</b> occurs when a player gives rupees to another player using the <code>\"/r pay\"</code> command."
		));
		//@formatter:on

		//build labels for item icons
		List<String> itemNames = dao.getItemNames();
		this.itemNames = new Vector<String>(itemNames);
		for (String itemName : itemNames) {
			ImageIcon image = ImageManager.getItemImage(itemName);
			JLabel label = new JLabel(itemName, image, SwingConstants.LEFT);
			itemIconLabels.put(itemName, label);
		}

		List<PaymentTransaction> pendingPayments = dao.getPendingPaymentTransactions();
		final PaymentsPanel panel = new PaymentsPanel(pendingPayments);

		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				for (RowGroup group : panel.rowGroups) {
					if (!group.validate()) {
						JOptionPane.showMessageDialog(PaymentTransactionsDialog.this, "One or more input fields are empty.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
				}

				try {
					//upsert any split payment transactions
					List<PaymentTransaction> toUpsert = panel.getPaymentTransactionsToUpsert();
					for (PaymentTransaction t : toUpsert) {
						dao.upsertPaymentTransaction(t);
					}

					//create new shop transactions
					Map<PaymentTransaction, ShopTransaction> toAdd = panel.getShopTransactionsToAdd();
					for (Map.Entry<PaymentTransaction, ShopTransaction> entry : toAdd.entrySet()) {
						PaymentTransaction payment = entry.getKey();
						ShopTransaction shop = entry.getValue();
						dao.insertTransaction(shop);
						dao.assignPaymentTransaction(payment.getId(), shop.getId());
					}

					//ignore payment transactions
					List<PaymentTransaction> toIgnore = panel.getPaymentTransactionsToIgnore();
					for (PaymentTransaction t : toIgnore) {
						dao.ignorePaymentTransaction(t.getId());
					}

					dao.commit();
					dispose();
				} catch (SQLException e) {
					dao.rollback();
					ErrorDialog.show(PaymentTransactionsDialog.this, "Problem updating payment transactions in the database.", e);
				}
			}
		});

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		});

		setLayout(new MigLayout());
		add(help, "split 2");
		add(description, "wrap");

		MyJScrollPane scrollPane = new MyJScrollPane(panel);
		add(scrollPane, "grow, w 100%, h 100%, wrap");
		add(save, "split 2, align center");
		add(cancel);

		setSize(750, 500);
		setLocationRelativeTo(owner);
	}

	private class PaymentsPanel extends JPanel {
		final List<RowGroup> rowGroups;

		PaymentsPanel(List<PaymentTransaction> transactions) {
			setLayout(new MigLayout("insets 0"));

			rowGroups = new ArrayList<RowGroup>();
			for (PaymentTransaction transaction : transactions) {
				rowGroups.add(new RowGroup(transaction, this));
			}
			refresh();
		}

		void insertRowGroup(RowGroup origGroup, RowGroup newGroup) {
			int index = rowGroups.indexOf(origGroup);
			rowGroups.add(index + 1, newGroup);
			refresh();
		}

		void refresh() {
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

		Map<PaymentTransaction, ShopTransaction> getShopTransactionsToAdd() {
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

		List<PaymentTransaction> getPaymentTransactionsToIgnore() {
			List<PaymentTransaction> toIgnore = new ArrayList<PaymentTransaction>();

			for (RowGroup rowGroup : rowGroups) {
				if (rowGroup.ignorePaymentTransaction) {
					toIgnore.add(rowGroup.transaction);
				}
			}

			return toIgnore;
		}

		List<PaymentTransaction> getPaymentTransactionsToUpsert() {
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
		final PaymentTransaction transaction;
		boolean ignorePaymentTransaction = false;
		boolean assignToShopTransaction = false;
		boolean upsertPaymentTransaction = false;

		final JLabel description;
		final JButton ignore, assign, split;
		final JPanel assignPanel, innerAssignPanel;
		final JNumberTextField quantity;
		final ItemSuggestField item;

		RowGroup(final PaymentTransaction transaction, final PaymentsPanel parent) {
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
						String amountStr = JOptionPane.showInputDialog(PaymentTransactionsDialog.this, "Enter the number of rupees you'd like to subtract\nfrom this payment transaction.  A new payment transaction\nwill then be created with the value you enter.\n\nEnter a value between 1 and " + (origAmountAbs - 1) + ":", "Split Payment Transaction", JOptionPane.QUESTION_MESSAGE);
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
						JOptionPane.showMessageDialog(PaymentTransactionsDialog.this, error, "Error", JOptionPane.ERROR_MESSAGE);
					} while (true);
				}
			});

			item = new ItemSuggestField(PaymentTransactionsDialog.this);
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

		void ignore(boolean ignore) {
			this.ignore.setText(ignore ? "Unignore" : "Ignore");
			updateDescription(ignore);
		}

		void assign(boolean assign) {
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
		boolean validate() {
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

		void updateDescription(boolean ignore) {
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

	private class ItemSuggestField extends JSuggestField {
		public ItemSuggestField(Window parent) {
			super(parent, itemNames);
			setSuggestMatcher(new ContainsMatcher());
			setListCellRenderer(new ListCellRenderer() {
				@Override
				public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
					String itemName = (String) value;
					JLabel label = itemIconLabels.get(itemName);
					label.setOpaque(isSelected);
					if (isSelected) {
						label.setBackground(selectedItemBgColor);
					}
					return label;
				}
			});
		}
	}

	public static void show(Window owner, DbDao dao) throws SQLException {
		new PaymentTransactionsDialog(owner, dao).setVisible(true);
	}
}
