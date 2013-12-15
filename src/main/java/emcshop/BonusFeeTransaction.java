package emcshop;

/**
 * Represents a bonus/fee transaction on the transaction history page.
 * @author Michael Angstadt
 */
public class BonusFeeTransaction extends RawTransaction {
	private boolean horseFee, lockFee, eggifyFee, vaultFee, signInBonus, voteBonus;

	public BonusFeeTransaction(RawTransaction transaction) {
		super(transaction);
	}

	public boolean isHorseFee() {
		return horseFee;
	}

	public void setHorseFee(boolean horseFee) {
		this.horseFee = horseFee;
	}

	public boolean isLockFee() {
		return lockFee;
	}

	public void setLockFee(boolean lockFee) {
		this.lockFee = lockFee;
	}

	public boolean isEggifyFee() {
		return eggifyFee;
	}

	public void setEggifyFee(boolean eggifyFee) {
		this.eggifyFee = eggifyFee;
	}

	public boolean isVaultFee() {
		return vaultFee;
	}

	public void setVaultFee(boolean vaultFee) {
		this.vaultFee = vaultFee;
	}

	public boolean isSignInBonus() {
		return signInBonus;
	}

	public void setSignInBonus(boolean signInBonus) {
		this.signInBonus = signInBonus;
	}

	public boolean isVoteBonus() {
		return voteBonus;
	}

	public void setVoteBonus(boolean voteBonus) {
		this.voteBonus = voteBonus;
	}
}
