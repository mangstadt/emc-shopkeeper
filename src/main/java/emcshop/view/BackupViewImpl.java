package emcshop.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.HelpLabel;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.JNumberTextField;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class BackupViewImpl extends JDialog implements IBackupView {
	private final JButton ok, cancel, backupNow;
	private final JCheckBox enabled;
	private final JLabel loading;
	private final JNumberTextField frequency, max;
	private final SettingsPanel settingsPanel;

	public BackupViewImpl(Window owner) {
		super(owner, "Database Backup Settings", ModalityType.APPLICATION_MODAL);
		setResizable(false);
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		GuiUtils.addCloseDialogListener(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (cancel.isEnabled()) {
					cancel.doClick();
				}
			}
		});
		GuiUtils.onEscapeKeyPress(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (cancel.isEnabled()) {
					cancel.doClick();
				}
			}
		});

		ok = new JButton("OK");

		cancel = new JButton("Cancel");

		backupNow = new JButton("Backup Now");
		backupNow.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				loading.setText("0%");
				loading.setVisible(true);

				cancel.setEnabled(false);
				ok.setEnabled(false);
				enabled.setEnabled(false);
				settingsPanel.setEnabled(false);
				backupNow.setEnabled(false);
			}
		});

		loading = new JLabel(ImageManager.getLoadingSmall(), SwingConstants.LEFT);
		loading.setVisible(false);

		enabled = new JCheckBox("Enable automatic database backups");
		enabled.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent arg0) {
				settingsPanel.setEnabled(enabled.isSelected());
			}
		});
		frequency = new JNumberTextField();
		max = new JNumberTextField();

		///////////////////////////////////////

		setLayout(new MigLayout());
		add(backupNow);
		add(new JLabel("<html><div width=300>The database holds all of your transaction data, including your payment transactions and inventory.  It can be backed up incase it is corrupted in some way.</div></html>"), "span 1 2, wrap");
		add(loading, "align center, wrap");

		add(new JSeparator(), "w 100%, span 2, wrap");

		add(enabled, "split 2, span 2");
		add(new HelpLabel("", "Automatic backups occur while EMC Shopkeeper is starting up.  They typically take 5-10 seconds to complete."), "wrap");
		settingsPanel = new SettingsPanel();
		add(settingsPanel, "span 2, gapleft 15, wrap");

		add(ok, "split 2, span 2, align center");
		add(cancel);

		pack();
		setLocationRelativeTo(owner);
	}

	@Override
	public void addStartBackupListener(ActionListener listener) {
		backupNow.addActionListener(listener);
	}

	@Override
	public void addRestoreBackupListener(ActionListener listener) {
		//TODO
	}

	@Override
	public void addSaveSettingsListener(ActionListener listener) {
		ok.addActionListener(listener);
		frequency.addActionListener(listener);
		max.addActionListener(listener);
	}

	@Override
	public void addCancelListener(ActionListener listener) {
		cancel.addActionListener(listener);
	}

	@Override
	public boolean getAutoBackupEnabled() {
		return enabled.isSelected();
	}

	@Override
	public Integer getBackupFrequency() {
		return frequency.getInteger();
	}

	@Override
	public Integer getMaxBackups() {
		return max.getInteger();
	}

	@Override
	public void setAutoBackupEnabled(boolean enabled) {
		this.enabled.setSelected(enabled);
		settingsPanel.setEnabled(enabled);
	}

	@Override
	public void setBackupFrequency(Integer days) {
		frequency.setNumber(days);
	}

	@Override
	public void setMaxBackups(Integer days) {
		max.setNumber(days);
	}

	@Override
	public void setBackups(List<Date> backups) {
		//TODO
	}

	@Override
	public void setBackupPercentComplete(double percent) {
		int percentInt = (int) percent;
		loading.setText(percentInt + "%");
	}

	@Override
	public void backupComplete() {
		loading.setVisible(false);

		ok.setEnabled(true);
		cancel.setEnabled(true);
		enabled.setEnabled(true);
		if (enabled.isSelected()) {
			settingsPanel.setEnabled(true);
		}
		backupNow.setEnabled(true);

		JOptionPane.showMessageDialog(this, "Backup complete.", "Backup complete", JOptionPane.INFORMATION_MESSAGE);
	}

	@Override
	public void invalidFrequency() {
		JOptionPane.showMessageDialog(this, "The backup frequency must be 1 or greater.", "Error", JOptionPane.ERROR_MESSAGE);
		frequency.requestFocus();
	}

	@Override
	public void invalidMax() {
		JOptionPane.showMessageDialog(this, "The max number of backups must be 1 or greater.", "Error", JOptionPane.ERROR_MESSAGE);
		max.requestFocus();
	}

	@Override
	public void display() {
		setVisible(true);
	}

	@Override
	public void close() {
		dispose();
	}

	private class SettingsPanel extends JPanel {
		private final Color enabledColor = new JLabel().getForeground();
		private final Color disabledColor = new Color(128, 128, 128);

		public SettingsPanel() {
			super(new MigLayout("insets 0"));

			add(new JSeparator(SwingConstants.VERTICAL), "h 100%, span 1 2");

			add(new JLabel("Backup every"), "split 3");
			add(frequency, "w 50");
			add(new JLabel("days."), "wrap");

			add(new JLabel("Keep a maximum of"), "split 3");
			add(max, "w 50");
			add(new JLabel("backups."));
		}

		@Override
		public void setEnabled(boolean enabled) {
			super.setEnabled(enabled);
			for (Component component : getComponents()) {
				if (component instanceof JLabel) {
					JLabel label = (JLabel) component;
					label.setForeground(enabled ? enabledColor : disabledColor);
				}
				component.setEnabled(enabled);
			}
		}
	}
}
