package emcshop.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.EmcSession;
import emcshop.db.DbDao;
import emcshop.db.ItemGroup;
import emcshop.util.Settings;
import emcshop.util.TimeUtils;

@SuppressWarnings("serial")
public class MainFrame extends JFrame implements WindowListener {
	private static final Logger logger = Logger.getLogger(MainFrame.class.getName());
	private JButton update;
	private JLabel lastUpdateDate;
	private DatePicker toDatePicker;
	private DatePicker fromDatePicker;
	private JComboBox groupBy;
	private JButton show;
	private JPanel tablePanel;
	private DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
		ImageIcon img = new ImageIcon(getClass().getResource("update.png"));
		update = new JButton("Update Transactions", img);
		update.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				//log the user in if he's not logged in
				EmcSession session = settings.getSession();
				if (session == null) {
					session = LoginDialog.show(MainFrame.this);
					if (session == null) {
						return;
					}
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

		groupBy = new JComboBox();
		groupBy.addItem("Item");
		groupBy.addItem("Player");

		img = new ImageIcon(getClass().getResource("search.png"));
		show = new JButton("Show Transactions", img);
		show.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				tablePanel.removeAll();
				tablePanel.validate();

				final LoadingDialog loading = new LoadingDialog(MainFrame.this, "Loading", "Querying . . .");
				Thread t = new Thread() {
					@Override
					public void run() {
						try {
							final List<ItemGroup> itemGroupsList;
							{
								Date from = fromDatePicker.getDate();
								Date to = toDatePicker.getDate();
								if (to != null) {
									Calendar c = Calendar.getInstance();
									c.setTime(to);
									c.add(Calendar.DATE, 1);
									to = c.getTime();
								}
								Map<String, ItemGroup> itemGroups = dao.getItemGroups(from, to);
								itemGroupsList = new ArrayList<ItemGroup>(itemGroups.values());
								Collections.sort(itemGroupsList, new Comparator<ItemGroup>() {
									@Override
									public int compare(ItemGroup a, ItemGroup b) {
										return a.getItem().compareToIgnoreCase(b.getItem());
									}
								});
							}

							JTable table = new JTable();
							table.setColumnSelectionAllowed(false);
							table.setRowSelectionAllowed(false);
							table.setCellSelectionEnabled(false);
							table.setRowHeight(24);
							table.setModel(new AbstractTableModel() {
								String[] columns = new String[] { "Item Name", "Sold", "Bought", "Net" };

								@Override
								public int getColumnCount() {
									return columns.length;
								}

								@Override
								public String getColumnName(int col) {
									return columns[col];
								}

								@Override
								public int getRowCount() {
									return itemGroupsList.size();
								}

								@Override
								public Object getValueAt(int row, int col) {
									return itemGroupsList.get(row);
								}

								public Class<?> getColumnClass(int c) {
									return ItemGroup.class;
								}

								@Override
								public boolean isCellEditable(int row, int col) {
									return false;
								}
							});
							table.setDefaultRenderer(ItemGroup.class, new ItemGroupRenderer());

							JScrollPane scrollPane = new JScrollPane(table);
							table.setFillsViewportHeight(true);

							String dateRangeStr;
							{
								DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
								Date from = fromDatePicker.getDate();
								Date to = toDatePicker.getDate();
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
							tablePanel.add(new JLabel("<html><font size=5>" + dateRangeStr + "</font></html>"), "wrap");

							tablePanel.add(scrollPane, "grow, w 100%, h 100%, wrap");

							String netTotalLabel;
							{
								int netTotal = 0;
								for (ItemGroup group : itemGroupsList) {
									netTotal += group.getNetAmount();
								}

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
							tablePanel.add(new JLabel(netTotalLabel), "align right");

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
		});
	}

	private static class ItemGroupRenderer implements TableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
			NumberFormat nf = NumberFormat.getNumberInstance();
			JLabel label = null;

			ItemGroup group = (ItemGroup) value;
			switch (col) {
			case 0:
				//TODO add the rest of the icons
				ImageIcon img = getItemImage(group.getItem());
				label = new JLabel(group.getItem(), img, SwingConstants.LEFT);
				break;
			case 1:
				if (group.getSoldQuantity() == 0) {
					label = new JLabel("-");
				} else {
					label = new JLabel(nf.format(group.getSoldQuantity()) + " / " + nf.format(group.getSoldAmount()) + "r");
				}
				break;
			case 2:
				if (group.getBoughtQuantity() == 0) {
					label = new JLabel("-");
				} else {
					label = new JLabel(nf.format(group.getBoughtQuantity()) + " / " + nf.format(group.getBoughtAmount()) + "r");
				}
				break;
			case 3:
				StringBuilder sb = new StringBuilder();
				sb.append("<html>");

				if (group.getNetQuantity() < 0) {
					sb.append("<font color=red>" + nf.format(group.getNetQuantity()) + "</font>");
				} else {
					sb.append("<font color=green>+" + nf.format(group.getNetQuantity()) + "</font>");
				}

				sb.append(" / ");

				if (group.getNetAmount() < 0) {
					sb.append("<font color=red>" + nf.format(group.getNetAmount()) + "r</font>");
				} else {
					sb.append("<font color=green>+" + nf.format(group.getNetAmount()) + "r</font>");
				}

				sb.append("</html>");

				label = new JLabel(sb.toString());
			}

			Color color = (row % 2 == 0) ? new Color(255, 255, 255) : new Color(240, 240, 240);
			label.setOpaque(true);
			label.setBackground(color);

			return label;
		}

		private ImageIcon getItemImage(String item) {
			item = item.toLowerCase().replace(" ", "_");
			URL url = getClass().getResource("items/" + item + ".png");
			if (url == null) {
				url = getClass().getResource("items/_empty.png");
			}
			ImageIcon img = new ImageIcon(url);
			return new ImageIcon(img.getImage().getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH));
		}
	}

	void updateSuccessful(Date started, long time, int transactionCount) {
		long components[] = TimeUtils.parseTimeComponents(time);
		String message;
		if (transactionCount == 0) {
			message = "No new transactions found.";
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append("Update complete.\n");
			sb.append(transactionCount).append(" transactions added in ");
			if (components[2] > 0) {
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

		p2.add(new JLabel("Group By:"), "align right");
		p2.add(groupBy, "wrap");

		p.add(p2, "wrap");
		p.add(show, "align center");

		return p;
	}

	private JPanel createRightPanel() {
		tablePanel = new JPanel(new MigLayout("width 100%, height 100%"));

		JLabel label = new JLabel("<html><i>no results</i></html>");
		tablePanel.add(label, "align center");

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
}
