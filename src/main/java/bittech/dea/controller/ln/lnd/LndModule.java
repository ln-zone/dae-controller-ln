package bittech.dea.controller.ln.lnd;

import bittech.dea.controller.ln.general.MixListener;
import bittech.dea.controller.ln.listeners.ChannelsListener;
import bittech.dea.controller.ln.listeners.InfoListener;
import bittech.dea.controller.ln.listeners.InvoicesListener;
import bittech.dea.controller.ln.listeners.OnChainListener;
import bittech.dea.controller.ln.listeners.PeersListener;
import bittech.lib.manager.ManagerModule;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Config;

public class LndModule implements AutoCloseable {
	
	LndModuleConfig config;
	
	private final Node node;
	private final LndRpc lndRpc;
	private final LndCommandsExecutor executor;
	
	private final  MixListener mixListener;
	private final InfoListener infoListener;
	private final OnChainListener onChainListener;
	private final PeersListener peersListener;
	private final ChannelsListener channelsListener;
	private final InvoicesListener invoicesListener;
	
	public LndModule(String configEntryName) {
		
		config = Config.getInstance().getEntry(configEntryName, LndModuleConfig.class);
		
		node = new Node(config.moduleName, config.listeningPort);
		lndRpc = new LndRpc(config.rpcHost, config.rpcPort, config.cert);
		executor = new LndCommandsExecutor(lndRpc.getChannel());

		mixListener = new MixListener();
		infoListener = new InfoListener(executor);
		onChainListener = new OnChainListener(lndRpc.getChannel(), executor);
		peersListener = new PeersListener(executor);
		channelsListener = new ChannelsListener(lndRpc.getChannel(), executor);
		invoicesListener = new InvoicesListener(node, lndRpc.getChannel());
		
		
		ManagerModule managerNotifier = new ManagerModule(node, config.moduleName);
		managerNotifier.addDetailsProvider(infoListener);
		managerNotifier.addDetailsProvider(onChainListener);
		managerNotifier.addDetailsProvider(channelsListener);
		
		node.registerListener(mixListener);
		node.registerListener(infoListener);
		node.registerListener(onChainListener);
		node.registerListener(peersListener);
		node.registerListener(channelsListener);
		node.registerListener(invoicesListener);
		
		onChainListener.start();
		channelsListener.start();
		managerNotifier.start();
	}

	@Override
	public void close() {
		lndRpc.close();
	}
	
	public void execute(Command<?, ?> command) {
		executor.execute(command);
	}


	
}
