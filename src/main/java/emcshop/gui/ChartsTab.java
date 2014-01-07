package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyVetoException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.util.DefaultShadowGenerator;
import org.jfree.data.time.Day;
import org.jfree.data.time.Month;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.RectangleInsets;

import com.michaelbaranov.microba.calendar.DatePicker;

import emcshop.ItemIndex;
import emcshop.db.DbDao;
import emcshop.db.Profits;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.ImageCheckBox;

@SuppressWarnings("serial")
public class ChartsTab extends JPanel {
	private final MainFrame owner;
	private final DbDao dao;
	private final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
	private final ItemIndex index = ItemIndex.instance();
	private final Map<String, ImageIcon> groupIcons = new HashMap<String, ImageIcon>();
	{
		groupIcons.put("Clay", ImageManager.getItemImage("Hard Clay"));
		groupIcons.put("Diamonds", ImageManager.getItemImage("Diamond"));
		groupIcons.put("Discs", ImageManager.getItemImage("Chirp Disc"));
		groupIcons.put("Dyes", ImageManager.getItemImage("Red Dye"));
		groupIcons.put("Fish", ImageManager.getItemImage("Raw Salmon"));
		groupIcons.put("Flowers", ImageManager.getItemImage("Blue Orchid"));
		groupIcons.put("Glass", ImageManager.getItemImage("Blue Glass"));
		groupIcons.put("Gold", ImageManager.getItemImage("Gold Ingot"));
		groupIcons.put("Iron", ImageManager.getItemImage("Iron Ingot"));
		groupIcons.put("Lapis", ImageManager.getItemImage("Blue Dye"));
		groupIcons.put("Leather", ImageManager.getItemImage("Leather"));
		groupIcons.put("Potions", ImageManager.getItemImage("Potion of Health"));
		groupIcons.put("Quartz", ImageManager.getItemImage("Quartz"));
		groupIcons.put("Spawn Eggs", ImageManager.getItemImage("Pig Egg"));
		groupIcons.put("Stone", ImageManager.getItemImage("Cobblestone"));
		groupIcons.put("Wool", ImageManager.getItemImage("White Wool"));
		groupIcons.put("Wood", ImageManager.getItemImage("Oak Plank"));
	}

	private final JCheckBox entireHistory;
	private final JLabel toDatePickerLabel;
	private final DatePicker toDatePicker;
	private final JLabel fromDatePickerLabel;
	private final DatePicker fromDatePicker;
	private final JComboBox groupBy;
	private final JButton loadData;

	private final JComboBox show;
	private final JPanel showPanel;
	private final Map<String, ImageCheckBox> itemGroupCheckboxes = new LinkedHashMap<String, ImageCheckBox>();
	private final JPanel itemGroupsPanel, itemNamesPanel;
	private final List<ItemSuggestField> itemNames;

	private final JLabel dateRangeQueried;
	private final JPanel graphPanel;
	private final JLabel netTotalLabelLabel;
	private final JLabel netTotalLabel;

	private Map<Date, Profits> profits;
	private GroupBy profitsGroupBy;
	private int netTotal = 0;

	private enum Show {
		NET_PROFITS("Net Profits"), ITEM_GROUPS("Item Groups"), ITEMS("Items");

		private final String display;

