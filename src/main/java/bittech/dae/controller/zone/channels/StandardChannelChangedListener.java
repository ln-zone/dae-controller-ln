package bittech.dae.controller.zone.channels;

import bittech.lib.commands.ln.channels.ChannelChangedCommand;
import bittech.lib.commands.ln.channels.RegisterChannelsListenerCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Notificator;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class StandardChannelChangedListener implements Listener, AutoCloseable {

	private final Notificator<StandardChannelChangeObserver> channelChanged = new Notificator<StandardChannelChangeObserver>();

	public StandardChannelChangedListener(final Connection controllerConnection) {

		Require.notNull(controllerConnection, "controllerConnection");

		RegisterChannelsListenerCommand cmd = new RegisterChannelsListenerCommand();
		controllerConnection.execute(cmd);

		if (cmd.getError() != null) {
			throw new StoredException("Register command failed", cmd.getError().toException());
		}

		controllerConnection.getNode().registerListener(this);
	}

	public void registerStandardChannelChangeObserver(StandardChannelChangeObserver observer) {
		channelChanged.register(observer);
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { ChannelChangedCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof ChannelChangedCommand) {

			ChannelChangedCommand cmd = (ChannelChangedCommand) command;
			try {
				(new Log()).param("change", cmd.getRequest()).event("ChannelChangedCommand received here");
				channelChanged.notifyThem((m) -> m.onStandardChannelChanged(cmd.getRequest()));
			} catch (Exception ex) {
				throw new StoredException("Cannot notify listeners about channel changed", ex);
			}
			cmd.response = new NoDataResponse();

		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}
	}

	@Override
	public void close() throws Exception {
		channelChanged.close();
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
