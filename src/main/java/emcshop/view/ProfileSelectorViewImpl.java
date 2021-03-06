package emcshop.view;

import java.awt.Window;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;

import emcshop.gui.DialogBuilder;
import emcshop.gui.images.Images;
import emcshop.util.GuiUtils;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class ProfileSelectorViewImpl extends JDialog implements IProfileSelectorView {
	private final JComboBox<String> profiles;
	private final JButton ok, quit;

	public ProfileSelectorViewImpl(Window owner) {
		super(owner);
		setTitle("Choose Profile");
		setResizable(false);
		setModal(true);

		setIconImage(Images.APP_ICON.getImage());

		profiles = new JComboBox<>();
		profiles.setEditable(true);

		ok = new JButton("OK");
		quit = new JButton("Quit");
		GuiUtils.onEscapeKeyPress(this, quit);

		///////////////////////

		setLayout(new MigLayout());

		add(new JLabel(Images.HEADER), "align center, wrap");
		add(new JLabel("<html><div width=250><center>Select an existing profile, or type the name of a new profile:</center></div>"), "align center, wrap");
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
	public void addCancelListener(ActionListener listener) {
		quit.addActionListener(listener);
		GuiUtils.addCloseDialogListener(this, listener);
	}

	@Override
	public void setAvailableProfiles(List<String> profiles) {
		this.profiles.setModel(new DefaultComboBoxModel<>(new Vector<>(profiles)));
	}

	@Override
	public String getSelectedProfile() {
		return (String) profiles.getEditor().getItem();
	}

	@Override
	public void showValidationError(String message) {
		DialogBuilder.error() //@formatter:off
			.parent(this)
			.title("Error")
			.text(message)
		.show(); //@formatter:on
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
