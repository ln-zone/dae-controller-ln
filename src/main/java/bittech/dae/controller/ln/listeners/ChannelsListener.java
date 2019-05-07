package bittech.dae.controller.ln.listeners;

import bittech.dae.controller.ln.lnd.LndCommandsExecutor;
import bittech.lib.commands.ln.channels.ChannelChangedRequest;
import bittech.lib.commands.ln.channels.CloseChannelCommand;
import bittech.lib.commands.ln.channels.DescribeGraphCommand;
import bittech.lib.commands.ln.channels.FindRouteCommand;
import bittech.lib.commands.ln.channels.ListChannelsCommand;
import bittech.lib.commands.ln.channels.ListChannelsResponse;
import bittech.lib.commands.ln.channels.ListChannelsResponse.ActiveChannel;
import bittech.lib.commands.ln.channels.ListPendingChannelsCommand;
import bittech.lib.commands.ln.channels.OpenChannelCommand;
import bittech.lib.commands.ln.channels.PayToRouteCommand;
import bittech.lib.commands.ln.channels.RegisterChannelsListenerCommand;
import bittech.lib.manager.ManagerDataProvider;
import bittech.lib.manager.commands.GetNodeDetailsResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.Node;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.protocol.helpers.CommandBroadcaster;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Notificator;
import bittech.lib.utils.Require;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lnrpc.LightningGrpc;
import lnrpc.Rpc;
import lnrpc.Rpc.GraphTopologyUpdate;

public class ChannelsListener implements Listener, ManagerDataProvider, AutoCloseable, ChannelChangedEvent {

	private final Notificator<ChannelChangedEvent> changeNotifier = new Notificator<ChannelChangedEvent>();
	private final CommandBroadcaster channelChangedBroadcaster;

	private final ManagedChannel channel;
	private final LndCommandsExecutor executor;

	private ListChannelsResponse lastResponse = null;

	public ChannelsListener(Node node, ManagedChannel channel, LndCommandsExecutor executor) {
		this.channel = Require.notNull(channel, "channel");
		this.executor = Require.notNull(executor, "executor");
		this.channelChangedBroadcaster = new CommandBroadcaster(node, "channelChangedBroadcaster.json");
		changeNotifier.register(this);
		subcribeChannels();
	}

	public void start() {
		grabChannelsData();
	}

	private void subcribeChannels() {

		LightningGrpc.LightningStub blockingStub = LightningGrpc.newStub(channel);

		Rpc.GraphTopologySubscription request = Rpc.GraphTopologySubscription.newBuilder().build();
		blockingStub.subscribeChannelGraph(request, new StreamObserver<GraphTopologyUpdate>() {

			@Override
			public void onNext(GraphTopologyUpdate value) {
				try {
					grabChannelsData();
				} catch (Exception ex) {
					new StoredException("Grab channels data failed", ex);
				}
			}

			@Override
			public void onError(Throwable t) {
				new StoredException("Sunbscibe transaction thrown error", t);
			}

			@Override
			public void onCompleted() {
				// Everything is OK
			}

		});

	}

	public void registerObserver(ChannelChangedEvent observer) {
		changeNotifier.register(observer);
	}

	public synchronized void grabChannelsData() {
		ListChannelsCommand cmd = new ListChannelsCommand();
		executor.execute(cmd);
		if ((lastResponse == null) || Utils.deepEquals(lastResponse, cmd.getResponse()) == false) {
			lastResponse = cmd.getResponse();
			Log.build().event("Some channels changed");
			changeNotifier.notifyThem((m) -> m.onChange(null)); // TODO: Build channel change
		}
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { OpenChannelCommand.class, CloseChannelCommand.class, ListChannelsCommand.class,
				ListPendingChannelsCommand.class, RegisterChannelsListenerCommand.class, FindRouteCommand.class,
				DescribeGraphCommand.class, PayToRouteCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof RegisterChannelsListenerCommand) {
			RegisterChannelsListenerCommand cmd = (RegisterChannelsListenerCommand) command;
			channelChangedBroadcaster.addService(fromServiceName);
			cmd.response = new NoDataResponse();
		} else {
			executor.execute(command);
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// nothing to do
	}

	@Override
	public void close() {

	}

	@Override
	public void addCustomData(GetNodeDetailsResponse details) {
		if (lastResponse != null) {
			details.summary.put("channels", "" + lastResponse.channels.size());

			Btc inChannels = new Btc("0");
			Btc toSpend = new Btc("0");
			Btc toReceive = new Btc("0");
			for (ActiveChannel channel : lastResponse.channels) {
				inChannels = inChannels.add(channel.capacity);
				toSpend = toSpend.add(channel.local_balance);
				toReceive = toReceive.add(channel.remote_balance);
			}
			details.summary.put("in_channels__", inChannels.toString());
			details.summary.put("to_spend_____", toSpend.toString());
			details.summary.put("to_receive___", toReceive.toString());

			details.details.put("channels", lastResponse.channels);
		} else {
			details.summary.put("channels", "pending...");
			details.summary.put("in_channels__", "pending...");
			details.summary.put("to_spend_____", "pending...");
			details.summary.put("to_receive___", "pending...");

			details.details.put("channels", "pending...");
		}
	}

	@Override
	public void onChange(ChannelChangedRequest channelChanged) {
//		ChannelChangedCommand cmd = new ChannelChangedCommand(channelChanged);
//		channelChangedBroadcaster.broadcast(cmd); //TODO: Broadcast
	}

}
