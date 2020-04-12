package emcshop;

public class ReportSenderTest {
	//Tests this class and the PHP script.
	public static void main(String args[]) throws Exception {
		ReportSender rs = new ReportSender();

		try {
			Integer.parseInt("test");
		} catch (Exception e) {
			rs.report(null, e);
		}

		System.in.read(); //stop the program from terminating
	}
}
