package emcshop.view;

import static emcshop.util.GuiUtils.shake;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.HelpLabel;
import emcshop.gui.images.Images;
import emcshop.util.GuiUtils;
import emcshop.util.Listeners;

@SuppressWarnings("serial")
public class LoginViewImpl extends JDialog implements ILoginView {
    private final Listeners loginListeners = new Listeners();
    private final JButton login, cancel;
    private final JTextField username;
    private final JPasswordField password;
    private final JCheckBox savePassword;
    private final JLabel loading;
    private final JPanel messagePanel;
    private String origUsername;

    public LoginViewImpl(final Window owner) {
        super(owner, "Login");
        setModal(true);
        setResizable(false);

        login = new JButton("Login");
        login.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (origUsername != null && !origUsername.equalsIgnoreCase(username.getText())) {
                    boolean cancel = !showUsernameChangedDialog();
                    if (cancel) {
                        return;
                    }
                }

                messagePanel.removeAll();
                messagePanel.add(loading);

                login.setEnabled(false);
                username.setEnabled(false);
                password.setEnabled(false);
                savePassword.setEnabled(false);

                pack();
                validate();

                Thread t = new Thread() {
                    @Override
                    public void run() {
                        loginListeners.fire();
                    }
                };
                t.setDaemon(true);
                t.start();
            }
        });
        GuiUtils.onKeyPress(this, KeyEvent.VK_ENTER, login);

        cancel = new JButton("Cancel");
        GuiUtils.onEscapeKeyPress(this, cancel);

        messagePanel = new JPanel();
        username = new JTextField();
        password = new JPasswordField();
        savePassword = new JCheckBox("Save password");
        loading = new JLabel("Logging in...", Images.LOADING_SMALL, SwingConstants.CENTER);

        ///////////////////////

        setLayout(new MigLayout());

        add(new JLabel(Images.EMC_LOGO), "align center, wrap");

        add(new HelpLabel("<html><b>Please enter your login credentials.", "Your login credentials to the EMC website are required in order to download your rupee transactions.  EMC Shopkeeper will not steal your password, I promise!"), "align center, wrap");

        add(messagePanel, "align center, wrap");

        JPanel p = new JPanel(new MigLayout("insets 0"));
        p.add(new JLabel("Username:"), "align right");
        p.add(username, "w 150, wrap");
        p.add(new JLabel("Password:"), "align right");
        p.add(password, "w 150");
        add(p, "align center, wrap");

        add(savePassword, "split 2, align center");
        add(new HelpLabel("", "Selecting this option will save your password to the hard drive so that EMC Shopkeeper can auto-fill your password on this screen."), "wrap");

        add(login, "split 2, align center");
        add(cancel);

        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void addOnLoginListener(ActionListener listener) {
        loginListeners.add(listener);
    }

    @Override
    public void addOnCancelListener(ActionListener listener) {
        cancel.addActionListener(listener);
        GuiUtils.addCloseDialogListener(this, listener);
    }

    @Override
    public void setUsername(String username) {
        origUsername = username;

        this.username.setText(username);
        if (username != null) {
            password.requestFocus();
        }
    }

    @Override
    public String getUsername() {
        return username.getText();
    }

    @Override
    public void setPassword(String password) {
        savePassword.setSelected(password != null);
        this.password.setText(password);
    }

    @Override
    public String getPassword() {
        return new String(password.getPassword());
    }

    @Override
    public boolean getSavePassword() {
        return savePassword.isSelected();
    }

    @Override
    public void setSavePassword(boolean savePassword) {
        this.savePassword.setSelected(savePassword);
    }

    @Override
    public void networkError() {
        showError("Network error contacting EMC.");
    }

    @Override
    public void badLogin() {
        showError("Invalid login credentials.");
        password.requestFocus();
        password.selectAll();
    }

    private void showError(String text) {
        messagePanel.removeAll();

        JLabel error = new JLabel("<html><font color=red>" + text + "</font></html>");
        messagePanel.add(error);

        login.setEnabled(true);
        username.setEnabled(true);
        password.setEnabled(true);
        savePassword.setEnabled(true);

        validate();
        shake(this);
    }

    @Override
    public void close() {
        dispose();
    }

    @Override
    public void display() {
        setVisible(true);
    }

    /**
     * @return true to continue, false to cancel
     */
    private boolean showUsernameChangedDialog() {
        //@formatter:off
        int choice = JOptionPane.showOptionDialog(
                LoginViewImpl.this,

                "You have changed the username to something different!\n\n" +
                        "If you want to download the transactions of an alt account, you have to create a separate profile!\n\n" +
                        "1. Go to [Menu Button > Settings] and click the \"Show Profiles on Startup\" menu item.\n" +
                        "2. Restart EMC Shopkeeper.\n" +
                        "3. At the \"Choose Profile\" dialog, type a name for your new profile in the textbox and click OK.",

                "Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new Object[]{"Continue (not recommended)", "Cancel"},
                "Cancel"
        );
        //@formatter:on

        return choice == JOptionPane.YES_OPTION;
    }
}
