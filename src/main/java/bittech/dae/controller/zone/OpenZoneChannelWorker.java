package bittech.dae.controller.zone;

import bittech.dae.controller.zone.channels.StandardChannelChangeObserver;
import bittech.dae.controller.zone.channels.ZoneChannel;
import bittech.dae.controller.zone.channels.ZoneChannels;
import bittech.lib.commands.ln.channels.ChannelChangedRequest;
import bittech.lib.commands.ln.channels.OpenChannelCommand;
import bittech.lib.commands.ln.channels.OpenChannelResponse;
import bittech.lib.commands.ln.invoices.AddInvoiceRequest;
import bittech.lib.commands.ln.invoices.DecodeInvoiceCommand;
import bittech.lib.commands.ln.invoices.DecodeInvoiceResponse;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.ln.invoices.PaymentReceivedRequest;
import bittech.lib.commands.ln.peers.ConnectPeerCommand;
import bittech.lib.commands.ln.peers.ListPeersWithChannelsCommand;
import bittech.lib.commands.ln.peers.ListPeersWithChannelsResponse.Peer;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelRequest;
import bittech.lib.protocol.Connection;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class OpenZoneChannelWorker implements StandardChannelChangeObserver, PaymentReceivedObserver {

	private ZoneChannels channels;
	private Connection controllerConnection;

	public OpenZoneChannelWorker(ZoneChannels channels, Connection contollerConnection) {
		this.channels = Require.notNull(channels, "channels");
		this.controllerConnection = Require.notNull(contollerConnection, "contollerConnection");
	}

	private void verifyOffer(OpenZoneChannelRequest request) {
		if (Utils.deepEquals(channels.getOffer(), request.offer) == false) {
			throw new StoredException("Requested offer not accepted", null);
		}
	}

	private void verifyIfNoChannelYet(OpenZoneChannelRequest request) {
		ListPeersWithChannelsCommand listPeersCommand = new ListPeersWithChannelsCommand();
		controllerConnection.execute(listPeersCommand);
		if (listPeersCommand.getError() != null) {
			throw new StoredException("Cannot list existing peers", listPeersCommand.getError().toException());
		}

		String peerId = request.peerUri.substring(0, request.peerUri.indexOf("@"));
		Require.notEmpty(peerId, "peerId");

		for (Peer peer : listPeersCommand.getResponse().peers) {
			if (peer.openedChannels == null) {
				continue;
			}
			if (!peerId.equals(peer.id)) {
				continue;
			}
//			for (ListPeersResponse.Channel channel : peer.openedChannels) {
//				if (!"ONCHAIN".equals(channel.state) && !"CLOSINGD_COMPLETE".equals(channel.state)) {
//					throw new StoredException("We have already channel created. Please close old channel first.", null);
//				}
//			}
		}

	}

	private DecodeInvoiceResponse verifyInvoice(OpenZoneChannelRequest request) {

		// LOGGER.info("------------ Verify invoice");
		Require.notNull(request.invoice, "invoice");

		DecodeInvoiceCommand decodePayCmd = new DecodeInvoiceCommand(request.invoice);
		controllerConnection.execute(decodePayCmd);
		if (decodePayCmd.getError() != null) {
			throw new StoredException("Given invoice not correct", decodePayCmd.getError().toException());
		}

		Require.notNull(decodePayCmd.getResponse(), "decodePayCmd.getResponse()");
		Require.notNull(request.myAmount, "clientSide");
		if (!decodePayCmd.getResponse().amount.equals(request.myAmount)) {
			throw new StoredException("Given invoice has wrong amount. Should be " + request.myAmount + " but there is "
					+ decodePayCmd.getResponse().amount, null);
		}
		return decodePayCmd.getResponse();
	}

	private void verifyUri(OpenZoneChannelRequest request) {

		Require.notNull(request.invoice, "invoice");

		ConnectPeerCommand connectCommand = new ConnectPeerCommand(request.peerUri);
		controllerConnection.execute(connectCommand);
		if (connectCommand.getError() != null) {
			throw new StoredException("Cannot connect to uri: " + request.peerUri, null);
		}

	}

	public ZoneChannel onOpenChannel(OpenZoneChannelRequest request) {
		try {
			new Log().param("module", "zone").param("request", request).event("onOpenChannel");
			Require.notNull(request, "request");

			verifyOffer(request);
			verifyIfNoChannelYet(request);
			DecodeInvoiceResponse decPayResp = verifyInvoice(request);
			verifyUri(request);

			Btc costs = request.offer.fixedCost.add(request.feeReserve).add(request.myAmount);

			String label = "lnzone" + (long) (Math.random() * Long.MAX_VALUE);
			AddInvoiceRequest invRequest = new AddInvoiceRequest(costs, label, "Open lnzone channel");

			return channels.newChannel(request, invRequest, decPayResp);

		} catch (Exception ex) {
			throw new StoredException("Open channel request failed", ex);
		}

	}
	
	@Override
	public synchronized void onStandardChannelChanged(ChannelChangedRequest request) {
		try {
			Log log = new Log().param("module", "zone").param("request", request);
			log.event("onChannelChanged");
			Require.notNull(request, "request");

			ZoneChannel channel = channels.findByTxId(request.fundingTxId);
			if (channel == null) {
				log.event("This is not ln.zone channel");
				return;
			}
			
			Utils.prn(request.state);
			if (!"CHANNELD_NORMAL".equals(request.state)) {
				log.event("Channel is NOT in CHANNELD_NORMAL state");
				return; // Channel not ready yet
			}
			
			Utils.prn(channel.establishedChannel.owner);
			if (channel.establishedChannel.owner == false) {
				log.event("We are not owner of this channel");
				return;
			}
			
			if (channel.establishedChannel.status.equalOrAfter(bittech.lib.commands.lnzone.EstablishedChannel.Status.SENDING_CLIENT_PART)) {
				log.event("Client part already sending or already sent");
				return; // Channel not ready yet
			}			
			
			if (channel.getOpenChannelRequest().invoice == null) {
				new StoredException("Invoice is null", null);
				return; // no need to trasfer amount. Client wants to have zero at the beginnig
			}
			
			channel.establishedChannel.status = bittech.lib.commands.lnzone.EstablishedChannel.Status.SENDING_CLIENT_PART;
			channels.update(channel);
			
			PayInvoiceCommand cmd = new PayInvoiceCommand(channel.getDecodedInvoice().payment_hash, new Btc(), new Btc());
			controllerConnection.execute(cmd);
			if (cmd.getError() != null) {
				throw new StoredException("Send pay invoice failed", cmd.getError().toException());
			}
			
/*
			List<RouteElement> route = new LinkedList<RouteElement>();
			RouteElement rel = new RouteElement();
			rel.channel = request.shortChannelId;
			rel.id = channel.getDecodedInvoice().payee;
			rel.delay = 9;
			rel.msatoshi = channel.getDecodedInvoice().msatoshi;
			route.add(rel);

			SendPayCommand sendPayCommand = new SendPayCommand(route, channel.getDecodedInvoice().payment_hash);
			controllerConnection.execute(sendPayCommand);
			if (sendPayCommand.getError() != null) {
				throw new StoredException("Send pay invoice failed", sendPayCommand.getError().toException());
			}
			
			WaitSendPayCommand waitSendPayCommand = new WaitSendPayCommand(channel.getDecodedInvoice().payment_hash, 5);
			controllerConnection.execute(waitSendPayCommand);
			if (waitSendPayCommand.getError() != null) {
				throw new StoredException("Wait for pay invoice failed", waitSendPayCommand.getError().toException());
			}
*/
			channel.establishedChannel.status = bittech.lib.commands.lnzone.EstablishedChannel.Status.ACTIVE;
			channels.update(channel);
		} catch (Exception ex) {
			throw new StoredException("Cannot continue opening LN channel process", ex);
		}
	}

	@Override
	public void onPaymentReceived(PaymentReceivedRequest paymentReceived) {
		Log log = new Log().param("module", "zone").param("paymentReceived", paymentReceived);
		log.event("onPayment");
		ZoneChannel channel = channels.channelPaid(paymentReceived.label);
		log.param("channel", channel).event("Channel paid called");
		if (channel == null) {
			return; // That was not payment related with channel
		}
		log.event("Calling createChannel");
		fundZoneChannel(channel);
	}

	private void fundZoneChannel(ZoneChannel channel) {

		Log log = new Log().param("channel", channel);

		channel.establishedChannel.status = bittech.lib.commands.lnzone.EstablishedChannel.Status.FUNDING;
		channels.update(channel);
		log.event("Status set to FUNDING_CHANNEL");

		log.param("peerUri", channel.getOpenChannelRequest().peerUri).event("Connecting to peer URI");
		String peerNodeId = connectToUri(channel.getOpenChannelRequest().peerUri);

		Btc capacity = channel.getOpenChannelRequest().myAmount.add(channel.getOpenChannelRequest().peerAmount);

		log.param("nodeId", peerNodeId).param("capacity", capacity).event("Funding channel");
		channel.setFundChannelResponse(fundChannel(peerNodeId, capacity));

		log.event("Channel funded. Changing status to WAITING_FOR_CONFIRMATION");
		channel.establishedChannel.status = bittech.lib.commands.lnzone.EstablishedChannel.Status.WAITING_FOR_CONFIRMATION;
		channels.update(channel);
	}

	private String connectToUri(final String uri) {
		ConnectPeerCommand connectCommand = new ConnectPeerCommand(uri);
		controllerConnection.execute(connectCommand);
		if (connectCommand.getError() != null) {
			throw new StoredException("Cannot connect to URI: " + uri, connectCommand.getError().toException());
		}
		
		return uri.split("@")[0]; // TODO: Not very clear
	}

	private OpenChannelResponse fundChannel(String nodeId, Btc capacity) {
		Require.notNull(nodeId, "nodeId");
		Require.notNull(capacity, "capacity");
		Log log = new Log();
		System.out.println("nodeId : " + nodeId);
		log.param("nodeId", nodeId).param("capacity", capacity).event("Createing fund channel comand");
		OpenChannelCommand fundChannelCommand = new OpenChannelCommand(nodeId, capacity, new Btc("0"));
		log.param("fundChannelCommand", fundChannelCommand).event("Executing command");
		controllerConnection.execute(fundChannelCommand);
		log.event("Command executed");
		if (fundChannelCommand.getError() != null) {
			log.param("error", fundChannelCommand.getError()).event("Command returned errror");
			throw new StoredException("Cannot fund channel with: " + nodeId,
					fundChannelCommand.getError().toException());
		}
		log.param("response", fundChannelCommand.getResponse()).event("Returning response");
		return fundChannelCommand.getResponse();
	}

	
}
