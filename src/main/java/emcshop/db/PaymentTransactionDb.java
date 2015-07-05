package emcshop.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import com.github.mangstadt.emc.rupees.dto.PaymentTransaction;

public class PaymentTransactionDb {
	private Integer id;
	private Date ts;
	private int amount, balance;
	private String player, reason;

	public PaymentTransactionDb() {
		//empty
	}

	public PaymentTransactionDb(ResultSet rs) throws SQLException {
		id = rs.getInt("id");
		amount = rs.getInt("amount");
		balance = rs.getInt("balance");
		player = rs.getString("playerName");
		ts = rs.getTimestamp("ts"); //TODO do I need to do a new Date() here?
		reason = rs.getString("reason");
	}

	public PaymentTransactionDb(PaymentTransaction transaction) {
		ts = transaction.getTs();
		amount = transaction.getAmount();
		balance = transaction.getBalance();
		player = transaction.getPlayer();
		reason = transaction.getReason();
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Date getTs() {
		return ts;
	}

	public void setTs(Date ts) {
		this.ts = ts;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

	public int getBalance() {
		return balance;
	}

	public void setBalance(int balance) {
		this.balance = balance;
	}

	public String getPlayer() {
		return player;
	}

	public void setPlayer(String player) {
		this.player = player;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + amount;
		result = prime * result + balance;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((player == null) ? 0 : player.hashCode());
		result = prime * result + ((reason == null) ? 0 : reason.hashCode());
		result = prime * result + ((ts == null) ? 0 : ts.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		PaymentTransactionDb other = (PaymentTransactionDb) obj;
		if (amount != other.amount) return false;
		if (balance != other.balance) return false;
		if (id == null) {
			if (other.id != null) return false;
		} else if (!id.equals(other.id)) return false;
		if (player == null) {
			if (other.player != null) return false;
		} else if (!player.equals(other.player)) return false;
		if (reason == null) {
			if (other.reason != null) return false;
		} else if (!reason.equals(other.reason)) return false;
		if (ts == null) {
			if (other.ts != null) return false;
		} else if (!ts.equals(other.ts)) return false;
		return true;
	}
}
