package emcshop.util;

import java.util.Locale;

import org.junit.rules.ExternalResource;

/**
 * Changes the JVM's default locale for the duration of a test.
 * @author Michael Angstadt
 */
public class DefaultLocaleRule extends ExternalResource {
	private final Locale locale;
	private Locale defaultLocale;

	/**
	 * @param locale the locale to use as the default
	 */
	public DefaultLocaleRule(Locale locale) {
		this.locale = locale;
	}

	@Override
	protected void before() {
		defaultLocale = Locale.getDefault();
		Locale.setDefault(locale);
	}

	@Override
	protected void after() {
		Locale.setDefault(defaultLocale);
	}
}
