package emcshop.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
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

import org.apache.commons.lang3.StringUtils;

import au.com.bytecode.opencsv.CSVWriter;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.EmcSession;
import emcshop.Main;
import emcshop.db.DbDao;
import emcshop.db.ItemGroup;
import emcshop.gui.images.ImageManager;
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
			about.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					AboutDialog.show(MainFrame.this);
				}
			});
			help.add(about);

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

		show = new JButton("Show Transactions", ImageManager.getSearch());
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
							final Date from = fromDatePicker.getDate();
							final Date to = toDatePicker.getDate();

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

							final int netTotal;
							{
								int t = 0;
								for (ItemGroup group : itemGroupsList) {
									t += group.getNetAmount();
								}
								netTotal = t;
							}

							JScrollPane scrollPane = new JScrollPane(table);
							table.setFillsViewportHeight(true);

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

							tablePanel.add(scrollPane, "span 2, grow, w 100%, h 100%, wrap");

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
				ImageIcon img = ImageManager.getItemImage(group.getItem());
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

	private abstract class ExportComboBox extends DisabledItemsComboBox implements ActionListener {
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
		StringBuilder sb = new StringBuilder();

		sb.append("[font=courier new]");

		//date range
		sb.append("[b]");
		if (from == null && to == null) {
			sb.append("entire history");
		} else if (from == null) {
			sb.append("up to ").append(df.format(to));
		} else if (to == null) {
			sb.append(df.format(from)).append(" to today");
		} else if (from.equals(to)) {
			sb.append(df.format(from));
		} else {
			sb.append(df.format(from)).append(" to ").append(df.format(to));
		}
		sb.append("[/b]\n");

		//item table
		sb.append("- - - -Item - - - | - - - -Sold- - - -| - - -Bought- - - -| - - - -Net- - - -\n");
		for (ItemGroup group : itemGroups) {
			String item = group.getItem();
			bbCodeColumn(item, 17, sb);
			sb.append(" | ");

			String sold;
			if (group.getSoldQuantity() == 0) {
				sold = StringUtils.repeat("- ", 8) + "-";
			} else {
				sold = nf.format(group.getSoldQuantity()) + " / +" + nf.format(group.getSoldAmount()) + "r";
			}
			bbCodeColumn(sold, 17, sb);
			sb.append(" | ");

			String bought;
			if (group.getBoughtQuantity() == 0) {
				bought = StringUtils.repeat("- ", 8) + "-";
			} else {
				bought = "+" + nf.format(group.getBoughtQuantity()) + " / " + nf.format(group.getBoughtAmount()) + "r";
			}
			bbCodeColumn(bought, 17, sb);
			sb.append(" | ");

			if (group.getNetQuantity() > 0) {
				sb.append("[color=green]+");
			} else {
				sb.append("[color=red]");
			}
			sb.append(nf.format(group.getNetQuantity()));
			sb.append("[/color] / ");
			if (group.getNetAmount() > 0) {
				sb.append("[color=green]+");
			} else {
				sb.append("[color=red]");
			}
			sb.append(nf.format(group.getNetAmount())).append("r");
			sb.append("[/color]\n");
		}

		//footer and total
		String footer = "EMC Shopkeeper v" + Main.VERSION;
		sb.append("[url=http://github.com/mangstadt/emc-shopkeeper]").append(footer).append("[/url] ");
		sb.append(StringUtils.repeat('_', 50 - footer.length()));
		sb.append(" [b]Total[/b] | [b]");
		if (netTotal > 0) {
			sb.append("[color=green]+");
		} else {
			sb.append("[color=red]");
		}
		sb.append(nf.format(netTotal)).append("r");
		sb.append("[/color][/b]");

		sb.append("[/font]");

		return sb.toString();
	}

	private static void bbCodeColumn(String text, int length, StringBuilder sb) {
		sb.append(text);
		if (length - text.length() == 1) {
			sb.append('.');
		} else {
			for (int i = text.length(); i < length; i++) {
				if (i == text.length()) {
					sb.append(' ');
				} else {
					sb.append('.');
				}
			}
		}
	}
}
