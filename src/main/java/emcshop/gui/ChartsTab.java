package emcshop.gui;

import static emcshop.util.GuiUtils.busyCursor;
import static emcshop.util.NumberFormatter.formatRupeesWithColor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

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
	private final JButton display;

	private final JRadioButton showNetTotals, showItems;
	private final Map<String, ImageCheckBox> chartLines = new LinkedHashMap<String, ImageCheckBox>();
	private final JPanel chartLinesPanel;

	private final JLabel dateRangeQueried;
	private final JPanel graphPanel;
	private final JLabel netTotalLabelLabel;
	private final JLabel netTotalLabel;

	private Map<Date, Profits> profits;
	private GroupBy profitsGroupBy;

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

		display = new JButton("Display", ImageManager.getSearch());
		display.addActionListener(new ActionListener() {
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

		showNetTotals = new JRadioButton("<html><b>Show Net Totals</b></html>", true);
		showNetTotals.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = showNetTotals.isSelected();
				for (ImageCheckBox checkBox : chartLines.values()) {
					checkBox.setEnabled(!selected);
				}
				if (selected) {
					refreshChart();
				}
			}
		});

		showItems = new JRadioButton("<html>Show Items</html>");
		showItems.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = showItems.isSelected();
				for (ImageCheckBox checkBox : chartLines.values()) {
					checkBox.setEnabled(selected);
				}
				if (selected) {
					refreshChart();
				}
			}
		});

		ButtonGroup bg = new ButtonGroup();
		bg.add(showNetTotals);
		bg.add(showItems);

		List<String> groupNames = new ArrayList<String>(index.getItemGroupNames());
		Collections.sort(groupNames);
		for (final String group : groupNames) {
			ImageIcon icon = groupIcons.get(group);
			if (icon == null) {
				continue;
			}

			ImageCheckBox checkBox = new ImageCheckBox(group, icon);
			checkBox.setEnabled(false); //because "Show Net Total" radio button is selected
			checkBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					refreshChart();
				}
			});
			chartLines.put(group, checkBox);
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
		left.add(display);
		add(left);

		JPanel right = new JPanel(new MigLayout("insets 0"));

		right.add(showNetTotals);

		chartLinesPanel = new JPanel(new MigLayout("insets 0"));
		int column = 0;
		for (Map.Entry<String, ImageCheckBox> entry : chartLines.entrySet()) {
			final ImageCheckBox checkBox = entry.getValue();

			String constraints = (column % 2 == 1) ? "wrap" : "";
			chartLinesPanel.add(checkBox.getCheckbox(), "split 2");
			chartLinesPanel.add(checkBox.getLabel(), constraints);
			column++;
		}
		right.add(new MyJScrollPane(chartLinesPanel), "span 1 2, w 250, wrap");

		right.add(showItems);

		add(right, "w 100%, wrap");

		add(dateRangeQueried, "gaptop 20, w 100%, wrap"); //putting this label here allows the left panel to be vertically aligned to the top of the tab

		add(graphPanel, "span 2, grow, h 100%, wrap");

		add(netTotalLabelLabel, "span 2, split 2, align right");
		add(netTotalLabel);

		updateNetTotal(0);
	}

	public void clear() {
		try {
			fromDatePicker.setDate(new Date());
			toDatePicker.setDate(new Date());
		} catch (PropertyVetoException e) {
			throw new RuntimeException(e);
		}

		graphPanel.removeAll();

		updateNetTotal(0);

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

		//calculate net total
		int netTotal = 0;
		if (showNetTotals.isSelected()) {
			for (Profits p : profits.values()) {
				netTotal += p.getCustomer() + p.getSupplier();
			}
		} else {
			for (Profits p : profits.values()) {
				Set<String> groups = new HashSet<String>(p.getGroupCustomer().keySet());
				groups.addAll(p.getGroupSupplier().keySet());
				for (String group : groups) {
					ImageCheckBox checkBox = chartLines.get(group);
					if (checkBox == null || !checkBox.isSelected()) {
						continue;
					}

					Integer customer = p.getGroupCustomer().get(group);
					if (customer == null) {
						customer = 0;
					}
					Integer supplier = p.getGroupSupplier().get(group);
					if (supplier == null) {
						supplier = 0;
					}

					netTotal += customer + supplier;
				}
			}
		}
		updateNetTotal(netTotal);
	}

	private XYDataset createDataset(Map<Date, Profits> profits) {
		TimeSeriesCollection dataset = new TimeSeriesCollection();

		if (showNetTotals.isSelected()) {
			TimeSeries customersSeries = new TimeSeries("Customers");
			TimeSeries suppliersSeries = new TimeSeries("Suppliers");
			TimeSeries netProfitSeries = new TimeSeries("Net Profit");

			for (Map.Entry<Date, Profits> entry : profits.entrySet()) {
				Date date = entry.getKey();
				Profits p = entry.getValue();
				RegularTimePeriod timePeriod = (profitsGroupBy == GroupBy.DAY) ? new Day(date) : new Month(date);

				customersSeries.add(timePeriod, p.getCustomer());
				suppliersSeries.add(timePeriod, p.getSupplier());
				netProfitSeries.add(timePeriod, p.getCustomer() + p.getSupplier());
			}

			dataset.addSeries(customersSeries);
			dataset.addSeries(suppliersSeries);
			dataset.addSeries(netProfitSeries);
			return dataset;
		}

		if (showItems.isSelected()) {
			Map<String, TimeSeries> series = new HashMap<String, TimeSeries>();
			for (Map.Entry<Date, Profits> entry : profits.entrySet()) {
				Date date = entry.getKey();
				Profits p = entry.getValue();
				RegularTimePeriod timePeriod = (profitsGroupBy == GroupBy.DAY) ? new Day(date) : new Month(date);

				for (Map.Entry<String, ImageCheckBox> chartLine : chartLines.entrySet()) {
					ImageCheckBox checkbox = chartLine.getValue();
					if (checkbox == null || !checkbox.isSelected()) {
						continue;
					}

					String group = chartLine.getKey();
					TimeSeries ts = series.get(group);
					if (ts == null) {
						ts = new TimeSeries(group);
						series.put(group, ts);
					}

					Integer customer = p.getGroupCustomer().get(group);
					if (customer == null) {
						customer = 0;
					}
					Integer supplier = p.getGroupSupplier().get(group);
					if (supplier == null) {
						supplier = 0;
					}

					ts.add(timePeriod, customer + supplier);
				}
			}

			for (TimeSeries ts : series.values()) {
				dataset.addSeries(ts);
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
		if (showNetTotals.isSelected()) {
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

	private void updateNetTotal(int netTotal) {
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
