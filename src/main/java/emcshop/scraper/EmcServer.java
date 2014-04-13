package emcshop.scraper;

/**
 * Represents the EMC servers.
 */
public enum EmcServer {
	SMP1("SMP1"), SMP2("SMP2"), SMP3("SMP3"), SMP4("SMP4"), SMP5("SMP5"), SMP6("SMP6"), SMP7("SMP7"), SMP8("SMP8"), SMP9("SMP9"), UTOPIA("Utopia");

	private final String display;

	private EmcServer(String display) {
		this.display = display;
	}

	@Override
	public String toString() {
		return display;
	}
}
