package emcshop.view;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.images.ImageManager;
import emcshop.util.GuiUtils;

@SuppressWarnings("serial")
public class ProfileSelectorViewImpl extends JDialog implements ProfileSelectorView {
	private final JComboBox profiles;
	private final JButton ok, quit;

	public ProfileSelectorViewImpl(Window owner) {
		super(owner);
		setTitle("Choose Profile");
		setResizable(false);
		setModal(true);

		profiles = new JComboBox();
		profiles.setEditable(true);

		ok = new JButton("OK");
		quit = new JButton("Quit");

		//fire all listeners attached to the quit button when escape is pressed
		GuiUtils.onEscapeKeyPress(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (ActionListener listener : quit.getActionListeners()) {
					listener.actionPerformed(e);
				}
			}
		});

		///////////////////////

		setLayout(new MigLayout());

		add(new JLabel(ImageManager.getImageIcon("header.png")), "align center, wrap");
		add(new JLabel("<html><div width=250><center>Select a profile:</center></div>"), "align center, wrap");
		add(profiles, "w 200, align center, wrap");
		add(ok, "split 2, align center");
		add(quit);

		pack();
		setLocationRelativeTo(owner);
	}

	@Override
	public void addProfileSelectedListener(ActionListener listener) {
		ok.addActionListener(listener);
		profiles.getEditor().addActionListener(listener);
	}

	@Override
	public void addCancelListener(final ActionListener listener) {
		quit.addActionListener(listener);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				listener.actionPerformed(null);
			}
		});
	}

	@Override
	public void setAvailableProfiles(List<String> profiles) {
		this.profiles.setModel(new DefaultComboBoxModel(new Vector<String>(profiles)));
	}

	@Override
	public String getSelectedProfile() {
		return (String) profiles.getSelectedItem();
	}

	@Override
	public void showValidationError(String message) {
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	@Override
	public void close() {
		dispose();
	}

	@Override
	public void display() {
		setVisible(true);
	}
}
