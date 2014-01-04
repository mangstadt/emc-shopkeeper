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
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

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

import emcshop.db.DbDao;
import emcshop.db.Profits;
import emcshop.gui.images.ImageManager;

@SuppressWarnings("serial")
public class GraphsTab extends JPanel {
	private final MainFrame owner;
	private final DbDao dao;
	private final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

	private final JCheckBox entireHistory;
	private final JLabel toDatePickerLabel;
	private final DatePicker toDatePicker;
	private final JLabel fromDatePickerLabel;
	private final DatePicker fromDatePicker;
	private final JComboBox groupBy;
	private final JButton display;

	private final JLabel dateRangeQueried;
	private final JPanel graphPanel;
	private final JLabel netTotalLabelLabel;
	private final JLabel netTotalLabel;

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

	public GraphsTab(MainFrame owner, DbDao dao) {
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
				showTransactions();
			}
		});

		graphPanel = new JPanel(new MigLayout("width 100%, height 100%, fillx, insets 0"));

		dateRangeQueried = new JLabel();

		netTotalLabelLabel = new JLabel("<html><font size=5>Net Profit:</font></html>");
		netTotalLabel = new JLabel();

		///////////////////////////////////////

		setLayout(new MigLayout("fillx, insets 5"));
		add(entireHistory, "wrap");
		add(fromDatePickerLabel, "split 4");
		add(fromDatePicker);
		add(toDatePickerLabel);
		add(toDatePicker, "wrap");

		add(new JLabel("Group By:"), "split 2");
		add(groupBy, "wrap");

		add(display, "wrap");

		add(dateRangeQueried, "gaptop 20, w 100%, wrap"); //putting this label here allows the left panel to be vertically aligned to the top of the tab

		add(graphPanel, "span 2, grow, h 100%, wrap");

		add(netTotalLabelLabel, "split 2, align right");
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

	private void showTransactions() {
		Date range[] = getQueryDateRange();
		showTransactions(range[0], range[1]);
	}

	private void showTransactions(final Date from, final Date to) {
		busyCursor(owner, true);

		graphPanel.removeAll();
		graphPanel.validate();

		final LoadingDialog loading = new LoadingDialog(owner, "Loading", "Querying . . .");
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					//query database
					Map<Date, Profits> profits = (groupBy.getSelectedItem() == GroupBy.DAY) ? dao.getProfitsByDay(from, to) : dao.getProfitsByMonth(from, to);

					//build the chart
					JFreeChart chart = createChart(createDataset(profits));
					//StandardChartTheme t = (StandardChartTheme) StandardChartTheme.createJFreeTheme();
					//t.setShadowVisible(true);
					//t.apply(chart);

					//add the chart to the UI
					ChartPanel panel = new ChartPanel(chart);
					panel.setFillZoomRectangle(true);
					panel.setMouseWheelEnabled(true);
					graphPanel.add(panel, "grow, w 100%, h 100%, wrap");
					graphPanel.validate();

					updateDateRangeLabel(from, to);

					int netTotal = 0;
					for (Profits p : profits.values()) {
						netTotal += p.getCustomer() + p.getSupplier();
					}
					updateNetTotal(netTotal);
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

	private XYDataset createDataset(Map<Date, Profits> profits) {
		TimeSeries customersTimeSeries = new TimeSeries("Customers");
		TimeSeries suppliersTimeSeries = new TimeSeries("Suppliers");
		TimeSeries netProfitTimeSeries = new TimeSeries("Net Profit");

		for (Map.Entry<Date, Profits> entry : profits.entrySet()) {
			Date date = entry.getKey();
			Profits p = entry.getValue();

			RegularTimePeriod timePeriod = (groupBy.getSelectedItem() == GroupBy.DAY) ? new Day(date) : new Month(date);
			customersTimeSeries.add(timePeriod, p.getCustomer());
			suppliersTimeSeries.add(timePeriod, p.getSupplier());
			netProfitTimeSeries.add(timePeriod, p.getCustomer() + p.getSupplier());
		}

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(customersTimeSeries);
		dataset.addSeries(suppliersTimeSeries);
		dataset.addSeries(netProfitTimeSeries);

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
		renderer.setSeriesPaint(0, new Color(0, 128, 0));
		renderer.setSeriesPaint(1, Color.red);
		renderer.setSeriesPaint(2, Color.blue);
		renderer.setSeriesStroke(2, new BasicStroke(3));

		if (renderer instanceof XYLineAndShapeRenderer) {
			XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) renderer;
			r.setDrawSeriesLineAsPath(true);
		}

		DateAxis xAxis = (DateAxis) plot.getDomainAxis();
		String formatStr = (groupBy.getSelectedItem() == GroupBy.DAY) ? "MMM dd" : "MMM yyyy";
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
