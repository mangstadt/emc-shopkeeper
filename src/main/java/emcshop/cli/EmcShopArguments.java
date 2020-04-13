package emcshop.cli;

import java.util.logging.Level;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import emcshop.EMCShopkeeper;

public class EmcShopArguments {
	private final OptionSet options;

	public EmcShopArguments(String[] args) {
		OptionParser parser = new OptionParser();
		parser.accepts("profile").withRequiredArg();
		parser.accepts("profile-dir").withRequiredArg();
		parser.accepts("db").withRequiredArg();
		parser.accepts("log-level").withRequiredArg();
		parser.accepts("update");
		parser.accepts("stop-page").withRequiredArg().ofType(Integer.class);
		parser.accepts("start-page").withRequiredArg().ofType(Integer.class);
		parser.accepts("query").withOptionalArg();
		parser.accepts("format").withRequiredArg();
		parser.accepts("version");
		parser.accepts("help");

		options = parser.parse(args);
	}

	public boolean version() {
		return options.has("version");
	}

	public boolean help() {
		return options.has("help");
	}

	public boolean update() {
		return options.has("update");
	}

	public String profileDir() {
		return (String) options.valueOf("profile-dir");
	}

	public String profile() {
		return (String) options.valueOf("profile");
	}

	public String query() {
		if (!options.has("query")) {
			return null;
		}

		if (!options.hasArgument("query")) {
			return "";
		}

		return (String) options.valueOf("query");
	}

	public String format() {
		return (String) options.valueOf("format");
	}

	public Level logLevel() {
		String value = (String) options.valueOf("log-level");
		return (value == null) ? null : Level.parse(value.toUpperCase());
	}

	public boolean isUpdate() {
		return options.has("update");
	}

	public String db() {
		return (String) options.valueOf("db");
	}

	public Integer stopPage() {
		return (Integer) options.valueOf("stop-page");
	}

	public Integer startPage() {
		return (Integer) options.valueOf("start-page");
	}

	public String printHelp(String defaultProfileName, String defaultProfileRoot, int defaultStartPage, String defaultFormat) {
		final String nl = System.getProperty("line.separator");

		//@formatter:off
		return
		"EMC Shopkeeper v" + EMCShopkeeper.VERSION + nl +
		"by Michael Angstadt (shavingfoam)" + nl +
		EMCShopkeeper.URL + nl +
		nl +
		"General arguments" + nl +
		"These arguments can be used for the GUI and CLI." + nl +
		"================================================" + nl +
		"--profile=PROFILE" + nl +
		"  The profile to use (defaults to \"" + defaultProfileName + "\")." + nl +
		nl +
		"--profile-dir=DIR" + nl +
		"  The path to the directory that contains all the profiles" + nl +
		"  (defaults to \"" + defaultProfileRoot + "\")." + nl +
		nl +
		"--db=PATH" + nl +
		"  Overrides the database location (stored in the profile by default)." + nl +
		nl +
		"--log-level=FINEST|FINER|FINE|CONFIG|INFO|WARNING|SEVERE" + nl +
		"  The log level to use (defaults to INFO)." + nl +
		nl +
		"CLI arguments" + nl +
		"Using one of these arguments will launch EMC Shopkeeper in CLI mode." + nl +
		"================================================" + nl +
		"--update" + nl +
		"  Updates the database with the latest transactions." + nl +
		"--start-page=PAGE" + nl +
		"  Specifies the transaction history page number to start at during" + nl +
		"  the first update (defaults to " + defaultStartPage + ")." + nl +
		"--stop-page=PAGE" + nl +
		"  Specifies the transaction history page number to stop at during" + nl +
		"  the first update (defaults to the last page)." + nl +
		nl +
		"--query=QUERY" + nl +
		"  Shows the net gains/losses of each item.  Examples:" + nl +
		"  All data:               --query" + nl +
		"  Today's data:           --query=\"today\"" + nl +
		"  Data since last update: --query=\"since last update\"" + nl +
		"  Three days of data:     --query=\"2013-03-07 to 2013-03-09\"" + nl +
		"  Data up to today:       --query=\"2013-03-07 to today\"" + nl +
		"--format=TABLE|CSV|BBCODE" + nl +
		"  Specifies how to render the queried transaction data (defaults to " + defaultFormat + ")." + nl +
		nl +
		"--version" + nl +
		"  Prints the version of this program." + nl +
		nl +
		"--help" + nl +
		"  Prints this help message.";
		//@formatter:on
	}
}