		private Show(String display) {
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	private enum GroupBy {
		DAY("Day"), MONTH("Month");

		private final String display;

		private GroupBy(String display) {
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	public ChartsTab(MainFrame owner, DbDao dao) {
		this.owner = owner;
		this.dao = dao;

		Date earliestTransactionDate = null;
		try {
			earliestTransactionDate = dao.getEarliestTransactionDate();
		} catch (SQLException e) {
			//ignore
		}

		String text = "entire history";
		if (earliestTransactionDate != null) {
			text += " (since " + df.format(earliestTransactionDate) + ")";
		}
		entireHistory = new JCheckBox(text);
		entireHistory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean enableDatePickers = !entireHistory.isSelected();
				fromDatePickerLabel.setEnabled(enableDatePickers);
				fromDatePicker.setEnabled(enableDatePickers);
				toDatePickerLabel.setEnabled(enableDatePickers);
				toDatePicker.setEnabled(enableDatePickers);
			}
		});

		fromDatePickerLabel = new JLabel("Start:");
		fromDatePicker = new DatePicker();
		fromDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		fromDatePicker.setShowNoneButton(true);
		fromDatePicker.setShowTodayButton(true);
		fromDatePicker.setStripTime(true);

		toDatePickerLabel = new JLabel("End:");
		toDatePicker = new DatePicker();
		toDatePicker.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
		toDatePicker.setShowNoneButton(true);
		toDatePicker.setShowTodayButton(true);
		toDatePicker.setStripTime(true);

		groupBy = new JComboBox(GroupBy.values());
		groupBy.setEditable(false);

		loadData = new JButton("Load Data", ImageManager.getSearch());
		loadData.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (!checkDateRange()) {
					return;
				}
				showProfits();
			}
		});

		graphPanel = new JPanel(new MigLayout("width 100%, height 100%, fillx, insets 0"));

		dateRangeQueried = new JLabel();

		netTotalLabelLabel = new JLabel("<html><font size=5>Net Profit:</font></html>");
		netTotalLabel = new JLabel();

		show = new JComboBox(Show.values());
		show.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Show selected = (Show) show.getSelectedItem();

				showPanel.removeAll();
				switch (selected) {
				case NET_PROFITS:
					break;
				case ITEM_GROUPS:
					showPanel.add(new MyJScrollPane(itemGroupsPanel), "w 100%");
					break;
				case ITEMS:
					showPanel.add(itemNamesPanel, "w 100%");
					break;
				}
				showPanel.validate();
				showPanel.repaint();

				refreshChart();
			}
		});

		showPanel = new JPanel(new MigLayout("insets 0"));

		ActionListener checkboxListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				refreshChart();
			}
		};
		List<String> groupNames = new ArrayList<String>(index.getItemGroupNames());
		Collections.sort(groupNames);
		for (String group : groupNames) {
			ImageIcon icon = groupIcons.get(group);
			if (icon == null) {
				continue;
			}

			ImageCheckBox checkBox = new ImageCheckBox(group, icon);
			checkBox.addActionListener(checkboxListener);
			itemGroupCheckboxes.put(group, checkBox);
		}

		itemGroupsPanel = new JPanel(new MigLayout("insets 0"));
		int column = 0;
		for (Map.Entry<String, ImageCheckBox> entry : itemGroupCheckboxes.entrySet()) {
			final ImageCheckBox checkBox = entry.getValue();

			String constraints = (column % 2 == 1) ? "wrap" : "";
			itemGroupsPanel.add(checkBox.getCheckbox(), "split 2");
			itemGroupsPanel.add(checkBox.getLabel(), constraints);
			column++;
		}

		itemNamesPanel = new JPanel(new MigLayout("insets 0"));
		itemNames = new ArrayList<ItemSuggestField>();
		for (int i = 0; i < 6; i++) {
			String wrap = (i % 2 == 1) ? ", wrap" : "";

			final ItemSuggestField f = new ItemSuggestField(owner);
			f.addFocusListener(new FocusListener() {
				private String textOnFocusGained;

				@Override
				public void focusGained(FocusEvent arg0) {
					textOnFocusGained = f.getText();
				}

				@Override
				public void focusLost(FocusEvent arg0) {
					String textOnFocusLost = f.getText();
					if (!textOnFocusLost.equals(textOnFocusGained)) {
						refreshChart();
					}
				}
			});
			itemNames.add(f);
			itemNamesPanel.add(f, "w 150" + wrap);
		}

		///////////////////////////////////////

		setLayout(new MigLayout("fillx, insets 5"));

		JPanel left = new JPanel(new MigLayout("insets 0"));
		left.add(entireHistory, "wrap");
		left.add(fromDatePickerLabel, "split 4");
		left.add(fromDatePicker);
		left.add(toDatePickerLabel);
		left.add(toDatePicker, "wrap");
		left.add(new JLabel("Group By:"), "split 2");
		left.add(groupBy, "wrap");
		left.add(loadData);
		add(left, "w 100%");

		add(showPanel, "w 700, growy");

		JPanel right = new JPanel(new MigLayout("insets 0"));
		right.add(new JLabel("Show:"), "split 2, align right");
		right.add(show);
		add(right, "align right, growy, wrap");

		add(dateRangeQueried, "gaptop 20, w 100%, wrap"); //putting this label here allows the left panel to be vertically aligned to the top of the tab

		add(graphPanel, "span 3, grow, h 100%, wrap");

		add(netTotalLabelLabel, "span 3, split 2, align right");
		add(netTotalLabel);

		updateNetTotal();
	}

	public void clear() {
		try {
			fromDatePicker.setDate(new Date());
			toDatePicker.setDate(new Date());
		} catch (PropertyVetoException e) {
			throw new RuntimeException(e);
		}

		graphPanel.removeAll();

		updateNetTotal();

		validate();
	}

	private void showProfits() {
		Date range[] = getQueryDateRange();
		showProfits(range[0], range[1]);
	}

	private void showProfits(final Date from, final Date to) {
		busyCursor(owner, true);

		final LoadingDialog loading = new LoadingDialog(owner, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					profitsGroupBy = (GroupBy) groupBy.getSelectedItem();
					profits = (profitsGroupBy == GroupBy.DAY) ? dao.getProfitsByDay(from, to) : dao.getProfitsByMonth(from, to);

					updateDateRangeLabel(from, to);
					refreshChart();
				} catch (SQLException e) {
					ErrorDialog.show(owner, "An error occurred querying the database.", e);
				} finally {
					loading.dispose();
					busyCursor(owner, false);
				}
			}
		};
		t.start();
		loading.setVisible(true);
	}

	private void refreshChart() {
		if (profits == null) {
			return;
		}

		netTotal = 0;

		//build the chart
		JFreeChart chart = createChart(createDataset(profits));

		graphPanel.removeAll();
		graphPanel.validate();

		//add the chart to the UI
		ChartPanel panel = new ChartPanel(chart);
		panel.setFillZoomRectangle(true);
		panel.setMouseWheelEnabled(true);
		graphPanel.add(panel, "grow, w 100%, h 100%, wrap");
		graphPanel.validate();

		updateNetTotal();
	}

	private Map<String, Integer> organizeIntoGroups(Map<String, Integer> itemAmounts) {
		Map<String, Integer> groupAmounts = new HashMap<String, Integer>();

		for (Map.Entry<String, Integer> entry : itemAmounts.entrySet()) {
			String item = entry.getKey();
			Integer amount = entry.getValue();

			List<String> groups = index.getGroups(item);
			for (String group : groups) {
				Integer groupAmount = groupAmounts.get(group);
				if (groupAmount == null) {
					groupAmount = 0;
				}
				groupAmounts.put(group, groupAmount + amount);
			}
		}

		return groupAmounts;
	}

	private List<String> getSelectedGroups() {
		List<String> groups = new ArrayList<String>();

		for (Map.Entry<String, ImageCheckBox> entry : itemGroupCheckboxes.entrySet()) {
			ImageCheckBox checkbox = entry.getValue();
			if (checkbox.isSelected()) {
				String group = entry.getKey();
				groups.add(group);
			}
		}

		return groups;
	}

	private Set<String> getSelectedItems() {
		Set<String> items = new HashSet<String>();

		for (ItemSuggestField field : itemNames) {
			String item = field.getText();
			if (!item.isEmpty()) {
				items.add(item);
			}
		}

		return items;
	}

	private XYDataset createDataset(Map<Date, Profits> profits) {
		TimeSeriesCollection dataset = new TimeSeriesCollection();

		if (show.getSelectedItem() == Show.NET_PROFITS) {
			TimeSeries customersSeries = new TimeSeries("Customers");
			TimeSeries suppliersSeries = new TimeSeries("Suppliers");
			TimeSeries netProfitSeries = new TimeSeries("Net Profit");

			for (Map.Entry<Date, Profits> entry : profits.entrySet()) {
				Date date = entry.getKey();
				Profits p = entry.getValue();
				RegularTimePeriod timePeriod = (profitsGroupBy == GroupBy.DAY) ? new Day(date) : new Month(date);

				customersSeries.add(timePeriod, p.getCustomerTotal());
				suppliersSeries.add(timePeriod, p.getSupplierTotal());
				netProfitSeries.add(timePeriod, p.getCustomerTotal() + p.getSupplierTotal());
				netTotal += p.getCustomerTotal() + p.getSupplierTotal();
			}

			dataset.addSeries(customersSeries);
			dataset.addSeries(suppliersSeries);
			dataset.addSeries(netProfitSeries);
			return dataset;
		}

		if (show.getSelectedItem() == Show.ITEM_GROUPS) {
			//create TimeSeries for each selected group
			Map<String, TimeSeries> series = new HashMap<String, TimeSeries>();
			for (String group : getSelectedGroups()) {
				TimeSeries ts = new TimeSeries(group);
				series.put(group, ts);
				dataset.addSeries(ts);
			}

			for (Map.Entry<Date, Profits> entry : profits.entrySet()) {
				Date date = entry.getKey();
				Profits p = entry.getValue();
				RegularTimePeriod timePeriod = (profitsGroupBy == GroupBy.DAY) ? new Day(date) : new Month(date);

				Map<String, Integer> customerGroupTotals = organizeIntoGroups(p.getCustomerTotals());
				Map<String, Integer> supplierGroupTotals = organizeIntoGroups(p.getSupplierTotals());
				for (Map.Entry<String, TimeSeries> tsEntry : series.entrySet()) {
					String group = tsEntry.getKey();
					TimeSeries ts = tsEntry.getValue();

					Integer customer = customerGroupTotals.get(group);
					if (customer == null) {
						customer = 0;
					}
					Integer supplier = supplierGroupTotals.get(group);
					if (supplier == null) {
						supplier = 0;
					}

					ts.add(timePeriod, customer + supplier);
					netTotal += customer + supplier;
				}
			}

			return dataset;
		}

		if (show.getSelectedItem() == Show.ITEMS) {
			//create TimeSeries for each item
			Map<String, TimeSeries> series = new HashMap<String, TimeSeries>();
			for (String item : getSelectedItems()) {
				TimeSeries ts = new TimeSeries(item);
				series.put(item.toLowerCase(), ts);
				dataset.addSeries(ts);
			}

			for (Map.Entry<Date, Profits> entry : profits.entrySet()) {
				Date date = entry.getKey();
				Profits p = entry.getValue();
				RegularTimePeriod timePeriod = (profitsGroupBy == GroupBy.DAY) ? new Day(date) : new Month(date);

				for (Map.Entry<String, TimeSeries> tsEntry : series.entrySet()) {
					String item = tsEntry.getKey();
					TimeSeries ts = tsEntry.getValue();

					Integer customer = p.getCustomerTotals().get(item);
					if (customer == null) {
						customer = 0;
					}
					Integer supplier = p.getSupplierTotals().get(item);
					if (supplier == null) {
						supplier = 0;
					}

					ts.add(timePeriod, customer + supplier);
					netTotal += customer + supplier;
				}
			}

			return dataset;
		}

		return dataset;
	}

	private JFreeChart createChart(XYDataset dataset) {
		JFreeChart chart = ChartFactory.createTimeSeriesChart("", "Date", "Rupees Earned", dataset, true, false, false);
		chart.setBackgroundPaint(null); //transparent

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint(Color.white);
		plot.setDomainGridlinePaint(new Color(192, 192, 192));
		plot.setRangeGridlinePaint(new Color(192, 192, 192));
		plot.setRangeZeroBaselinePaint(Color.black);
		plot.setRangeZeroBaselineStroke(new BasicStroke(2));
		plot.setRangeZeroBaselineVisible(true);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		plot.setShadowGenerator(new DefaultShadowGenerator());

		XYItemRenderer renderer = plot.getRenderer();
		if (show.getSelectedItem() == Show.NET_PROFITS) {
			renderer.setSeriesPaint(0, new Color(0, 128, 0));
			renderer.setSeriesPaint(1, Color.red);
			renderer.setSeriesPaint(2, Color.blue);
			renderer.setSeriesStroke(2, new BasicStroke(3));
		}

		if (renderer instanceof XYLineAndShapeRenderer) {
			XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) renderer;
			r.setDrawSeriesLineAsPath(true);
		}

		DateAxis xAxis = (DateAxis) plot.getDomainAxis();
		String formatStr = (profitsGroupBy == GroupBy.DAY) ? "MMM dd" : "MMM yyyy";
		xAxis.setDateFormatOverride(new SimpleDateFormat(formatStr));

		NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
		NumberFormat nf = new DecimalFormat("#,###'r'");
		nf.setGroupingUsed(true);
		yAxis.setNumberFormatOverride(nf);
		yAxis.setAutoRangeIncludesZero(true);

		return chart;
	}

	private void updateDateRangeLabel(Date from, Date to) {
		String dateRangeStr;
		final String startFont = "<b><i><font color=navy>";
		final String endFont = "</font></i></b>";
		if (from == null && to == null) {
			dateRangeStr = startFont + "entire history" + endFont;
		} else if (from == null) {
			dateRangeStr = "up to " + startFont + df.format(to) + endFont;
		} else if (to == null) {
			dateRangeStr = startFont + df.format(from) + endFont + " to " + startFont + "now" + endFont;
		} else if (from.equals(to)) {
			dateRangeStr = startFont + df.format(from) + endFont;
		} else {
			dateRangeStr = startFont + df.format(from) + endFont + " to " + startFont + df.format(to) + endFont;
		}

		dateRangeQueried.setText("<html>" + dateRangeStr + "</html>");
	}

	private void updateNetTotal() {
		StringBuilder sb = new StringBuilder();
		sb.append("<html><font size=5><code>");
		sb.append(formatRupeesWithColor(netTotal));
		sb.append("</code></font></html>");
		netTotalLabel.setText(sb.toString());
	}

	private boolean checkDateRange() {
		if (entireHistory.isSelected()) {
			return true;
		}

		Date from = fromDatePicker.getDate();
		Date to = toDatePicker.getDate();
		if (from == null || to == null) {
			return true;
		}

		if (from.compareTo(to) > 0) {
			JOptionPane.showMessageDialog(this, "Invalid date range: \"Start\" date must come before \"End\" date.", "Invalid date range", JOptionPane.INFORMATION_MESSAGE);
			return false;
		}

		return true;
	}

	/**
	 * Calculates the date range that the query should search over from the
	 * various input elements on the panel.
	 * @return the date range
	 */
	private Date[] getQueryDateRange() {
		Date from, to;
		if (entireHistory.isSelected()) {
			from = to = null;
		} else {
			from = fromDatePicker.getDate();

			to = toDatePicker.getDate();
			if (to != null) {
				Calendar c = Calendar.getInstance();
				c.setTime(to);
				c.add(Calendar.DATE, 1);
				to = c.getTime();
			}
		}

		return new Date[] { from, to };
	}
}
