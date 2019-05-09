package bittech.dae.controller.zone;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import bittech.dae.controller.zone.channels.ZoneChannels;
import bittech.lib.commands.lnzone.commans.Offer;
import bittech.lib.commands.lnzone.external.GetPeerOfferCommand;
import bittech.lib.commands.lnzone.external.GetPeerOfferResponse;
import bittech.lib.commands.lnzone.internal.GetOfferCommand;
import bittech.lib.commands.lnzone.internal.GetOfferResponse;
import bittech.lib.commands.lnzone.internal.SetOfferCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.Node;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class OfferListener implements Listener {

	private final String myUri;

	final ZoneChannels channels;

	public String getMyUri() {
		return myUri;
	}

	public OfferListener(ZoneChannels channels, final int listeningPort) {

		this.channels = Require.notNull(channels, "channels");
		this.myUri = "zone:" + getMyIp() + ":" + Require.inRange(listeningPort, 1, Short.MAX_VALUE, "listeningPort");

		if (channels.getOffer() == null) {
			Offer offer = new Offer();
			offer.fundsAllocationFee = new BigDecimal("0.1");
			offer.fixedCost = Btc.fromSat(100);
			offer.feeBase = new Btc("1");
			offer.feePerSatoshi = new Btc("0:001");
			channels.setOffer(offer);
		}
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { GetOfferCommand.class, SetOfferCommand.class, GetPeerOfferCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public synchronized void commandReceived(String fromServiceName, Command<?, ?> command) {
		if (command instanceof GetOfferCommand) {
			GetOfferCommand cmd = (GetOfferCommand) command;

			if (channels.getOffer() == null) {
				cmd.error = new ErrorResponse(new StoredException("Offer not specified", null));
				return;
			}

			cmd.response = new GetOfferResponse(myUri, channels.getOffer());
		} else if (command instanceof SetOfferCommand) {
			SetOfferCommand cmd = (SetOfferCommand) command;
			channels.setOffer(cmd.getRequest().offer);
			cmd.response = new NoDataResponse();
		} else if (command instanceof GetPeerOfferCommand) {
			GetPeerOfferCommand cmd = (GetPeerOfferCommand) command;
			Offer offer = getPeerOffer(cmd.getRequest().uri);
			cmd.response = new GetPeerOfferResponse(offer);
		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}

	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

	private static String getMyIp() {
		String ip;
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface iface = interfaces.nextElement();
				// filters out 127.0.0.1 and inactive interfaces
				if (iface.isLoopback() || !iface.isUp())
					continue;

				Enumeration<InetAddress> addresses = iface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();
					ip = addr.getHostAddress();
					if (ip.toString().matches(
							"^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")) {
						System.out.println(ip);
						return ip.toString();
					}

					// System.out.println(iface.getDisplayName() + " " + ip);
				}
			}
		} catch (SocketException e) {
			throw new StoredException("Cannot get IP", e);
		}
		return null;
	}

	public static Offer getPeerOffer(String uri) {
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

	public static String[] parseUri(String uri) {
		String[] ret = uri.split(":");
		if (ret.length != 3 || !"zone".equals(ret[0])) {
			throw new StoredException("Given uri not correct. Should be 'zone:<ip>:<port>' ", null);
		}

		return ret;
	}

}
