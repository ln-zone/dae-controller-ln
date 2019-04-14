package bittech.dae.controller.zone.channels;

import bittech.lib.commands.ln.channels.ChannelChangedRequest;

public interface StandardChannelChangeObserver {

	public void onStandardChannelChanged(ChannelChangedRequest request);
}
