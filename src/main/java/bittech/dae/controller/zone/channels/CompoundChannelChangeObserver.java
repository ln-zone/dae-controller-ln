package bittech.dae.controller.zone.channels;

import bittech.lib.commands.lnzone.CompoundChannel;
import bittech.lib.commands.lnzone.internal.ChannelChangedRequest.ChangeType;

public interface CompoundChannelChangeObserver {

	public void onCompoundChannelChanged(ChangeType changeType, CompoundChannel changedChannel);
}
