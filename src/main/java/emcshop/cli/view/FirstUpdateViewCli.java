package emcshop.cli.view;

import java.awt.event.ActionListener;
import java.io.Console;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.time.DurationFormatUtils;

import emcshop.util.GuiUtils;
import emcshop.view.IFirstUpdateView;

public class FirstUpdateViewCli implements IFirstUpdateView {
	private final PrintStream out = System.out;
	private final Console console = System.console();
	private final String NEWLINE = System.getProperty("line.separator");

	private Integer stopAtPage, maxPaymentTransactionAge;
	private Long estimatedTime;

	private final List<ActionListener> onCancelListeners = new ArrayList<>();
	private final List<ActionListener> onBeginListeners = new ArrayList<>();
	private final List<ActionListener> onStopAtPageListeners = new ArrayList<>();

	@Override
	public void addOnCancelListener(ActionListener listener) {
		onCancelListeners.add(listener);
	}

	@Override
	public void addOnBeginListener(ActionListener listener) {
		onBeginListeners.add(listener);
	}

	@Override
	public void addStopAtPageChangedListener(ActionListener listener) {
		onStopAtPageListeners.add(listener);
	}

	@Override
	public Integer getStopAtPage() {
		return stopAtPage;
	}

	@Override
	public void setStopAtPage(Integer stopAtPage) {
		this.stopAtPage = stopAtPage;
	}

	@Override
	public Integer getMaxPaymentTransactionAge() {
		return maxPaymentTransactionAge;
	}

	@Override
	public void setMaxPaymentTransactionAge(Integer age) {
		maxPaymentTransactionAge = age;
	}

	@Override
	public void setEstimatedTime(Long estimatedTime) {
		this.estimatedTime = estimatedTime;
	}

	@Override
	public void display() {
		//@formatter:off
		out.println(
		"================================================================================" + NEWLINE +
		"NOTE: This is the first time you are running an update.  To ensure accurate" + NEWLINE +
		"results, it is recommended that you set MOVE PERMS to FALSE on your res for this" + NEWLINE +
		"first update." + NEWLINE +
		"                                /res set move false" + NEWLINE);
		//@formatter:on

		if (stopAtPage == null) {
			//@formatter:off
			out.println(
			"Your entire transaction history will be parsed." + NEWLINE +
			"This could take up to 60 MINUTES depending on its size.");
			//@formatter:on
		} else {
			out.println(stopAtPage + " pages will be parsed.");
			if (estimatedTime != null) {
				out.println("Estimated time: " + DurationFormatUtils.formatDuration(estimatedTime, "HH:mm:ss", true));
			}
		}

		out.println("--------------------------------------------------------------------------------");
		String ready = console.readLine("Are you ready to start? (y/n) ");
		if ("y".equalsIgnoreCase(ready)) {
			GuiUtils.fireEvents(onBeginListeners);
		} else {
			GuiUtils.fireEvents(onCancelListeners);
		}
	}

	@Override
	public void close() {
		//empty
	}
}
