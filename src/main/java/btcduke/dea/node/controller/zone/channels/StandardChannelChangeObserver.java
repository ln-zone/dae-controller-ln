package btcduke.dea.node.controller.zone.channels;

import bittech.lib.commands.ln.channels.ChannelChangedRequest;

public interface StandardChannelChangeObserver {

	public void onStandardChannelChanged(ChannelChangedRequest request);
}
