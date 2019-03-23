package bittech.dae.controller.ln.listeners;

import bittech.lib.commands.ln.channels.ChannelChangedRequest;

public interface ChannelChangedEvent {

	public void onChange(ChannelChangedRequest channelChanged);
}
