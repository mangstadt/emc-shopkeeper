package emcshop.view;

import java.awt.Component;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.exception.ExceptionUtils;

import emcshop.gui.MyJScrollPane;
import emcshop.gui.images.Images;
import emcshop.util.GuiUtils;
import emcshop.util.RelativeDateFormat;
import emcshop.util.UIDefaultsWrapper;

@SuppressWarnings("serial")
public class DatabaseStartupErrorViewImpl extends JDialog implements IDatabaseStartupErrorView {
    private final JTextArea displayText, stackTrace;
    private final JLabel errorIcon, restoreLoading;
    private final JButton quit, report, restore;
    private final JList backups;
    private final List<ActionListener> restoreListeners = new ArrayList<ActionListener>();

    public DatabaseStartupErrorViewImpl(Window owner) {
        super(owner, "Error");
        setModalityType(ModalityType.DOCUMENT_MODAL); //go on top of all windows
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        GuiUtils.addCloseDialogListener(this, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (quit.isEnabled()) {
                    quit.doClick();
                }
            }
        });
        GuiUtils.onEscapeKeyPress(this, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                if (quit.isEnabled()) {
                    quit.doClick();
                }
            }
        });

        restoreLoading = new JLabel("Working...", Images.LOADING_SMALL, SwingConstants.LEFT);
        restoreLoading.setVisible(false);

        restore = new JButton("Restore Selected Backup");
        restore.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                Date selected = getSelectedBackup();
                if (selected == null) {
                    return;
                }

                int result = JOptionPane.showConfirmDialog(DatabaseStartupErrorViewImpl.this, "Are you sure you want to restore this backup?", "Confirm Restore", JOptionPane.YES_NO_OPTION);
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }

                restoreLoading.setVisible(true);
                quit.setEnabled(false);
                restore.setEnabled(false);
                backups.setEnabled(false);
                report.setEnabled(false);

                GuiUtils.fireEvents(restoreListeners);
            }
        });

        displayText = new JTextArea("An error occurred while starting up the database.");
        displayText.setEditable(false);
        displayText.setBackground(getBackground());
        displayText.setLineWrap(true);
        displayText.setWrapStyleWord(true);

        //http://stackoverflow.com/questions/1196797/where-are-these-error-and-warning-icons-as-a-java-resource
        errorIcon = new JLabel(Images.getErrorIcon());

        stackTrace = new JTextArea();
        stackTrace.setEditable(false);
        stackTrace.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        quit = new JButton("Quit");
        GuiUtils.onEscapeKeyPress(this, quit);

        report = new JButton("Send Error Report");

        backups = new JList();
        backups.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backups.setCellRenderer(new ListCellRenderer() {
            private final RelativeDateFormat df = new RelativeDateFormat();

            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
                Date date = (Date) value;
                JLabel label = new JLabel(df.format(date));
                UIDefaultsWrapper.assignListFormats(label, selected);
                return label;
            }
        });

        /////////////////

        setLayout(new MigLayout());
        add(errorIcon, "split 2");
        add(displayText, "w 100:100%:100%, gapleft 10, wrap");
        JScrollPane scroll = new MyJScrollPane(stackTrace);
        add(scroll, "grow, w 100%, h 100%, align center, wrap");
        add(report, "align right, wrap");

        add(new JSeparator(), "w 100%, wrap");

        add(new JLabel("<html><b>Backups:</b></html>"), "wrap");
        JScrollPane pane = new JScrollPane(backups);
        add(pane, "h 100!, w 100%, wrap");
        add(restoreLoading, "align center, wrap");

        add(restore, "split 2, align center");
        add(quit);

        setSize(500, 500);
    }

    @Override
    public void addStartRestoreListener(final ActionListener listener) {
        restoreListeners.add(listener);
    }

    @Override
    public Date getSelectedBackup() {
        return (Date) backups.getSelectedValue();
    }

    @Override
    public void setBackups(final List<Date> backups) {
        this.backups.setModel(new AbstractListModel() {
            @Override
            public Object getElementAt(int index) {
                return backups.get(index);
            }

            @Override
            public int getSize() {
                return backups.size();
            }
        });
    }

    @Override
    public void setThrown(Throwable thrown) {
        stackTrace.setText(ExceptionUtils.getStackTrace(thrown));
        stackTrace.setCaretPosition(0); //scroll to top
    }

    @Override
    public void addSendErrorReportListener(ActionListener listener) {
        report.addActionListener(listener);
    }

    @Override
    public void addCloseListener(ActionListener listener) {
        quit.addActionListener(listener);
        GuiUtils.addCloseDialogListener(this, listener);
    }

    @Override
    public void errorReportSent() {
        report.setEnabled(false);
        report.setText("Reported");
        JOptionPane.showMessageDialog(DatabaseStartupErrorViewImpl.this, "Error report sent.  Thanks!");
    }

    @Override
    public void display() {
        setVisible(true);
    }

    @Override
    public void close() {
        dispose();
    }
}
