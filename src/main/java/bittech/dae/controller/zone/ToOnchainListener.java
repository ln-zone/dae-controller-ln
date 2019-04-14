package bittech.dae.controller.zone;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.dae.controller.zone.Invoices.Invoice;
import bittech.dae.controller.zone.channels.ClientZoneListener;
import bittech.dae.controller.zone.channels.CompoundChannels;
import bittech.dae.controller.zone.channels.ZoneChannel;
import bittech.dae.controller.zone.channels.ZoneChannels;
import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.ln.invoices.DecodeInvoiceCommand;
import bittech.lib.commands.ln.invoices.GetPaymentStatusCommand;
import bittech.lib.commands.ln.invoices.GetPaymentStatusResponse;
import bittech.lib.commands.ln.invoices.GetPaymentStatusResponse.InvoiceStatus;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.ln.invoices.PaymentReceivedRequest;
import bittech.lib.commands.ln.invoices.RegisterPaymentsListenerCommand;
import bittech.lib.commands.ln.onchain.NewAddressCommand;
import bittech.lib.commands.ln.onchain.SendOnChainCommand;
import bittech.lib.commands.lnzone.CompoundChannel;
import bittech.lib.commands.lnzone.external.ToOnchainCommand;
import bittech.lib.commands.lnzone.external.ToOnchainResponse;
import bittech.lib.commands.lnzone.internal.RebalanceToOnchainCommand;
import bittech.lib.commands.lnzone.internal.RebalanceToOnchainResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Btc;
import bittech.lib.utils.FormattedTime;
import bittech.lib.utils.FormattedTime.Precision;
import bittech.lib.utils.Require;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class ToOnchainListener implements Listener, PaymentReceivedObserver {

	private static final Logger LOGGER = LoggerFactory.getLogger(ToOnchainListener.class);

	public static final class Payment {
		String invoice_label;
		Btc amount;
		String addr;
	}

	private Invoices invoices = new Invoices();
	// //
	private Queue<Payment> payments = new ConcurrentLinkedQueue<Payment>();

	private final ZoneChannels channels;
	
	private final CompoundChannels compoundChannels;

	private final Connection controllerConnection;

	private final PaymentsThread paymentsThread = new PaymentsThread();

	public ToOnchainListener(final ZoneChannels channels, final Connection controllerConnection, CompoundChannels compoundChannels) {
		this.channels = Require.notNull(channels, "channels");
		this.controllerConnection = Require.notNull(controllerConnection, "controllerConnection");
		this.compoundChannels = Require.notNull(compoundChannels, "compoundChannels");
	}

	//
	// public Map<String, Invoice> getInvoices() {
	// return new HashMap<String, Invoice>(invoices); // TODO: copy?
	// }

	public List<Payment> getPayments() {
		return new LinkedList<Payment>(payments);
	}

	public void start() throws StoredException {
		RegisterPaymentsListenerCommand cmd = new RegisterPaymentsListenerCommand();
		controllerConnection.execute(cmd);

		if (cmd.getError() != null) {
			throw new StoredException("Register command failed: " + cmd.getError().message, null);
		}

		paymentsThread.start();

	}

	private Invoice addInvoice(String zoneChannelId, String label, String addr, Btc amount, String bolt11)
			throws StoredException {

		Invoice inv = new Invoice();
		inv.label = label;
		inv.status = InvoiceStatus.UNPAID;
		inv.zoneChannelId = zoneChannelId;
		inv.bolt_11 = bolt11;
		inv.addr = addr;
		inv.amount = amount;
		inv.created_at = FormattedTime.now(Precision.SECONDS);

		invoices.add(inv);

		return inv;
	}

	@Override
	public Class<?>[] getListeningCommands() {
		// TODO Auto-generated method stub
		return new Class<?>[] { ToOnchainCommand.class, GetPaymentStatusCommand.class,
				RebalanceToOnchainCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		// TODO Auto-generated method stub
		return null;
	}

	public Invoices getInvoices() {
		return invoices;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {

		if (command instanceof ToOnchainCommand) {
			Invoice inv = null;
			Log log = new Log();
			try {
				ToOnchainCommand cmd = (ToOnchainCommand) command;

				ZoneChannel channel = channels.findZoneChannelById(cmd.getRequest().zoneChannelId);

				if (channel == null) {
					throw new StoredException("No zone channel found with id " + cmd.getRequest().zoneChannelId, null);
				}

				Btc amount = Require.notNull(cmd.getRequest().amount, "amount");
				String addr = Require.notNull(cmd.getRequest().addr, "addr");

				log.param("amount", amount.toString());
				log.param("addr", addr);
				log.event("Createing ln change request");

				// SystemExecCommand cmdExec = new
				// SystemExecCommand("bitcoin-cli validateaddress " + addr + "
				// ",
				// 3000);
				// node.execute("hub", cmdExec);
				//
				// if (cmdExec.getResponse().output.contains("\"isvalid\":
				// false")) {
				// cmd.error = new ErrorResponse(0, "This is not correct bitcoin
				// address: " + addr);
				// return;
				// }

				String label = "toonchain" + (long) (Math.random() * Long.MAX_VALUE);
				AddInvoiceCommand cmdInvoice = new AddInvoiceCommand(amount, label, addr);
				LOGGER.debug(cmdInvoice.toString());
				controllerConnection.execute(cmdInvoice);

				if (cmdInvoice.getError() != null) {
					cmd.error = new ErrorResponse(0, "Cannot generate invoice. " + cmdInvoice.getError().message
							+ ". Exception ID: " + cmdInvoice.getError().exceptionId);
					return;
				}

				inv = addInvoice(cmd.getRequest().zoneChannelId, label, addr, cmd.getRequest().amount,
						cmdInvoice.getResponse().payment_request);

				cmd.response = new ToOnchainResponse(cmdInvoice.getResponse().payment_request);

			} catch (Exception ex) {
				if (inv != null) {
					inv.status = InvoiceStatus.ERROR;
				}
				throw new StoredException("Cannot schedule return given satoshis on-chain", ex);
			}
		} else if (command instanceof GetPaymentStatusCommand) {
			try {
				GetPaymentStatusCommand cmd = (GetPaymentStatusCommand) command;
				String requestId = cmd.getRequest().requestId;
				Invoice inv = invoices.getByRequestId(requestId);
				if (inv != null) {
					cmd.response = new GetPaymentStatusResponse(inv.status);
				} else {
					cmd.response = new GetPaymentStatusResponse(InvoiceStatus.NO_STATUS);
				}
			} catch (Exception ex) {
				throw new StoredException("Cannot get payment status", ex);
			}
		} else if (command instanceof RebalanceToOnchainCommand) {
			try {
				RebalanceToOnchainCommand cmd = (RebalanceToOnchainCommand) command;
				rebalanceToOnchainCommand(cmd);

				// channel.owner
				//
				// String requestId = cmd.getRequest().requestId;
				// Invoice inv = invoices.getByRequestId(requestId);
				// if (inv != null) {
				// cmd.response = new GetPaymentStatusResponse(inv.status);
				// } else {
				// cmd.response = new GetPaymentStatusResponse(InvoiceStatus.NO_STATUS);
				// }
			} catch (Exception ex) {
				throw new StoredException("Cannot rebalance to onchain", ex);
			}
		} else {
			throw new StoredException("Command not supported: " + command, null);
		}
	}

	private String newAddr() {
		NewAddressCommand cmd = new NewAddressCommand();
		controllerConnection.execute(cmd);
		if (cmd.getError() != null) {
			throw new StoredException("Cannot create new addr", cmd.getError().toException());
		}
		return cmd.getResponse().address;
	}

	private void payInvoice(CompoundChannel channel, String invoiceBolt11) {
		
//		String shortChnnelId = getShortChannelId(channel.fundingTxId);
		
		DecodeInvoiceCommand decodePayCmd = new DecodeInvoiceCommand(invoiceBolt11);
		controllerConnection.execute(decodePayCmd);
		if (decodePayCmd.getError() != null) {
			throw new StoredException("Cannot decode invoice " + invoiceBolt11, decodePayCmd.getError().toException());
		}

		PayInvoiceCommand payInvoiceCommand = new PayInvoiceCommand(decodePayCmd.getResponse().payment_hash, Btc.noValue(), Btc.noValue());
		controllerConnection.execute(payInvoiceCommand);
		if (payInvoiceCommand.getError() != null) {
			throw new StoredException("Cannot pay invoice", payInvoiceCommand.getError().toException());
		}
		
		//		List<RouteElement> route = new LinkedList<RouteElement>();
//		RouteElement rel = new RouteElement();
//		rel.channel = channel.shortChannelId;
//		rel.id = decodePayCmd.getResponse().payee;
//		rel.delay = 9;
//		rel.msatoshi = decodePayCmd.getResponse().msatoshi;
//		route.add(rel);
//
//		SendPayCommand cmd = new SendPayCommand(route, decodePayCmd.getResponse().payment_hash);
//		controllerConnection.execute(cmd);
//		if (cmd.getError() != null) {
//			throw new StoredException("Cannot pay invoice", cmd.getError().toException());
//		}
//		
//		WaitSendPayCommand waitSendPayCommand = new WaitSendPayCommand(decodePayCmd.getResponse().payment_hash, 5);
//		controllerConnection.execute(waitSendPayCommand);
//		if (waitSendPayCommand.getError() != null) {
//			throw new StoredException("Wait for pay invoice failed", waitSendPayCommand.getError().toException());
//		}
	}

	private String askZonePeerForInvoice(ZoneChannel channel, Btc amount, String addr) {
		Node node = null;
		try {

			node = new Node("zone-customer-" + Utils.rand()); // TODO: Think about names for nodes/connections
			String[] parsedUri = ClientZoneListener.parseUri(channel.establishedChannel.zonePeerUri);

			node.connect("zone-peer", parsedUri[1], Integer.parseInt(parsedUri[2]));

			ToOnchainCommand toOnchainCommand = new ToOnchainCommand(channel.establishedChannel.zoneChannelId, amount, addr);
			node.execute("zone-peer", toOnchainCommand);

			if (toOnchainCommand.getError() != null) {
				throw new StoredException("Zone peer returned error", toOnchainCommand.getError().toException());
			}

			return toOnchainCommand.getResponse().invoiceBolt11;
		} finally {
			if (node != null) {
				node.close();
			}
		}
	}

	private void rebalanceToOnchainCommand(RebalanceToOnchainCommand cmd) {
		
		CompoundChannel compoundChannel = compoundChannels.findZoneChannelById(cmd.getRequest().zoneChannelId);
		
		ZoneChannel channel = channels.findZoneChannelById(cmd.getRequest().zoneChannelId);

		if (channel == null) {
			throw new StoredException("No zone channel found with id " + cmd.getRequest().zoneChannelId, null);
		}

		if (channel.establishedChannel.owner == true) {
			throw new StoredException("Channel owner cannot schedule rebalance to onchain", null);
		}

		if (channel.establishedChannel.zonePeerUri == null) {
			throw new StoredException(
					"No zone peer Uri available for zone channel with id = " + cmd.getRequest().zoneChannelId, null);
		}

		if(channel.establishedChannel.myAmount.toMsat() < cmd.getRequest().amount.toMsat()) {
			throw new StoredException("Not anough funds to rebalance. Needed at least " + cmd.getRequest().amount + " but there is " + channel.establishedChannel.myAmount, null);
		} //TODOD: Use ToReceive

		String newAddr = newAddr();

		String invoiceBolt = askZonePeerForInvoice(channel, cmd.getRequest().amount, newAddr);

		payInvoice(compoundChannel, invoiceBolt);

		cmd.response = new RebalanceToOnchainResponse(newAddr);
	}

	private final class PaymentsThread extends Thread {

		public AtomicBoolean running = new AtomicBoolean(true);

		@Override
		public void run() {
			while (running.get() == true) {
				Payment p = payments.poll();
				if (p == null) {

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace(); // interrupted
						new StoredException("Payments thread interrupted. Stopping thread", e);
						running.set(false);
					}

					continue;
				}

				SendOnChainCommand withdrawCmd = new SendOnChainCommand(p.addr, p.amount);
				Invoice inv = null;
				try {
					LOGGER.debug("Sending on-chain payment to addr: " + p.addr);
					controllerConnection.execute(withdrawCmd);
					if (withdrawCmd.getError() != null) {
						throw new Exception(withdrawCmd.getError().message);
					}
					LOGGER.debug("PAID on-chain payment to addr: " + p.addr);
					inv = invoices.getByLabel(p.invoice_label);
					inv.status = InvoiceStatus.COMPLETED;
					inv.refund_at = FormattedTime.now(Precision.SECONDS);
				} catch (Exception e) {
					if (inv != null) {
						inv.status = InvoiceStatus.ERROR;
					}
					new StoredException("Cannot send on-chain payment to address: " + p.addr, e);
				}

			}
		}
	}

	public void onPaymentReceived(PaymentReceivedRequest paymentReceived) {

		String label = paymentReceived.label;
		LOGGER.info("Payment received for label " + label);
		if (!label.startsWith("toonchain")) {
			LOGGER.info("Not invoice generated by this listener: " + label);
			return;
		}
		if (invoices.contains(label) == false) {
			new StoredException("Invoices do not contains payment for " + label, null);
			return;
		}
		LOGGER.debug("Payment received for label " + label + " with amount " + paymentReceived.amount);

		Payment p = new Payment();
		p.amount = paymentReceived.amount_received.sub(new Btc("1000"));
		Invoice inv = invoices.getByLabel(label);
		p.invoice_label = label;
		p.addr = inv.addr;
		inv.status = InvoiceStatus.PAID;
		inv.paid_at = FormattedTime.now(Precision.SECONDS);

		payments.add(p);
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
