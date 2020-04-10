package emcshop.db;

import java.time.LocalDateTime;

public class BonusFee {
	private LocalDateTime since, highestBalanceTs;
	private int horse, lock, eggify, vault, signIn, vote, mail, highestBalance;

	public LocalDateTime getSince() {
		return since;
	}

	public void setSince(LocalDateTime since) {
		this.since = since;
	}

	public int getSignIn() {
		return signIn;
	}

	public void setSignIn(int signIn) {
		this.signIn = signIn;
	}

	public int getHorse() {
		return horse;
	}

	public void setHorse(int horse) {
		this.horse = horse;
	}

	public int getLock() {
		return lock;
	}

	public void setLock(int lock) {
		this.lock = lock;
	}

	public int getEggify() {
		return eggify;
	}

	public void setEggify(int eggify) {
		this.eggify = eggify;
	}

	public int getVault() {
		return vault;
	}

	public void setVault(int vault) {
		this.vault = vault;
	}

	public int getVote() {
		return vote;
	}

	public void setVote(int vote) {
		this.vote = vote;
	}

	public int getMail() {
		return mail;
	}

	public void setMail(int mail) {
		this.mail = mail;
	}

	public LocalDateTime getHighestBalanceTs() {
		return highestBalanceTs;
	}

	public void setHighestBalanceTs(LocalDateTime highestBalanceTs) {
		this.highestBalanceTs = highestBalanceTs;
	}

	public int getHighestBalance() {
		return highestBalance;
	}

	public void setHighestBalance(int highestBalance) {
		this.highestBalance = highestBalance;
	}
}
