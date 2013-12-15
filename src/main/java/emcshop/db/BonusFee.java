package emcshop.db;

import java.util.Date;

public class BonusFee {
	private Date since;
	private int horse, lock, eggify, vault, signIn, vote;

	public Date getSince() {
		return since;
	}

	public void setSince(Date since) {
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
}
