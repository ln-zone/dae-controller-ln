package btcduke.dea.node.controller.zone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.commands.ln.invoices.PaymentReceivedCommand;
import bittech.lib.commands.ln.invoices.RegisterPaymentsListenerCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Notificator;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class PaymentReceivedListener implements Listener {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReceivedListener.class);

	private final Connection connection;

//	private Queue<PaymentReceivedRequest> payments = new ConcurrentLinkedQueue<PaymentReceivedRequest>();

//	private final PaymentsThread paymentsThread = new PaymentsThread();
	
	private Notificator<PaymentReceivedObserver> paymentReceivedNotifier = new Notificator<PaymentReceivedObserver>();

	public PaymentReceivedListener(Connection connection) {
		this.connection = Require.notNull(connection, "connection");
	}

	public void start() throws StoredException {
		RegisterPaymentsListenerCommand cmd = new RegisterPaymentsListenerCommand();
		connection.execute(cmd);

		if (cmd.getError() != null) {
			throw new StoredException("Register command failed", cmd.getError().toException());
		}
		LOGGER.info("------------------ RegisterPaymentsListenerCommand registered");
//		paymentsThread.start();

	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { PaymentReceivedCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}
	
	public void registerPaymentReceivedObserver(PaymentReceivedObserver observer) {
		paymentReceivedNotifier.register(observer);
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof PaymentReceivedCommand) {
			LOGGER.info("------------------ PaymentReceivedCommand");
			try {
				PaymentReceivedCommand cmd = (PaymentReceivedCommand) command;

				(new Log()).param("cmd", cmd).event("Payment received");

//				LOGGER.info("------------------ Adding to payments queue.Size is: " + payments.size());
				paymentReceivedNotifier.notifyThem((m) -> m.onPaymentReceived(cmd.getRequest()));
//				payments.add(cmd.getRequest());
//				LOGGER.info("------------------ Added to queue.Size is: " + payments.size());

				cmd.response = new NoDataResponse();

			} catch (Exception ex) {
				throw new StoredException("Cannot add request to payment list", ex);
			}
		} else {
			throw new StoredException("Command not supported: " + command, null);
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

//	private final class PaymentsThread extends Thread {
//
//		public AtomicBoolean running = new AtomicBoolean(true);
//
//		@Override
//		public void run() {
//			while (running.get() == true) {
//				// LOGGER.info("------------------ Polling payment from queue. Size is: " +
//				// payments.size());
//				PaymentReceivedRequest p = payments.poll();
//				// LOGGER.info("------------------ Payment polled from queue. Size is: " +
//				// payments.size());
//				if (p == null) {
//					// LOGGER.info("------------------ Payment is null. Size is: " +
//					// payments.size());
//					try {
//						Thread.sleep(1000);
//					} catch (InterruptedException e) {
//						// LOGGER.info("------------------ Exception. Interrupted. Size is: " +
//						// payments.size());
//						e.printStackTrace(); // interrupted
//						new StoredException("Payments thread interrupted. Stopping thread", e);
//						running.set(false);
//					}
//				} else {
//
//					(new Log()).param("PaymentReceivedRequest", p).event("Calling onPaymentReceived");
//					// LOGGER.info("------------------ Running onPaymentReceived. Size is: " +
//					// payments.size());
//					onPaymentReceived(p);
//					// LOGGER.info("------------------ onPaymentReceived finished. Size is: " +
//					// payments.size());
//
//				}
//			}
//		}
//	}

//	public abstract void onPaymentReceived(PaymentReceivedRequest paymentReceived);

}
