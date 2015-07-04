package emcshop;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import emcshop.gui.lib.JarSignersHardLinker;
import emcshop.util.XmlBuilder;

/**
 * Uploads error reports to my website.
 */
public class ReportSender {
	private static final Logger logger = Logger.getLogger(ReportSender.class.getName());

	private final String url;
	private Integer dbVersion;
	private final BlockingQueue<Job> queue = new LinkedBlockingQueue<Job>();

	public ReportSender() {
		this("http://mikeangstadt.name/emc-shopkeeper/error-report.php");
	}

	/**
	 * Creates a new report sender.
	 * @param url the URL to send the error reports to
	 */
	ReportSender(String url) {
		this.url = url;

		SenderThread t = new SenderThread();
		t.setName(t.getClass().getSimpleName());
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Sets the version of the database.
	 * @param version the database version
	 */
	public void setDatabaseVersion(Integer version) {
		dbVersion = version;
	}

	/**
	 * Reports an error.
	 * @param message the message to report (can be null)
	 * @param throwable the error to report (can be null)
	 */
	public void report(String message, Throwable throwable) {
		Job job = new Job(message, throwable);
		queue.add(job);
	}

	private class SenderThread extends Thread {
		private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'");
		{
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
		}

		private final HttpClient client = HttpClientBuilder.create().build();

		@Override
		public void run() {
			while (true) {
				//get the next error
				Job job;
				try {
					job = queue.take();
				} catch (InterruptedException e) {
					break;
				}

				try {
					String body = createRequestBody(job);
					HttpPost request = new HttpPost(url);
					request.setEntity(new StringEntity(body, ContentType.TEXT_XML));

					HttpResponse response = client.execute(request);

					int status = response.getStatusLine().getStatusCode();
					HttpEntity entity = response.getEntity();
					String responseBody = (entity == null) ? "" : EntityUtils.toString(entity);
					if (status != 200 || !responseBody.isEmpty()) {
						logger.log(Level.WARNING, "Error report not accepted (HTTP " + status + "): " + responseBody);
					}
				} catch (Throwable t) {
					logger.log(Level.WARNING, "Problem sending error report.", t);
				}
			}
		}

		private String createRequestBody(Job job) {
			XmlBuilder xml = new XmlBuilder("Error");

			String timestamp = df.format(job.received);
			xml.append("Timestamp", timestamp);

			xml.append("Version", EMCShopkeeper.VERSION);

			String dbVersionStr = (dbVersion == null) ? "null" : dbVersion.toString();
			xml.append("DatabaseVersion", dbVersionStr);

			xml.append("JavaVersion", System.getProperty("java.version"));

			xml.append("OS", System.getProperty("os.name"));

			xml.append("Locale", Locale.getDefault().toString());

			xml.append("WebStart", JarSignersHardLinker.isRunningOnWebstart() + "");

			String message = job.message;
			if (message != null) {
				xml.append("Message", message);
			}

			if (job.throwable != null) {
				String stackTrace = ExceptionUtils.getStackTrace(job.throwable);
				xml.append("StackTrace", stackTrace);
			}

			return xml.toString();
		}
	}

	private static class Job {
		private final String message;
		private final Throwable throwable;
		private final Date received = new Date();

		public Job(String message, Throwable throwable) {
			this.message = message;
			this.throwable = throwable;
		}
	}
}
