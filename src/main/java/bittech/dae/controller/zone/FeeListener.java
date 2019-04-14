package bittech.dae.controller.zone;

import java.util.HashMap;
import java.util.Map;

import bittech.dae.controller.zone.channels.ClientZoneListener;
import bittech.dae.controller.zone.channels.ZoneChannel;
import bittech.dae.controller.zone.channels.ZoneChannels;
import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.ln.invoices.PaymentReceivedRequest;
import bittech.lib.commands.lnzone.external.IncreaseFeeDepositCommand;
import bittech.lib.commands.lnzone.external.IncreaseFeeDepositResponse;
import bittech.lib.commands.lnzone.internal.IncreaseZoneFeeDepositCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.Node;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class FeeListener implements Listener, PaymentReceivedObserver {

	private final Connection controllerConnection;
	private final ZoneChannels channels;

	private Map<String, String> increaseFeeRequest = new HashMap<String, String>();

	public FeeListener(ZoneChannels channels, Connection controllerConnection) {
		this.channels = Require.notNull(channels, "channels");
		this.controllerConnection = Require.notNull(controllerConnection, "controllerConnection");
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { IncreaseFeeDepositCommand.class, IncreaseZoneFeeDepositCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof IncreaseFeeDepositCommand) {

			IncreaseFeeDepositCommand cmd = (IncreaseFeeDepositCommand) command;
			increaseFeeDeposit(cmd);

		} else if (command instanceof IncreaseZoneFeeDepositCommand) {

			IncreaseZoneFeeDepositCommand cmd = (IncreaseZoneFeeDepositCommand) command;
			increaseZoneFeeDeposit(cmd);

		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}
	}

	private void increaseZoneFeeDeposit(IncreaseZoneFeeDepositCommand cmd) {
		try {
			ZoneChannel channel = channels.findZoneChannelById(cmd.getRequest().zoneChannelId);
			if (channel == null) {
				throw new StoredException("No channel found with zone channel id = " + cmd.getRequest().zoneChannelId,
						null);
			}

			if (channel.establishedChannel.owner == true) {
				throw new StoredException("Channel owner cannot schedule increase fee deposit", null);
			}

			if (channel.establishedChannel.zonePeerUri == null) {
				throw new StoredException(
						"No zone peer Uri available for zone channel with id = " + cmd.getRequest().zoneChannelId,
						null);
			}

			String invoiceBolt = null;
			Node node = null;
			try {

				node = new Node("zone-customer-123"); // TODO: Think about names for nodes/connections
				String[] parsedUri = ClientZoneListener.parseUri(channel.establishedChannel.zonePeerUri);

				node.connect("zone-peer", parsedUri[1], Integer.parseInt(parsedUri[2]));

				IncreaseFeeDepositCommand increaseFeeDepositCmd = new IncreaseFeeDepositCommand(
						cmd.getRequest().zoneChannelId, cmd.getRequest().amount);
				node.execute("zone-peer", increaseFeeDepositCmd);

				if (increaseFeeDepositCmd.getError() != null) {
					throw new StoredException("Peer returned error", increaseFeeDepositCmd.getError().toException());
				}

				invoiceBolt = increaseFeeDepositCmd.getResponse().invoiceBolt11;
			} catch (Exception ex) {
				throw new StoredException(
						"Cannot increase fee deposit for peer " + channel.establishedChannel.zonePeerUri, ex);
			} finally {
				if (node != null) {
					node.close();
				}
			}

			PayInvoiceCommand payCommand = new PayInvoiceCommand(invoiceBolt, new Btc(), new Btc());

			controllerConnection.execute(payCommand);

			if (payCommand.getError() != null) {
				throw new StoredException("Cannot pay invoice. Peer returned error",
						payCommand.getError().toException());
			}

			channel.establishedChannel.feeReserve = channel.establishedChannel.feeReserve.add(cmd.getRequest().amount);
			channels.update(channel);

			cmd.response = new NoDataResponse();
		} catch (Exception ex) {
			throw new StoredException("Cannot increase zone fee deposit", ex);
		}
	}

	private void increaseFeeDeposit(IncreaseFeeDepositCommand cmd) {
		Log log = new Log().param("cmd", "IncreaseFeeDepositCommand").param("zoneChannelId",
				cmd.getRequest().zoneChannelId);

		try {

			ZoneChannel channel = channels.findZoneChannelById(cmd.getRequest().zoneChannelId);

			if (channel == null) {
				log.event("Channel is null");
				throw new StoredException("No channel with zone channel id = " + cmd.getRequest().zoneChannelId, null);
			}
			log.param("channel", channel);

			if (cmd.getRequest().amount == null || !cmd.getRequest().amount.hasValue()) {
				log.event("Amount not provided");
				throw new StoredException("Amount not provided", null);
			}

			String label = "incfee" + Utils.rand();
			String description = "Increase fee deposit";
			AddInvoiceCommand invoiceCmd = new AddInvoiceCommand(cmd.getRequest().amount, label, description);
			log.param("invoiceCmd", invoiceCmd);
			log.event("Executing invoice command");
			controllerConnection.execute(invoiceCmd);
			log.param("invoiceCmd after exec", invoiceCmd);
			log.event("Invoice command executed");
			if (invoiceCmd.getError() != null) {
				log.event("Throwing exception");
				throw invoiceCmd.getError().toException();
			} else {
				increaseFeeRequest.put(label, cmd.getRequest().zoneChannelId);
				cmd.response = new IncreaseFeeDepositResponse();
				cmd.response.invoiceBolt11 = invoiceCmd.getResponse().payment_request;
				log.event("Done. Invoice generated waiting for payment");
			}

		} catch (Exception ex) {
			throw new StoredException("Cannot increase fee deposit", ex);
		}
	}

	public synchronized void onPaymentReceived(PaymentReceivedRequest paymentReceived) {
		try {
			Log log = new Log().param("method", "FeeListener.onPayment").param("paymentReceived", paymentReceived);

			if (!paymentReceived.label.startsWith("incfee")) {
				log.event("Payment not related with increase fee");
				return; // Payment not related with increase fee
			}
			System.out.println("LABEL: " + paymentReceived.label);

			String zoneChannelId = increaseFeeRequest.get(paymentReceived.label);
			log.param("zoneChannelId", zoneChannelId);
			if (zoneChannelId == null) {
				log.event("Received increase fee payment that was not created before.");
				new StoredException(
						"Received increase fee payment that was not created before. Label is " + paymentReceived.label,
						null);
				return; // TODO: Should I throw this exception? Rather not
			}
			ZoneChannel channel = channels.findZoneChannelById(zoneChannelId);
			log.param("channel", channel).event("Adding fee reserve");
			Utils.prn(channel.establishedChannel.feeReserve);
			channel.establishedChannel.feeReserve = channel.establishedChannel.feeReserve
					.add(paymentReceived.amount);
			log.event("Fee is " + channel.establishedChannel.feeReserve);
			channels.update(channel);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
