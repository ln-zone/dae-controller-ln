package bittech.dae.controller.zone.channels;

import bittech.lib.commands.lnzone.EstablishedChannel;

public interface ZoneChannelChangeObserver {

	public void onZoneChannelChanged(EstablishedChannel zoneEstablishedChannel);
}
