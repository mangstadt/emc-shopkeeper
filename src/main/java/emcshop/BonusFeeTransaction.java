package emcshop;

/**
 * Represents a bonus/fee transaction on the transaction history page.
 * @author Michael Angstadt
 */
public class BonusFeeTransaction extends RupeeTransaction {
	private boolean horseFee, lockFee, eggifyFee, vaultFee, signInBonus, voteBonus;

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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (eggifyFee ? 1231 : 1237);
		result = prime * result + (horseFee ? 1231 : 1237);
		result = prime * result + (lockFee ? 1231 : 1237);
		result = prime * result + (signInBonus ? 1231 : 1237);
		result = prime * result + (vaultFee ? 1231 : 1237);
		result = prime * result + (voteBonus ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		BonusFeeTransaction other = (BonusFeeTransaction) obj;
		if (eggifyFee != other.eggifyFee)
			return false;
		if (horseFee != other.horseFee)
			return false;
		if (lockFee != other.lockFee)
			return false;
		if (signInBonus != other.signInBonus)
			return false;
		if (vaultFee != other.vaultFee)
			return false;
		if (voteBonus != other.voteBonus)
			return false;
		return true;
	}
}
