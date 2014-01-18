package emcshop.gui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import net.miginfocom.swing.MigLayout;
import emcshop.Main;
import emcshop.gui.images.ImageManager;

/**
 * Allows the user to choose a profile.
 */
@SuppressWarnings("serial")
public class ProfileDialog extends JDialog {
	private final JComboBox profiles;
	private final JButton ok, quit;
	private File selectedProfileDir = null;

	private ProfileDialog(Window owner, final File profileRootDir) {
		super(owner);
		setTitle("Choose Profile");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setResizable(false);
		setModal(true);

		//get list of existing profiles
		Vector<String> profileNames = new Vector<String>();
		File files[] = profileRootDir.listFiles();
		for (File file : files) {
			if (!file.isDirectory()) {
				continue;
			}

			profileNames.add(file.getName());
		}

		profiles = new JComboBox(profileNames);
		profiles.setEditable(true);
		if (profileNames.contains(Main.defaultProfileName)) {
			profiles.setSelectedItem(Main.defaultProfileName);
		}
		profiles.getEditor().addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ok.doClick();
			}
		});

		ok = new JButton("OK");
		ok.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String profile = (String) profiles.getEditor().getItem();
				if (profile.isEmpty()) {
					JOptionPane.showMessageDialog(ProfileDialog.this, "Profile name cannot be blank.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}

				File profileDir = new File(profileRootDir, profile);
				if (!profileDir.exists() && !profileDir.mkdir()) {
					//if it couldn't create the directory
					JOptionPane.showMessageDialog(ProfileDialog.this, "Profile directory could not be created.  Try changing the name.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}

				selectedProfileDir = profileDir;
				dispose();
			}
		});

		quit = new JButton("Quit");
		quit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
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

	/**
	 * Shows the dialog.
	 * @param owner the dialog owner or null if there is no owner
	 * @param profileRootDir the directory where the profiles are located
	 * @return the file system location of the selected profile or null if the
	 * user canceled the dialog
	 */
	public static File show(Window owner, final File profileRootDir) {
		ProfileDialog dialog = new ProfileDialog(owner, profileRootDir);
		dialog.setVisible(true);
		return dialog.selectedProfileDir;
	}
}
