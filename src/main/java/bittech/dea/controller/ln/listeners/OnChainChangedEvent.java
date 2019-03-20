package bittech.dea.controller.ln.listeners;

import bittech.lib.commands.ln.onchain.WalletBalanceResponse;

public interface OnChainChangedEvent {

	public void onChange(WalletBalanceResponse walletBalance);
}
