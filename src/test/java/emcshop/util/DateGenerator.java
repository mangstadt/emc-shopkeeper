package emcshop.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DateGenerator {
	private final Calendar calendar = Calendar.getInstance();
	private final List<Date> generated = new ArrayList<Date>();
	private final int field, amount;

	public DateGenerator() {
		this(Calendar.HOUR_OF_DAY, 1);
	}

	/**
	 * @param field the {@link Calendar} field
	 * @param amount the amount to increment by
	 */
	public DateGenerator(int field, int amount) {
		this.field = field;
		this.amount = amount;
	}

	/**
	 * Gets the next generated date.
	 * @return the next date
	 */
	public Date next() {
		calendar.add(field, amount);
		Date date = calendar.getTime();
		generated.add(date);
		return date;
	}

	/**
	 * Gets a previously generated date.
	 * @param index the list index
	 * @return the previously generated date
	 */
	public Date getGenerated(int index) {
		return generated.get(index);
	}
}
