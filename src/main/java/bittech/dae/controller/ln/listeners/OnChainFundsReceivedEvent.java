package bittech.dae.controller.ln.listeners;

import bittech.lib.utils.Btc;

public interface OnChainFundsReceivedEvent {

	public void onchainFundsReceived(String addr, Btc value);
}
