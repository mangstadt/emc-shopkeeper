package emcshop;

/**
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class NotLoggedInException extends Exception {
	public NotLoggedInException() {
		super("Not logged in.");
	}
}
