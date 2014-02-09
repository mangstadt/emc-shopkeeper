package emcshop;

/**
 * Alias for the {@link Main} class so that "EMCShopkeeper" instead of "Main"
 * appears on the Mac menu bar. The {@link Main} class wasn't renamed so people
 * don't have to download the JNLP file again.
 * @author mangst
 * 
 */
public class EMCShopkeeper {
	public static void main(String args[]) throws Throwable {
		Main.main(args);
	}
}
