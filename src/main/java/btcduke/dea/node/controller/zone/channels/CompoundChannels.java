package btcduke.dea.node.controller.zone.channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import bittech.lib.commands.ln.channels.ChannelChangedRequest;
import bittech.lib.commands.lnzone.CompoundChannel;
import bittech.lib.commands.lnzone.EstablishedChannel;
import bittech.lib.commands.lnzone.CompoundChannel.Type;
import bittech.lib.commands.lnzone.internal.ChannelChangedRequest.ChangeType;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Notificator;
import bittech.lib.utils.Require;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class CompoundChannels implements StandardChannelChangeObserver, ZoneChannelChangeObserver {

	private Map<String, CompoundChannel> channels = new HashMap<String, CompoundChannel>(); // fundingTxID, channel

	private Notificator<CompoundChannelChangeObserver> changeNotifier = new Notificator<CompoundChannelChangeObserver>();

	public void registerCompoundChannelChangeObserver(CompoundChannelChangeObserver observer) {
		changeNotifier.register(observer);
	}

	public void unregisterCompoundChannelChangeObserver(CompoundChannelChangeObserver observer) {
		changeNotifier.unregister(observer);
	}

	private static String generateName(String peerAlias, String peerId) {
		Require.notNull(peerId, "peerId");
		if (peerId.length() != 66) {
			throw new StoredException("Peer ID should have exackly 66 length but it has " + peerId.length(), null);
		}
		return peerAlias != null ? peerAlias
				: peerId.substring(0, 14) + "..." + peerId.substring(peerId.length() - 14, peerId.length());
	}

//	public synchronized Map<String, CompoundChannel> mergeChannelsData(List<Peer> peers,
//			List<EstablishedChannel> zoneChannels) {
//
//		Require.notNull(peers, "peers");
//		Require.notNull(zoneChannels, "zoneChannels");
//
//		Map<String, EstablishedChannel> zoneChannelsMap = new HashMap<String, EstablishedChannel>();
//		for (EstablishedChannel zcd : zoneChannels) {
//			if (zcd.fundingTxId != null && zcd.fundingTxId.length() > 0) {
//				zoneChannelsMap.put(zcd.fundingTxId, zcd);
//			}
//		}
//
//		this.channels = new LinkedHashMap<String, CompoundChannel>();
//		for (Peer peer : peers) {
//			if (peer.channels == null) {
//				continue;
//			}
//			for (btcduke.lib.ln.clightning.p2p.commands.ListPeersResponse.Channel basicChannel : peer.channels) {
//				CompoundChannel newChannel = new CompoundChannel();
//				newChannel.status = convertStatus(basicChannel.state);
//				newChannel.myAmount = Btc.fromMsat(basicChannel.msatoshi_to_us);
//				newChannel.peerAmount = Btc.fromMsat(basicChannel.msatoshi_total - basicChannel.msatoshi_to_us);
//				newChannel.capacity = Btc.fromMsat(basicChannel.msatoshi_total);
//				newChannel.peerId = peer.id;
//				newChannel.peerAlias = peer.alias;
//				newChannel.fundingTxId = basicChannel.funding_txid;
//				newChannel.shortChannelId = basicChannel.short_channel_id;
//				newChannel.zoneChannelData = zoneChannelsMap.get(basicChannel.funding_txid);
//
//				newChannel.name = generateName(newChannel.peerAlias, newChannel.peerId);
//
//				if (newChannel.zoneChannelData != null) {
//					newChannel.type = CompoundChannel.Type.ZONE;
//					newChannel.zoneChannelData.calcFields();
//				} else {
//					newChannel.type = CompoundChannel.Type.BASIC;
//				}
//
//				channels.put(newChannel.fundingTxId, newChannel);
//			}
//		}
//		return channels;
//	}

	private static String convertStatus(String state) {
		Require.notEmpty(state, "state");

		switch (state) {
		case "CHANNELD_AWAITING_LOCKIN":
			return "opening";
		case "CHANNELD_NORMAL":
			return "normal";
		default:
			return state.toLowerCase();
		}

	}

	private static void applyChange(CompoundChannel channel, ChannelChangedRequest change) {
		channel.peerAlias = change.peerAlias;
		channel.name = generateName(change.peerAlias, change.peerId);
		channel.status = convertStatus(change.state);
		channel.fundingTxId = Require.notNull(change.fundingTxId, "fundingTxId");
		channel.peerId = Require.notNull(change.peerId, "peerId");
		channel.shortChannelId = change.shortChannelId;

		channel.capacity = Require.notNull(change.capacity, "capacity");
		channel.myAmount = Require.notNull(change.myAmount, "myAmount");
		channel.peerAmount = Require.notNull(change.peerAmount, "peerAmount");
	}

	// public synchronized Collection<Channel> getAsList() {
	// return channels.values();
	// }

	private void newChannel(CompoundChannel channel) {
		channels.put(channel.fundingTxId, channel);
	}

	public synchronized List<CompoundChannel> getChannels() { // TODO: Multithreading issue?
		ArrayList<CompoundChannel> chList = new ArrayList<CompoundChannel>(channels.values().size());

		for (CompoundChannel ch : channels.values()) {
			chList.add(Utils.deepCopy(ch, CompoundChannel.class));
		}

		return chList;
	}

	// private synchronized void updateZonePart(EstablishedChannel
	// zoneEstablishedChannel) {
	//
	// }

	private synchronized ChangeType putChannel(CompoundChannel channel) {
		int len = channels.size();
		if (channel.fundingTxId != null) {
			channels.put(channel.fundingTxId, channel);
			if (channel.zoneChannelData != null) {
				channels.remove(channel.zoneChannelData.zoneChannelId);
			}
		} else if (channel.zoneChannelData != null && channel.zoneChannelData.zoneChannelId != null) {
			channels.put(channel.zoneChannelData.zoneChannelId, channel);
			channels.remove(channel.fundingTxId);
		} else {
			throw new StoredException("Channel do not contains data sufficient to put it to channels map", null);
		}
		// if(len == channels.size()) {
		// return ChangeType.MODIFIED;
		if (len < channels.size()) {
			return ChangeType.ADDED;
		} else {
			return ChangeType.MODIFIED;
			// throw new StoredException("Channel was removed from list, what should not be
			// possible here", null);
		}
	}

	public synchronized CompoundChannel findZoneChannelById(String zoneChannelId) {
		Require.notNull(zoneChannelId, "zoneChannelId");

		for (CompoundChannel channel : channels.values()) {
			if ((channel.zoneChannelData != null) && (zoneChannelId.equals(channel.zoneChannelData.zoneChannelId))) {
				return channel;
			}
		}
		return null;
	}

	@Override
	public synchronized void onZoneChannelChanged(EstablishedChannel zoneEstablishedChannel) {
		(new Log()).param("changed channel", zoneEstablishedChannel).event("onZoneChannelChanged");

		if (zoneEstablishedChannel.zoneChannelId == null) {
			throw new StoredException("Zone channel has null id", null);
		}

		CompoundChannel channelById = channels.get(zoneEstablishedChannel.zoneChannelId);
		CompoundChannel channelByFundingId = channels.get(zoneEstablishedChannel.fundingTxId);

		CompoundChannel channel;

		if (channelById == null && channelByFundingId == null) {
			channel = new CompoundChannel(); // No any data about this channel. Add this to list
			channel.status = "not_funded_yet";
			channel.type = CompoundChannel.Type.ZONE;
		} else if (channelById != null && channelByFundingId == null) {
			channel = channelById; // It exists only as zone part
		} else if (channelById == null && channelByFundingId != null) {
			channel = channelByFundingId; // It exists only as normal channel (was established)
		} else if (channelById != null && channelByFundingId != null) {
			channel = channelByFundingId; // It both channels are separately stored
		} else {
			throw new StoredException("Internal error. Additional combination exists!?!?", null);
		}

		channel.zoneChannelData = zoneEstablishedChannel;
		if (channel.zoneChannelData.fundingTxId != null) {
			if (channel.fundingTxId == null) {
				channel.fundingTxId = channel.zoneChannelData.fundingTxId;
			} else if (!channel.fundingTxId.equals(channel.zoneChannelData.fundingTxId)) {
				throw new StoredException(
						"Conflict! FundingTxId from zoneChannelData different than fundingTxId from channel: '"
								+ channel.zoneChannelData.fundingTxId + "' vs '" + channel.fundingTxId,
						null);
			}
		}

		ChangeType changeType = putChannel(channel);

		final CompoundChannel ch = Utils.deepCopy(channel, CompoundChannel.class);
		changeNotifier.notifyThem((e) -> e.onCompoundChannelChanged(changeType, ch));
	}

	private static CompoundChannel newChannel(ChannelChangedRequest change) {
		CompoundChannel channel = new CompoundChannel();
		channel.type = Type.BASIC;
		CompoundChannels.applyChange(channel, change);
		return channel;
	}

	@Override
	public synchronized void onStandardChannelChanged(ChannelChangedRequest change) {
		(new Log()).param("change", change).event("onStandardChannelChanged");

		Require.notNull(change, "change");
		Require.notEmpty(change.fundingTxId, "fundingTxId");
		CompoundChannel existingChannel = channels.get(change.fundingTxId);

		if (existingChannel == null) {
			CompoundChannel newChannel = newChannel(change);
			newChannel(newChannel);
			changeNotifier.notifyThem((e) -> e.onCompoundChannelChanged(ChangeType.ADDED, newChannel));
		} else {
			CompoundChannels.applyChange(existingChannel, change);
			changeNotifier.notifyThem((e) -> e.onCompoundChannelChanged(ChangeType.MODIFIED, existingChannel));
		}

	}

}
