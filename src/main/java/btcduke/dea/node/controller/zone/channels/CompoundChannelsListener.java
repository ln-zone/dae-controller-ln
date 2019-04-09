package btcduke.dea.node.controller.zone.channels;

import java.util.HashSet;
import java.util.Set;

import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.ln.peers.ListPeersCommand;
import bittech.lib.commands.lnzone.CompoundChannel;
import bittech.lib.commands.lnzone.commans.ListChannelsCommand;
import bittech.lib.commands.lnzone.commans.ListChannelsResponse;
import bittech.lib.commands.lnzone.internal.ChannelChangedCommand;
import bittech.lib.commands.lnzone.internal.RegisterChannelChangedCommand;
import bittech.lib.commands.lnzone.internal.ChannelChangedRequest.ChangeType;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.Node;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class CompoundChannelsListener implements Listener, CompoundChannelChangeObserver {

	private final Connection controllerConnection;
	private final ZoneChannels zoneChannels;
	private final CompoundChannels compoundChannels;
	private final Node myNode;
	private final Set<String> registeredToNotification;

//	private CalcZoneFeeThread calcZoneFeeThread;

	public CompoundChannelsListener(final ZoneChannels zoneChannels, CompoundChannels allChannels, final Node myNode,
			final Connection controllerConnection) {
		this.zoneChannels = Require.notNull(zoneChannels, "zoneChannels");
		this.compoundChannels = Require.notNull(allChannels, "allChannels");
		this.myNode = Require.notNull(myNode, "myNode");
		this.controllerConnection = Require.notNull(controllerConnection, "controllerConnection");
		this.registeredToNotification = new HashSet<String>();
		this.mergeToAllChannels();

		compoundChannels.registerCompoundChannelChangeObserver(this);

//		calcZoneFeeThread = new CalcZoneFeeThread();
//		calcZoneFeeThread.start();
	}

	private synchronized void mergeToAllChannels() {
		ListPeersCommand listPeersCmd = new ListPeersCommand();

		controllerConnection.execute(listPeersCmd);

		if (listPeersCmd.getError() != null) {
			throw new StoredException("Cannot get standard channels from controller", listPeersCmd.getError().toException());
		}

//		compoundChannels.mergeChannelsData(listPeersCmd.getResponse().peers, zoneChannels.copyEstablishedChannels());
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { ListChannelsCommand.class, RegisterChannelChangedCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof ListChannelsCommand) {
			try {
				ListChannelsCommand cmd = (ListChannelsCommand) command;
				cmd.response = new ListChannelsResponse(compoundChannels.getChannels());

			} catch (Exception ex) {
				throw new StoredException("Cannot list channels", ex);
			}

		} else if (command instanceof RegisterChannelChangedCommand) {
			try {
				RegisterChannelChangedCommand cmd = (RegisterChannelChangedCommand) command;
				registeredToNotification.add(fromServiceName);
				cmd.response = new NoDataResponse();

			} catch (Exception ex) {
				throw new StoredException("Cannot list channels", ex);
			}
		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}
	}

	private int getCurrentBlockumber() {
		GetInfoCommand cmd = new GetInfoCommand();
		controllerConnection.execute(cmd);
		if (cmd.getError() != null) {
			throw new StoredException("Cannot get node info", cmd.getError().toException());
		}
		return cmd.getResponse().block_height;
	}

	private synchronized void calcFees(int blockNumber) {
		for (CompoundChannel channel : compoundChannels.getChannels()) {
			if ("normal".equals(channel.status)) {
				if (channel.zoneChannelData != null) {

					// TODO: May not be needed in the future
					if (channel.zoneChannelData.lastBlockNumber == 0) {
						channel.zoneChannelData.lastBlockNumber = blockNumber;
					}

					int blocksDelta = blockNumber - channel.zoneChannelData.lastBlockNumber;
					if (blocksDelta > 0) {
						Btc feeToSub = channel.zoneChannelData.costPerBlock.multi(blocksDelta);
						channel.zoneChannelData.feeReserve = channel.zoneChannelData.feeReserve.sub(feeToSub);

						channel.zoneChannelData.lastBlockNumber = blockNumber;
						channel.zoneChannelData.myAmount = new Btc(channel.myAmount.toString());
						channel.zoneChannelData.peerAmount = new Btc(channel.peerAmount.toString());

						channel.zoneChannelData.calcFields();

						if (channel.zoneChannelData.feeReserve.toMsat() < 0) {
							// TODO: Close channel
						}

						onCompoundChannelChanged(ChangeType.MODIFIED, channel); // TODO: Change

					}
				}
			}
		}
	}

	private final class CalcZoneFeeThread extends Thread {
		@Override
		public void run() {
			for (;;) {
				try {
					// Log log = new Log();
					// log.event("Getting block number");
					int blockNum = getCurrentBlockumber();
					// log.param("blockNum", blockNum);
					// log.event("Block number got. Calulating fees");
					calcFees(blockNum);
					// log.event("Fees calulated");

					Thread.sleep(10000);
				} catch (InterruptedException e) {
					new StoredException("Error in CalcZoneFeeThread", e);
				}
			}
		}
	}

	/**
	 * Inform others that zone channel was changed
	 * 
	 * @param changeType
	 * @param changedChannel
	 */

	@Override
	public void onCompoundChannelChanged(ChangeType changeType, CompoundChannel changedChannel) {
		(new Log()).param("changedChannel", changedChannel).param("services", registeredToNotification.size())
				.event("updateServiceWithChangedChannel");
		for (String connectionName : registeredToNotification) {
			try {
				(new Log()).event("Updateing service " + connectionName);
				ChannelChangedCommand cmd = new ChannelChangedCommand(changeType, changedChannel);
				myNode.execute(connectionName, cmd);
				(new Log()).event("Service updated " + connectionName);
				if (cmd.getError() != null) {
					new StoredException("Peer failed receiving channelChanged command from zone module",
							cmd.getError().toException());
				}
			} catch (Exception ex) {
				new StoredException("Cannot send ChannelChangedCommand to service " + connectionName, ex);
			}
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
