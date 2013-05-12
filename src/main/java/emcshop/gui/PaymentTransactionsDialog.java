package emcshop.gui;

import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;
import emcshop.PaymentTransaction;
import emcshop.ShopTransaction;
import emcshop.db.DbDao;

/**
 * Displays the pending payment transactions.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class PaymentTransactionsDialog extends JDialog {
	private PaymentTransactionsDialog(Window owner, final DbDao dao) throws SQLException {
		super(owner, "Payment Transactions");
		setModalityType(ModalityType.DOCUMENT_MODAL); //go on top of all windows

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		//close when escape is pressed
		getRootPane().registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		//@formatter:off
		JLabel description = new JLabel(
		"<html>" +
			"<div width=100%>" +
				"The following direct payment transactions are awaiting your review. " +
				"These kinds of transactions occur when you manually give or receive rupees to/from a player (with the \"/r pay\" command).<br>" +
				"<br>" +
				"This screen allows you to specify whether any of these transactions were purchases/sales that you made for your shop (for example, selling someone items in bulk).  Transactions that you don't ignore or assign will remain here so you can review them later." +
			"</div>" +
		"</html>"
		);
		//@formatter:on

		List<PaymentTransaction> pendingPayments = dao.getPendingPaymentTransactions();
		final PaymentsPanel panel = new PaymentsPanel(pendingPayments);

		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {

				try {
					Map<PaymentTransaction, ShopTransaction> toAdd = panel.getShopTransactionsToAdd();
					for (Map.Entry<PaymentTransaction, ShopTransaction> entry : toAdd.entrySet()) {
						PaymentTransaction payment = entry.getKey();
						ShopTransaction shop = entry.getValue();
						dao.insertTransaction(shop);
						dao.assignPaymentTransaction(payment.getId(), shop.getId());
					}

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
		add(description, "wrap");

		MyJScrollPane scrollPane = new MyJScrollPane(panel);
		add(scrollPane, "grow, w 100%, h 100%, wrap");
		add(save, "split 2, align center");
		add(cancel);

		setSize(625, 500);
		setLocationRelativeTo(owner);
	}

	private class PaymentsPanel extends JPanel {
		final List<RowGroup> rowGroups;

		PaymentsPanel(List<PaymentTransaction> transactions) {
			setLayout(new MigLayout("insets 0"));

			rowGroups = new ArrayList<RowGroup>(transactions.size());
			for (PaymentTransaction transaction : transactions) {
				rowGroups.add(new RowGroup(transaction));
			}

			for (RowGroup group : rowGroups) {
				add(group.description);

				add(group.ignore, "split 2");
				add(group.assign, "wrap");
				add(new JLabel(""));
				add(group.assignPanel, "wrap");
			}
		}

		Map<PaymentTransaction, ShopTransaction> getShopTransactionsToAdd() {
			Map<PaymentTransaction, ShopTransaction> toAdd = new HashMap<PaymentTransaction, ShopTransaction>();

			for (RowGroup rowGroup : rowGroups) {
				if (!rowGroup.assignTransaction) {
					continue;
				}

				String item = rowGroup.item.getText();
				if (item.isEmpty()) {
					continue;
				}

				int quantity = Integer.parseInt(rowGroup.quantity.getText());

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
				if (rowGroup.ignoreTransaction) {
					toIgnore.add(rowGroup.transaction);
				}
			}

			return toIgnore;
		}
	}

	private class RowGroup {
		PaymentTransaction transaction;
		JLabel description;
		JButton ignore, assign;
		JPanel assignPanel, innerAssignPanel;
		JTextField item, quantity;
		DateFormat df = new SimpleDateFormat("MMM dd yyyy @ HH:mm");
		boolean ignoreTransaction = false;
		boolean assignTransaction = false;

		RowGroup(PaymentTransaction transaction) {
			this.transaction = transaction;

			description = new JLabel();

			ignore = new JButton();
			ignore.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ignoreTransaction = !ignoreTransaction;
					ignore(ignoreTransaction);
					if (assignTransaction) {
						assignTransaction = false;
						assign(assignTransaction);
					}
				}
			});

			assign = new JButton();
			assign.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					assignTransaction = !assignTransaction;
					assign(assignTransaction);
					if (ignoreTransaction) {
						ignoreTransaction = false;
						ignore(ignoreTransaction);
					}
				}
			});

			item = new JTextField(); //TODO auto-complete
			quantity = new JTextField(); //TODO validate

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
			String strikeStart, strikeEnd;
			if (ignore) {
				strikeStart = "<strike>";
				strikeEnd = "</strike>";
				this.ignore.setText("Unignore");
			} else {
				strikeStart = strikeEnd = "";
				this.ignore.setText("Ignore");
			}

			int amount = transaction.getAmount();
			String fromTo = (amount < 0) ? "to" : "from";
			description.setText("<html>" + strikeStart + df.format(transaction.getTs()) + " | Payment " + fromTo + " " + transaction.getPlayer() + ": " + formatRupeesWithColor(amount) + strikeEnd + "</html>");
		}

		void assign(boolean assign) {
			if (assign) {
				this.assign.setText("Cancel");
				assignPanel.add(innerAssignPanel);
			} else {
				this.assign.setText("Assign");
				assignPanel.remove(innerAssignPanel);
			}
			assignPanel.validate();
		}
	}

	public static void show(Window owner, DbDao dao) throws SQLException {
		new PaymentTransactionsDialog(owner, dao).setVisible(true);
	}
}
