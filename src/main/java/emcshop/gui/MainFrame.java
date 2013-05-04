package emcshop.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.StringUtils;

import au.com.bytecode.opencsv.CSVWriter;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.EmcSession;
import emcshop.LogManager;
import emcshop.Main;
import emcshop.db.DbDao;
import emcshop.db.ItemGroup;
import emcshop.db.PlayerGroup;
import emcshop.gui.images.ImageManager;
import emcshop.util.BBCodeBuilder;
import emcshop.util.Settings;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class MainFrame extends JFrame implements WindowListener {
	private static final Logger logger = Logger.getLogger(MainFrame.class.getName());
	private JButton update;
	private JLabel lastUpdateDate;
	private DatePicker toDatePicker;
	private DatePicker fromDatePicker;
	private JButton showItems;
	private JButton showPlayers;
	private JPanel tablePanel;
	private DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private final DbDao dao;
	private final Settings settings;
	private final LogManager logManager;

	public MainFrame(Settings settings, DbDao dao, LogManager logManager) {
		this.dao = dao;
		this.settings = settings;
		this.logManager = logManager;

		setTitle("EMC Shopkeeper");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		createMenu();
		createWidgets();
		layoutWidgets();
		setSize(settings.getWindowWidth(), settings.getWindowHeight());
		Image appIcon = ImageManager.getAppIcon().getImage();
		setIconImage(appIcon);

		addWindowListener(this);
	}

	private void createMenu() {
		//http://docs.oracle.com/javase/tutorial/uiswing/components/menu.html

		JMenuBar menuBar = new JMenuBar();

		if (!MacSupport.isMac()) {
			JMenu file = new JMenu("File");

			JMenuItem exit = new JMenuItem("Exit");
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

			JMenuItem showLog = new JMenuItem("Show log...");
			showLog.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ShowLogDialog.show(MainFrame.this, logManager);
				}
			});
			tools.add(showLog);

			JMenu logLevel = new JMenu("Log level");
			{
				Map<String, Level> levels = new LinkedHashMap<String, Level>();
				levels.put("Detailed", Level.FINEST);
				levels.put("Normal", Level.INFO);
				levels.put("Off", Level.OFF);
				ButtonGroup group = new ButtonGroup();
				for (Map.Entry<String, Level> entry : levels.entrySet()) {
					String name = entry.getKey();
					final Level level = entry.getValue();
					JMenuItem levelItem = new JRadioButtonMenuItem(name);
					levelItem.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent arg0) {
							logger.finest("Changing log level to " + level.getName() + ".");
							logManager.setLevel(level);
							settings.setLogLevel(level);
							try {
								settings.save();
							} catch (IOException e) {
								logger.log(Level.SEVERE, "Problem saving settings file.", e);
							}
						}
					});
					if (logManager.getLevel().equals(level)) {
						levelItem.setSelected(true);
					}
					group.add(levelItem);
					logLevel.add(levelItem);
				}
			}
			tools.add(logLevel);

			tools.addSeparator();

			JMenuItem resetDb = new JMenuItem("Reset database...");
			resetDb.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					boolean reset = ResetDatabaseDialog.show(MainFrame.this);
					if (reset) {
						final LoadingDialog loading = new LoadingDialog(MainFrame.this, "Resetting database", "Resetting database...");
						Thread t = new Thread() {
							@Override
							public void run() {
								try {
									dao.wipe();
									settings.setLastUpdated(null);
									try {
										settings.save();
									} catch (IOException e) {
										logger.log(Level.SEVERE, "Problem saving settings file.", e);
									}
									MainFrame.this.lastUpdateDate.setText("-");
									MainFrame.this.tablePanel.removeAll();
									MainFrame.this.tablePanel.validate();
									loading.dispose();
								} catch (Throwable e) {
									loading.dispose();
									ErrorDialog.show(MainFrame.this, "Problem resetting database.", e);
								}
							}
						};
						t.start();
						loading.setVisible(true);
					}
				}
			});
			tools.add(resetDb);

			menuBar.add(tools);
		}

		{
			JMenu help = new JMenu("Help");

			JMenuItem changelog = new JMenuItem("Changelog");
			changelog.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ChangelogDialog.show(MainFrame.this);
				}
			});
			help.add(changelog);

			if (!MacSupport.isMac()) {
				JMenuItem about = new JMenuItem("About");
				about.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						AboutDialog.show(MainFrame.this);
					}
				});
				help.add(about);
			}

			menuBar.add(help);
		}

		setJMenuBar(menuBar);
	}

	private void createWidgets() {
		update = new JButton("Update Transactions", ImageManager.getUpdate());
		update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				//log the user in if he's not logged in
				EmcSession session = settings.getSession();
				if (session == null) {
					LoginDialog.Result result = LoginDialog.show(MainFrame.this, settings.isPersistSession());
					session = result.getSession();
					if (session == null) {
						return;
					}
					settings.setPersistSession(result.isRememberMe());
					settings.setSession(session);
					try {
						settings.save();
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Problem saving settings file.", e);
					}
				}

				try {
					UpdateDialog w = new UpdateDialog(MainFrame.this, dao, settings);
					w.setVisible(true);
				} catch (SQLException e) {
					ErrorDialog.show(MainFrame.this, "An error occurred connecting to the database.", e);
				}
			}
		});

		lastUpdateDate = new JLabel();
		Date date = settings.getLastUpdated();
		lastUpdateDate.setText((date == null) ? "-" : df.format(date));

		fromDatePicker = new DatePicker();
		fromDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		fromDatePicker.setShowNoneButton(true);
		fromDatePicker.setShowTodayButton(true);
		fromDatePicker.setStripTime(true);

		toDatePicker = new DatePicker();
		toDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		toDatePicker.setShowNoneButton(true);
		toDatePicker.setShowTodayButton(true);
		toDatePicker.setStripTime(true);

		showItems = new JButton("Show Transactions", ImageManager.getSearch());
		showItems.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showTransactions(fromDatePicker.getDate(), toDatePicker.getDate(), false);
			}
		});

		showPlayers = new JButton("Show Players", ImageManager.getSearch());
		showPlayers.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showPlayers(fromDatePicker.getDate(), toDatePicker.getDate());
			}
		});
	}

	private void showTransactions(final Date from, final Date to, final boolean afterUpdate) {
		tablePanel.removeAll();
		tablePanel.validate();

		final LoadingDialog loading = new LoadingDialog(MainFrame.this, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					final List<ItemGroup> itemGroupsList;
					{
						Date toBumped = null;
						if (to != null) {
							Calendar c = Calendar.getInstance();
							c.setTime(to);
							c.add(Calendar.DATE, 1);
							toBumped = c.getTime();
						}
						Map<String, ItemGroup> itemGroups = dao.getItemGroups(from, toBumped);
						itemGroupsList = new ArrayList<ItemGroup>(itemGroups.values());
						Collections.sort(itemGroupsList, new Comparator<ItemGroup>() {
							@Override
							public int compare(ItemGroup a, ItemGroup b) {
								return a.getItem().compareToIgnoreCase(b.getItem());
							}
						});
					}

					final int netTotal;
					{
						int t = 0;
						for (ItemGroup group : itemGroupsList) {
							t += group.getNetAmount();
						}
						netTotal = t;
					}

					String dateRangeStr;
					{
						DateFormat df = new SimpleDateFormat("yyyy-MM-dd" + (afterUpdate ? " HH:mm:ss" : ""));
						if (from == null && to == null) {
							dateRangeStr = "entire history";
						} else if (from == null) {
							dateRangeStr = "up to <b><code>" + df.format(to) + "</b></code>";
						} else if (to == null) {
							dateRangeStr = "<b><code>" + df.format(from) + "</b></code> to today";
						} else if (from.equals(to)) {
							dateRangeStr = "<b><code>" + df.format(from) + "</b></code>";
						} else {
							dateRangeStr = "<b><code>" + df.format(from) + "</b></code> to <b><code>" + df.format(to) + "</b></code>";
						}
					}
					tablePanel.add(new JLabel("<html><font size=5>" + dateRangeStr + "</font></html>"), "w 100%, growx");

					ExportComboBox export = new ExportComboBox() {
						@Override
						public String bbCode() {
							return generateBBCode(itemGroupsList, netTotal, from, to);
						}

						@Override
						public String csv() {
							return generateCsv(itemGroupsList, netTotal, from, to);
						}

						@Override
						public void handle(String text) {
							Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
							StringSelection stringSelection = new StringSelection(text);
							c.setContents(stringSelection, stringSelection);

							JOptionPane.showMessageDialog(MainFrame.this, "Copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE);
						}
					};
					tablePanel.add(export, "align right, wrap");

					ItemsTable table = new ItemsTable(itemGroupsList);
					table.setFillsViewportHeight(true);
					JScrollPane tableScrollPane = new JScrollPane(table);
					tablePanel.add(tableScrollPane, "span 2, grow, w 100%, h 100%, wrap");

					String netTotalLabel;
					{
						NumberFormat nf = NumberFormat.getInstance();
						StringBuilder sb = new StringBuilder();
						sb.append("<html><font size=5>Net Total: <code>");
						if (netTotal < 0) {
							sb.append("<font color=red>" + nf.format(netTotal) + "r</font>");
						} else {
							sb.append("<font color=green>+" + nf.format(netTotal) + "r</font>");
						}
						sb.append("</code></font></html>");
						netTotalLabel = sb.toString();
					}
					tablePanel.add(new JLabel(netTotalLabel), "span 2, align right");

					tablePanel.validate();
				} catch (SQLException e) {
					ErrorDialog.show(MainFrame.this, "An error occurred querying the database.", e);
				} finally {
					loading.dispose();
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	private void showPlayers(final Date from, final Date to) {
		tablePanel.removeAll();
		tablePanel.validate();

		final LoadingDialog loading = new LoadingDialog(MainFrame.this, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					final Collection<PlayerGroup> playerGroups;
					{
						Date toBumped = null;
						if (to != null) {
							Calendar c = Calendar.getInstance();
							c.setTime(to);
							c.add(Calendar.DATE, 1);
							toBumped = c.getTime();
						}
						playerGroups = dao.getPlayerGroups(from, toBumped).values();
					}

					String dateRangeStr;
					{
						DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
						if (from == null && to == null) {
							dateRangeStr = "entire history";
						} else if (from == null) {
							dateRangeStr = "up to <b><code>" + df.format(to) + "</b></code>";
						} else if (to == null) {
							dateRangeStr = "<b><code>" + df.format(from) + "</b></code> to today";
						} else if (from.equals(to)) {
							dateRangeStr = "<b><code>" + df.format(from) + "</b></code>";
						} else {
							dateRangeStr = "<b><code>" + df.format(from) + "</b></code> to <b><code>" + df.format(to) + "</b></code>";
						}
					}
					tablePanel.add(new JLabel("<html><font size=5>" + dateRangeStr + "</font></html>"), "w 100%, growx");

					ExportComboBox export = new ExportComboBox() {
						@Override
						public String bbCode() {
							//return generateBBCode(playerGroup, from, to);
							return ""; //TODO
						}

						@Override
						public String csv() {
							//return generateCsv(playerGroup, from, to);
							return ""; //TODO
						}

						@Override
						public void handle(String text) {
							Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
							StringSelection stringSelection = new StringSelection(text);
							c.setContents(stringSelection, stringSelection);

							JOptionPane.showMessageDialog(MainFrame.this, "Copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE);
						}
					};
					tablePanel.add(export, "align right, wrap");

					PlayersPanel panel = new PlayersPanel(playerGroups);
					JScrollPane panelScrollPane = new JScrollPane(panel);
					panelScrollPane.getVerticalScrollBar().setUnitIncrement(100);

					tablePanel.add(new JLabel("Sort by:"), "align right");
					SortComboBox sort = new SortComboBox(panel, panelScrollPane);
					tablePanel.add(sort, "wrap");

					tablePanel.add(panelScrollPane, "span 2, grow, w 100%, h 100%, wrap");

					tablePanel.validate();
				} catch (SQLException e) {
					loading.dispose();
					ErrorDialog.show(MainFrame.this, "An error occurred querying the database.", e);
				} finally {
					loading.dispose();
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	void updateSuccessful(Date started, long time, int transactionCount, int pageCount, boolean showResults) {
		long components[] = TimeUtils.parseTimeComponents(time);
		String message;
		if (transactionCount == 0) {
			message = "No new transactions found.";
		} else {
			if (showResults) {
				showTransactions(settings.getLastUpdated(), started, true);
			}

			NumberFormat nf = NumberFormat.getInstance();
			StringBuilder sb = new StringBuilder();
			sb.append("Update complete.\n");
			sb.append(nf.format(pageCount)).append(" pages parsed and ");
			sb.append(nf.format(transactionCount)).append(" transactions added in ");
			if (components[3] > 0) {
				sb.append(components[3]).append(" hours, ");
			}
			if (components[3] > 0 || components[2] > 0) {
				sb.append(components[2]).append(" minutes and ");
			}
			sb.append(components[1]).append(" seconds.");
			message = sb.toString();
		}
		JOptionPane.showMessageDialog(this, message, "Update complete", JOptionPane.INFORMATION_MESSAGE);

		settings.setLastUpdated(started);
		try {
			settings.save();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Problem writing to settings file.", e);
		}
		lastUpdateDate.setText(df.format(started));
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

		p.add(new JSeparator(), "w 200!, align center, wrap");

		p2 = new JPanel(new MigLayout());

		p2.add(new JLabel("Start:"), "align right");
		p2.add(fromDatePicker, "wrap");

		p2.add(new JLabel("End:"), "align right");
		p2.add(toDatePicker, "wrap");

		p.add(p2, "wrap");
		p.add(showItems, "align center, wrap");
		p.add(showPlayers, "align center");

		return p;
	}

	private JPanel createRightPanel() {
		tablePanel = new JPanel(new MigLayout("width 100%, height 100%"));
		return tablePanel;
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

	private abstract class ExportComboBox extends JComboBox implements ActionListener {
		private final String heading = "Copy to Clipboard";
		private final String bbCode = "BB Code";
		private final String csv = "CSV";

		public ExportComboBox() {
			addItem(heading);
			addItem(bbCode);
			addItem(csv);
			addActionListener(this);
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			String selected = (String) getSelectedItem();
			String text = null;
			if (selected == csv) {
				text = csv();
			} else if (selected == bbCode) {
				text = bbCode();
			}

			//re-select the first element
			setSelectedItem(heading);

			if (text != null) {
				handle(text);
			}
		}

		public abstract String bbCode();

		public abstract String csv();

		public abstract void handle(String text);
	}

	private class SortComboBox extends JComboBox implements ActionListener {
		private final String playerName = "Player name";
		private final String bestCustomers = "Best Customers";
		private final String bestSuppliers = "Best Suppliers";
		private final PlayersPanel panel;
		private final JScrollPane scrollPane;
		private String currentSelection;

		public SortComboBox(PlayersPanel panel, JScrollPane scrollPane) {
			addItem(playerName);
			addItem(bestCustomers);
			addItem(bestSuppliers);
			addActionListener(this);
			this.panel = panel;
			this.scrollPane = scrollPane;
			currentSelection = playerName;
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			String selected = (String) getSelectedItem();
			if (selected == currentSelection) {
				return;
			}

			if (selected == playerName) {
				panel.sortByPlayerName();
			} else if (selected == bestCustomers) {
				panel.sortByCustomers();
			} else if (selected == bestSuppliers) {
				panel.sortBySuppliers();
			}
			currentSelection = selected;

			scrollPane.getVerticalScrollBar().setValue(0); //scroll to top
			panel.repaint(); //without this, it makes the panel's background white
		}
	}

	protected static String generateCsv(List<ItemGroup> itemGroups, int netTotal, Date from, Date to) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		writer.writeNext(new String[] { (from == null) ? "" : df.format(from), (to == null) ? "" : df.format(to) });
		writer.writeNext(new String[] { "Item", "Sold Quantity", "Sold Amount", "Bought Quantity", "Bought Amount", "Net Quantity", "Net Amount" });
		for (ItemGroup group : itemGroups) {
			//@formatter:off
			writer.writeNext(new String[]{
				group.getItem(),
				group.getSoldQuantity() + "",
				group.getSoldAmount() + "",
				group.getBoughtQuantity() + "",
				group.getBoughtAmount() + "",
				group.getNetQuantity() + "",
				group.getNetAmount() + ""
			});
			//@formatter:on
		}
		writer.writeNext(new String[] { "EMC Shopkeeper v" + Main.VERSION + " - " + Main.URL, "", "", "", "", "", netTotal + "" });

		try {
			writer.close();
		} catch (IOException e) {
			//writing to string
		}
		return sw.toString();
	}

	protected static String generateBBCode(List<ItemGroup> itemGroups, int netTotal, Date from, Date to) {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		NumberFormat nf = NumberFormat.getInstance();
		BBCodeBuilder sb = new BBCodeBuilder();

		sb.font("courier new");

		//date range
		sb.b();
		if (from == null && to == null) {
			sb.text("entire history");
		} else if (from == null) {
			sb.text("up to ").text(df.format(to));
		} else if (to == null) {
			sb.text(df.format(from)).text(" to today");
		} else if (from.equals(to)) {
			sb.text(df.format(from));
		} else {
			sb.text(df.format(from)).text(" to ").text(df.format(to));
		}
		sb.close().nl();

		//item table
		sb.text("- - - -Item - - - | - - - -Sold- - - -| - - -Bought- - - -| - - - -Net- - - -").nl();
		for (ItemGroup group : itemGroups) {
			String item = group.getItem();
			bbCodeColumn(item, 17, sb);
			sb.text(" | ");

			String sold;
			if (group.getSoldQuantity() == 0) {
				sold = StringUtils.repeat("- ", 8) + "-";
			} else {
				sold = nf.format(group.getSoldQuantity()) + " / +" + nf.format(group.getSoldAmount()) + "r";
			}
			bbCodeColumn(sold, 17, sb);
			sb.text(" | ");

			String bought;
			if (group.getBoughtQuantity() == 0) {
				bought = StringUtils.repeat("- ", 8) + "-";
			} else {
				bought = "+" + nf.format(group.getBoughtQuantity()) + " / " + nf.format(group.getBoughtAmount()) + "r";
			}
			bbCodeColumn(bought, 17, sb);
			sb.text(" | ");

			if (group.getNetQuantity() > 0) {
				sb.color("green").text('+');
			} else {
				sb.color("red");
			}
			sb.text(nf.format(group.getNetQuantity()));
			sb.close().text(" / ");
			if (group.getNetAmount() > 0) {
				sb.color("green").text('+');
			} else {
				sb.color("red");
			}
			sb.text(nf.format(group.getNetAmount())).text('r');
			sb.close().nl();
		}

		//footer and total
		String footer = "EMC Shopkeeper v" + Main.VERSION;
		sb.url(Main.URL, footer);
		sb.text(StringUtils.repeat('_', 50 - footer.length()));
		sb.b(" Total").text(" | ");
		sb.b();
		if (netTotal > 0) {
			sb.color("green").text('+');
		} else {
			sb.color("red");
		}
		sb.text(nf.format(netTotal)).text('r');
		sb.close(2);

		sb.close();

		return sb.toString();
	}

	private static void bbCodeColumn(String text, int length, BBCodeBuilder sb) {
		sb.text(text);
		if (length - text.length() == 1) {
			sb.text('.');
		} else {
			for (int i = text.length(); i < length; i++) {
				if (i == text.length()) {
					sb.text(' ');
				} else {
					sb.text('.');
				}
			}
		}
	}
}