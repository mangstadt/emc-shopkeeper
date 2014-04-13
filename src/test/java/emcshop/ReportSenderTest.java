package emcshop;

public class ReportSenderTest {
	//Tests this class and the PHP script.
	public static void main(String args[]) throws Throwable {
		ReportSender rs = new ReportSender();

		try {
			Integer.parseInt("test");
		} catch (Throwable t) {
			rs.report(t);
		}

		System.in.read(); //stop the program from terminating
	}
}
