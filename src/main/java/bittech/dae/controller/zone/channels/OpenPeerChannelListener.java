package bittech.dae.controller.zone.channels;

import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.lnzone.commans.OpenPeerChannelCommand;
import bittech.lib.commands.lnzone.external.OpenZoneChannelCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class OpenPeerChannelListener implements Listener {
	
	private final Connection controllerConnection;
	
	public OpenPeerChannelListener(Connection controllerConnection) {
		this.controllerConnection = Require.notNull(controllerConnection, "controllerConnection");
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { OpenPeerChannelCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	private Connection connectPeer(String zoneUri) {
		Node node = new Node("nodedede"); // TODO: Leaking
		String[] uri = zoneUri.split(":");
		if(uri.length != 2) {
			throw new StoredException("Incorrect uri format: " + zoneUri + ". Showul be ip:port", null);
		}
		return node.connect("ZonePeer", uri[0], Integer.parseInt(uri[1]));
	}
	
	
	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof OpenPeerChannelCommand) {
			OpenPeerChannelCommand cmd = (OpenPeerChannelCommand)command;
			Connection connection = connectPeer(cmd.getRequest().peerUri);
			
			GetInfoCommand gic = new GetInfoCommand();
			controllerConnection.execute(gic);
			
			if(gic.getError() != null) {
				throw new StoredException("Cannot get ln info", gic.getError().toException());
			}
			
			if(gic.getResponse().uris.size() == 0) {
				throw new StoredException("Ln do not contains uris. Have to contain at least one", null);
			}
			
			String myUri = gic.getResponse().uris.get(0);
			
			OpenZoneChannelCommand openZoneChanelCmd = new OpenZoneChannelCommand(cmd.getRequest().peerAmount, cmd.getRequest().feeReserve, cmd.getRequest().myAmount, cmd.getRequest().offer, myUri);
			connection.execute(openZoneChanelCmd);
			
		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// TODO Auto-generated method stub

	}

}
