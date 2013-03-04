package emcshop.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;
import emcshop.db.DbDao;
import emcshop.util.Settings;

@SuppressWarnings("serial")
public class MainFrame extends JFrame implements WindowListener {
	private static final Logger logger = Logger.getLogger(MainFrame.class.getName());
	private JButton update;
	private JLabel lastUpdateDate;
	private JTextField startDate;
	private JTextField endDate;
	private JComboBox groupBy;
	private JButton show;

	private final DbDao dao;
	private Settings settings;

	public MainFrame(Settings settings, DbDao dao) {
		this.dao = dao;
		this.settings = settings;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		createMenu();
		createWidgets();
		layoutWidgets();
		setSize(settings.getWindowWidth(), settings.getWindowHeight());

		addWindowListener(this);
	}

	private void createMenu() {
		//http://docs.oracle.com/javase/tutorial/uiswing/components/menu.html

		JMenuBar menuBar = new JMenuBar();

		{
			JMenu file = new JMenu("File");
			file.setMnemonic(KeyEvent.VK_F);

			JMenuItem exit = new JMenuItem("Exit");
			exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));
			exit.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					windowClosed(null);
				}
			});
			file.add(exit);

			menuBar.add(file);
		}

		{
			JMenu tools = new JMenu("Tools");
			tools.setMnemonic(KeyEvent.VK_T);

			JMenuItem export = new JMenuItem("Export to CSV");
			export.setEnabled(false);
			tools.add(export);

			tools.addSeparator();

			JMenuItem settings = new JMenuItem("Settings");
			tools.add(settings);

			menuBar.add(tools);
		}

		{
			JMenu help = new JMenu("Help");
			help.setMnemonic(KeyEvent.VK_H);

			JMenuItem about = new JMenuItem("About");
			help.add(about);

			menuBar.add(help);
		}

		setJMenuBar(menuBar);
	}

	private void createWidgets() {
		update = new JButton("Update Transactions");
		update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				UpdateDialog w = new UpdateDialog(MainFrame.this);
				w.setVisible(true);
			}
		});

		lastUpdateDate = new JLabel();
		Date date = settings.getLastUpdated();
		lastUpdateDate.setText((date == null) ? "-" : date.toString());

		startDate = new JTextField();
		endDate = new JTextField();
		groupBy = new JComboBox();
		groupBy.addItem("Item");
		groupBy.addItem("Player");
		show = new JButton("Show Transactions");
	}

	private void layoutWidgets() {
		setLayout(new BorderLayout());
		add(createLeftPanel(), BorderLayout.WEST);
		add(createRightPanel(), BorderLayout.CENTER);
	}

	private JPanel createLeftPanel() {
		JPanel p = new JPanel(new MigLayout());

		p.add(update, "align center, wrap");

		JPanel p2 = new JPanel(new FlowLayout());
		p2.add(new JLabel("Last updated:"));
		p2.add(lastUpdateDate);
		p.add(p2, "wrap");

		p.add(new JSeparator(), "w 100%!, wrap");

		p2 = new JPanel(new MigLayout());

		JLabel l = new JLabel("Start:");
		p2.add(l, "align right");
		startDate.setSize(100, 10);
		p2.add(startDate, "w 100!, wrap");

		p2.add(new JLabel("End:"), "align right");
		p2.add(endDate, "w 100!, wrap");

		p2.add(new JLabel("Group By:"), "align right");
		p2.add(groupBy, "wrap");

		p.add(p2, "wrap");
		p.add(show, "align center");

		return p;
	}

	private JPanel createRightPanel() {
		JPanel p = new JPanel();
		p.setLayout(new MigLayout());

		JLabel label = new JLabel("<html><h1>Feb 25 2013 to today</h1></html>");
		p.add(label);

		return p;
	}

	///////////////////////////////////

	@Override
	public void windowActivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		settings.setWindowWidth(getWidth());
		settings.setWindowHeight(getHeight());
		try {
			settings.save();
		} catch (IOException e) {
			logger.log(Level.WARNING, "Problem persisting settings file.", e);
		}

		System.exit(0);
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		//do nothing
	}
}
