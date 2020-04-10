package emcshop.util;

import java.time.LocalDateTime;

/**
 * Represents a date range.
 */
public class DateRange {
	private final LocalDateTime from, to;

	public DateRange(LocalDateTime from, LocalDateTime to) {
		this.from = from;
		this.to = to;
	}

	public LocalDateTime getFrom() {
		return from;
	}

	public LocalDateTime getTo() {
		return to;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((from == null) ? 0 : from.hashCode());
		result = prime * result + ((to == null) ? 0 : to.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		DateRange other = (DateRange) obj;
		if (from == null) {
			if (other.from != null) return false;
		} else if (!from.equals(other.from)) return false;
		if (to == null) {
			if (other.to != null) return false;
		} else if (!to.equals(other.to)) return false;
		return true;
	}
}
