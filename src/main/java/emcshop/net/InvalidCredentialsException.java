package emcshop.net;

import org.apache.commons.lang3.StringUtils;

/**
 * Thrown when the EMC website rejects your login credentials.
 *
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String username, String password) {
        super("Invalid credentials: (username = " + username + "; password = " + StringUtils.repeat('*', password.length()));
    }
}