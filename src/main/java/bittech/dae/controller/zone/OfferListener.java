package bittech.dae.controller.zone;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import bittech.dae.controller.zone.channels.ZoneChannels;
import bittech.lib.commands.lnzone.commans.GetOfferCommand;
import bittech.lib.commands.lnzone.commans.GetOfferResponse;
import bittech.lib.commands.lnzone.commans.Offer;
import bittech.lib.commands.lnzone.commans.SetOfferCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class OfferListener implements Listener {

	private final String myUri;

	final ZoneChannels channels;
	private final Connection controllerConnection;

	public String getMyUri() {
		return myUri;
	}

	public OfferListener(ZoneChannels channels, final Connection controllerConnection, final int listeningPort) {
		
		this.channels = Require.notNull(channels, "channels");
		this.controllerConnection = Require.notNull(controllerConnection, "controllerConnection");
		this.myUri = "zone:" + getMyIp() + ":" + Require.inRange(listeningPort, 1, Short.MAX_VALUE, "listeningPort");
		
		if (channels.getOffer() == null) {
			Offer offer = new Offer();
			offer.fundsAllocationFee = new BigDecimal("0.1");
			offer.fixedCost = Btc.fromSat(100);
			offer.feeBase = new Btc("1");
			offer.feePerSatoshi = new Btc("1");
//			updateOfferWithConfigData(offer);
		} else {
//			updateOfferWithConfigData(channels.getOffer());
		}
	}

	@Override
	public Class<?>[] getListeningCommands() {
		// TODO Auto-generated method stub
		return new Class<?>[] { GetOfferCommand.class, SetOfferCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		// TODO Auto-generated method stub
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
			updateOffer(cmd.getRequest().offer);
			cmd.response = new NoDataResponse();
		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}

	}

//	private void updateOfferWithConfigData(Offer offer) {
//		ListConfigsCommand configsCmd = new ListConfigsCommand();
//		controllerConnection.execute(configsCmd);
//		if (configsCmd.getError() != null) {
//			throw new StoredException("Cannot get configs from controllerConnection",
//					configsCmd.getError().toException());
//		}
//		if (configsCmd.getResponse().feeBase == 0) {
//			throw new StoredException("No feeBase returned from clightning configs or feeBase is zero", null);
//		}
//		offer.feeBase = Btc.fromMsat(configsCmd.getResponse().feeBase);
//
//		if (configsCmd.getResponse().feePerSatoshi == 0) {
//			throw new StoredException("No feePerSatoshi returned from clightning configs or feePerSatoshi is zero",
//					null);
//		}
//		offer.feePerSatoshi = Btc.fromMsat(configsCmd.getResponse().feePerSatoshi);
//
//		channels.setOffer(offer);
//	}

	private synchronized void updateOffer(Offer offer) {
		try {
			Offer existionOffer = channels.getOffer();
			if (existionOffer != null) {
				if (offer.feeBase != null && offer.feeBase.hasValue()) {
					if (offer.feeBase.toMsat() != existionOffer.feeBase.toMsat()) {
						throw new StoredException(
								"Fee base cannot be changed. This is setup in clightning configuration", null);
					}
				} else {
					offer.feeBase = existionOffer.feeBase;
				}

				if (offer.feePerSatoshi != null && offer.feePerSatoshi.hasValue()) {
					if (offer.feePerSatoshi.toMsat() != existionOffer.feePerSatoshi.toMsat()) {
						throw new StoredException(
								"Fee per satoshi cannot be changed. This is setup in clightning configuration", null);
					}
				} else {
					offer.feePerSatoshi = existionOffer.feePerSatoshi;
				}
			}
			channels.setOffer(offer);
		} catch (Exception ex) {
			throw new StoredException("Cannot set new offer", ex);
		}
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

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
