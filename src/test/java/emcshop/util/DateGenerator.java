package emcshop.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class DateGenerator {
	private LocalDateTime date = LocalDateTime.now();
	private final List<LocalDateTime> generated = new ArrayList<>();
	private final ChronoUnit field;
	private final int amount;

	public DateGenerator() {
		this(ChronoUnit.HOURS, 1);
	}

	/**
	 * @param field the time component to increment by
	 * @param amount the amount to increment by
	 */
	public DateGenerator(ChronoUnit field, int amount) {
		this.field = field;
		this.amount = amount;
	}

	/**
	 * Gets the next generated date.
	 * @return the next date
	 */
	public LocalDateTime next() {
		date = date.plus(amount, field);
		generated.add(date);
		return date;
	}

	/**
	 * Gets a previously generated date.
	 * @param index the list index
	 * @return the previously generated date
	 */
	public LocalDateTime getGenerated(int index) {
		return generated.get(index);
	}
}
