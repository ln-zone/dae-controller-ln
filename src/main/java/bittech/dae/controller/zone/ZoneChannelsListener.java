package bittech.dae.controller.zone;

import bittech.dae.controller.zone.channels.ZoneChannels;
import bittech.lib.commands.lnzone.commans.ListZoneChannelsCommand;
import bittech.lib.commands.lnzone.commans.ListZoneChannelsResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Listener;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class ZoneChannelsListener implements Listener {

	private final ZoneChannels channels;

	public ZoneChannelsListener(ZoneChannels channels) {
		this.channels = Require.notNull(channels, "channels");
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { ListZoneChannelsCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof ListZoneChannelsCommand) {

			ListZoneChannelsCommand cmd = (ListZoneChannelsCommand) command;
			cmd.response = new ListZoneChannelsResponse(channels.copyEstablishedChannels());

		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
