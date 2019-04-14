package btcduke.dea.node.controller.zone.channels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.lnzone.EstablishedChannel.Status;
import bittech.lib.commands.lnzone.commans.GetChannelStatusCommand;
import bittech.lib.commands.lnzone.commans.GetOfferCommand;
import bittech.lib.commands.lnzone.commans.GetOfferResponse;
import bittech.lib.commands.lnzone.commans.Offer;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelCommand;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelRequest;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelResponse;
import bittech.lib.commands.lnzone.commans.WaitForChannelFundedCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;


public class ClientZoneListener implements Listener {

	@SuppressWarnings("unused")
	private final static Logger LOGGER = LoggerFactory.getLogger(ClientZoneListener.class);

	private final ZoneChannels channels;
	private final Connection controllerConnection;

	public ClientZoneListener(final ZoneChannels channels, final Connection controllerConnection) {
		this.channels = Require.notNull(channels, "channels");
		this.controllerConnection = Require.notNull(controllerConnection, "controllerConnection");
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { /*GetOfferCommand.class,*/ /*OpenZoneChannelCommand.class*/ };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	public static String[] parseUri(String uri) {
		String[] ret = uri.split(":");
		if (ret.length != 3 || !"zone".equals(ret[0])) {
			throw new StoredException("Given uri not correct. Should be 'zone:<ip>:<port>' ", null);
		}

		return ret;
	}

	public static Offer getZoneOffer(String uri) {
		Node node = null;
		try {

			node = new Node("zone-customer");
			String[] parsedUri = parseUri(uri);

			node.connect("zone-peer", parsedUri[1], Integer.parseInt(parsedUri[2]));

			GetOfferCommand getOfferCmd = new GetOfferCommand();
			node.execute("zone-peer", getOfferCmd);

			if (getOfferCmd.getError() != null) {
				throw new StoredException("Peer returned error", getOfferCmd.getError().toException());
			}

			return getOfferCmd.getResponse().offer;
		} catch (Exception ex) {
			throw new StoredException("Cannot get zone offer", ex);
		} finally {
			if (node != null) {
				node.close();
			}
		}
	}

//	private void execGetZoneOfferCommand(GetOfferCommand cmd) {
//		cmd.response = new GetOfferResponse(getZoneOffer(cmd.getRequest().uri));
//	}

	private String newInvoice(Btc value) {
		String msat = Long.toString(value.toMsat());
		String label = "inv" + (long) (Math.random() * Long.MAX_VALUE);
		AddInvoiceCommand cmd = new AddInvoiceCommand(value, label, "Client side funds");

		controllerConnection.execute(cmd);

		if (cmd.getError() != null) {
			throw new StoredException("Cannot create invoice", cmd.getError().toException());
		}

		return cmd.getResponse().payment_request;
	}

	private String getUri() {

		GetInfoCommand cmd = new GetInfoCommand();

		controllerConnection.execute(cmd);

		if (cmd.getError() != null) {
			throw new StoredException("Cannot get info", cmd.getError().toException());
		}

		
		if (cmd.getResponse().uris.size() == 0) {
			throw new StoredException("Cannot get uri", new Exception("No address assigned to node"));
		}

		return cmd.getResponse().uris.get(0);
	}

	private OpenZoneChannelResponse callOpenZoneChannelOnPeer(ZoneChannel channel, Connection peerZoneConnection,
			OpenZoneChannelRequest request, String uri, String invoice) {

		try {

			OpenZoneChannelCommand cmd = new OpenZoneChannelCommand(request.peerAmount, request.feeReserve, request.myAmount,
					request.offer, uri, invoice);

			channel.setOpenChannelRequest(cmd.getRequest());
			peerZoneConnection.execute(cmd);

			if (cmd.getError() != null) {
				throw new StoredException("Peer returned error", cmd.getError().toException());
			}

			channel.establishedChannel.zoneChannelId = cmd.getResponse().zoneChannelId;
			channel.establishedChannel.status = Status.FUNDING;
			channels.update(channel);
			
			return cmd.getResponse();

		} catch (Exception ex) {
			throw new StoredException("Cannot get zone offer", ex);
		}
	}

	private void payInvoice(String invoice) {

		PayInvoiceCommand cmd = new PayInvoiceCommand(invoice, new Btc(), new Btc());

		controllerConnection.execute(cmd);

		if (cmd.getError() != null) {
			throw new StoredException("Cannot pay invoice", cmd.getError().toException());
		}

	}

	private Status getChannelStatus(Connection peerZoneConnection, String zoneChannelId) {
		GetChannelStatusCommand cmd = new GetChannelStatusCommand(zoneChannelId);
		peerZoneConnection.execute(cmd);
		if (cmd.getError() != null) {
			throw new StoredException("Cannot get channel status", cmd.getError().toException());
		}
		return cmd.getResponse().status;
	}

	private String waitForChannelFunded(Connection peerZoneConnection, String zoneChannelId) {
		WaitForChannelFundedCommand cmd = new WaitForChannelFundedCommand(zoneChannelId);
		peerZoneConnection.execute(cmd);
		if (cmd.getError() != null) {
			throw new StoredException("Cannot get channel txId", cmd.getError().toException());
		}
		return cmd.getResponse().fundedTxId;
	}

	private Connection connectToZonePeer(Node node, String uri) {
		node = new Node("zone-customer");
		String[] peerUri = parseUri(uri);
		Connection peerZoneConnection = new Connection(node, "zone-peer");
		node.connect("zone-peer", peerUri[1], Integer.parseInt(peerUri[2]));
		return peerZoneConnection;
	}

	private void execOpenNotOwnerChannelCommand(OpenZoneChannelCommand cmd) {

		Node node = null;

		Log log = new Log();
		log.param("methodName", "execOpenNotOwnerChannelCommand");
		log.event("Open zona channel");

		try {

			log.event("Connectiong to zone peer");
			Connection peerZoneConnection = connectToZonePeer(node, cmd.getRequest().peerUri);

			log.event("Creaing zone channel stub");
			ZoneChannel channel = channels.newChannelNoOwner();
			String tmpZoneChannelId = channel.establishedChannel.zoneChannelId;
			channel.establishedChannel.zonePeerUri = cmd.getRequest().peerUri;

			log.event("Creaing new invoice");
			String myUri = getUri();
			String myInvoice = newInvoice(cmd.getRequest().myAmount);

			log.event("1. Call OpenChannelCommand on for given zone uri");
			OpenZoneChannelResponse resp = callOpenZoneChannelOnPeer(channel, peerZoneConnection, cmd.getRequest(), myUri,
					myInvoice);

			log.event("2. Pay received invoice");
			payInvoice(resp.invoice);

			log.event("3. Update local info with new created channel");
			channels.replace(tmpZoneChannelId, channel);

			log.event("4. Wait for peer to fund channel and receive fundingTxId");
			String fundingTxId = waitForChannelFunded(peerZoneConnection, resp.zoneChannelId);

			channel.establishedChannel.fundingTxId = Require.notEmpty(fundingTxId, "fundingTxId");
			channel.establishedChannel.status = Status.WAITING_FOR_CONFIRMATION;
			channels.update(channel);
//			
//			EstablishedChannel establishedChannel = channels.channelEstablished(channel); // TODO: Dac to wyzej? Inaczej
//																							// zupdatować status
//			establishedChannel.fundingTxId = Require.notEmpty(fundingTxId, "fundingTxId");

			log.event("5. If channel is established, get its status from peer");
			Status status = getChannelStatus(peerZoneConnection, resp.zoneChannelId);
			channel.establishedChannel.status = status;

			channel.establishedChannel.feeReserve = cmd.getRequest().feeReserve;

			channels.update(channel);

			log.event("6. Return response with zoneChannelId");
			cmd.response = new OpenZoneChannelResponse();
			cmd.response.zoneChannelId = resp.zoneChannelId;
		} catch(Exception ex) {
			ex.printStackTrace();
			throw new StoredException("Cannot establish zone channel", ex);
		} finally {
			if (node != null) {
				node.close();
			}
		}

	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
//		if (command instanceof GetOfferCommand) {
//
//			execGetZoneOfferCommand((GetOfferCommand) command);
//
//		} else 
			if (command instanceof OpenZoneChannelCommand) {

			execOpenNotOwnerChannelCommand((OpenZoneChannelCommand) command);

		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}

	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
