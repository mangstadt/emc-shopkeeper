package emcshop.cli;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.lang3.StringUtils;

public class EmcShopArguments extends Arguments {
	private static final Set<String> validArgs;
	static {
		Set<String> set = new HashSet<String>();
		set.add("profile");
		set.add("profile-dir");
		set.add("db");
		set.add("settings");
		set.add("log-level");

		set.add("update");
		set.add("stop-page");
		set.add("start-page");
		set.add("query");
		set.add("format");
		set.add("version");
		set.add("help");
		validArgs = Collections.unmodifiableSet(set);
	}

	public EmcShopArguments(String[] args) {
		super(args);

		//check for invalid arguments
		Collection<String> invalidArgs = invalidArgs(validArgs);
		if (!invalidArgs.isEmpty()) {
			String argList = StringUtils.join(invalidArgs, ", ");
			throw new IllegalArgumentException(argList);
		}
	}

	public boolean version() {
		return exists(null, "version");
	}

	public boolean help() {
		return exists(null, "help");
	}

	public boolean update() {
		return exists(null, "update");
	}

	public String profileDir() {
		return value(null, "profile-dir");
	}

	public String profile() {
		return value(null, "profile");
	}

	public String settings() {
		return value(null, "settings");
	}

	public String query() {
		String value = value(null, "query");
		if (value == null && exists(null, "query")) {
			return "";
		}
		return value;
	}

	public String format() {
		return value(null, "format");
	}

	public Level logLevel() {
		String value = value(null, "log-level");
		return (value == null) ? null : Level.parse(value.toUpperCase());
	}

	public boolean isUpdate() {
		return exists(null, "update");
	}

	public String db() {
		return value(null, "db");
	}

	public Integer stopPage() {
		return valueInt(null, "stop-page");
	}

	public Integer startPage() {
		return valueInt(null, "start-page");
	}
}
