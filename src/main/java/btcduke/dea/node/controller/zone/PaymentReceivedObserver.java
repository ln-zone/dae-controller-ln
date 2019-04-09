package btcduke.dea.node.controller.zone;

import bittech.lib.commands.ln.invoices.PaymentReceivedRequest;

public interface PaymentReceivedObserver {

	public void onPaymentReceived(PaymentReceivedRequest paymentReceivedRequest);
}
