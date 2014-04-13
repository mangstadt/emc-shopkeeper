package emcshop.model;

import java.util.logging.Level;
import java.util.logging.Logger;

import emcshop.ReportSender;

public class UnhandledErrorModelImpl implements IUnhandledErrorModel {
	private static final Logger logger = Logger.getLogger(UnhandledErrorModelImpl.class.getName());

	private final ReportSender reportSender;
	private final String message;
	private final Throwable thrown;

	public UnhandledErrorModelImpl(ReportSender reportSender, String message, Throwable thrown) {
		this.reportSender = reportSender;
		this.message = message;
		this.thrown = thrown;
	}

	@Override
	public String getMessage() {
		return message;
	}

	@Override
	public Throwable getThrown() {
		return thrown;
	}

	@Override
	public void logError() {
		logger.log(Level.SEVERE, message, thrown);
	}

	@Override
	public void sendErrorReport() {
		reportSender.report(thrown);
	}
}
