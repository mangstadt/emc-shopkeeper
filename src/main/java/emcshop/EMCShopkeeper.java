package emcshop;

/**
 * Alias for the {@link Main} class so that "EMCShopkeeper" instead of "Main"
 * appears in the Mac menu bar. The {@link Main} class wasn't renamed because
 * that would require updating the JNLP file.
 */
public class EMCShopkeeper {
	public static void main(String args[]) throws Throwable {
		Main.main(args);
	}
}
