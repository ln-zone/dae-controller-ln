package bittech.dea.controller.ln.listeners;

import bittech.dea.controller.ln.lnd.LndCommandsExecutor;
import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.manager.ManagerDataProvider;
import bittech.lib.manager.commands.GetNodeDetailsResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Listener;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class InfoListener implements Listener, ManagerDataProvider {

	private final LndCommandsExecutor executor;

	public InfoListener(final LndCommandsExecutor executor) {
		this.executor = Require.notNull(executor, "executor");
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] {GetInfoCommand.class};
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		executor.execute(command);
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

	@Override
	public void addCustomData(GetNodeDetailsResponse details) {
		GetInfoCommand cmd = new GetInfoCommand();
		executor.execute(cmd);
		// TODO: Error handle
		if(cmd.getResponse().uris.size() > 0) {
			details.summary.put("uri", cmd.getResponse().uris.get(0));
		} else {
			details.summary.put("uri", "no uri!");
		}
		
		details.summary.put("alias", "" + cmd.getResponse().alias);
		details.summary.put("peers", "" + cmd.getResponse().num_peers);
		
		details.summary.put("active channels", "" + cmd.getResponse().num_active_channels);
		details.summary.put("inactive channels", "" + cmd.getResponse().num_inactive_channels);
		details.summary.put("pending channels", "" + cmd.getResponse().num_pending_channels);

		details.details.put("info", cmd.getResponse());
		// TODO Auto-generated method stub
		
	}

}
