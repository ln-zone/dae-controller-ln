package bittech.dae.controller.helpers;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.ln.channels.OpenChannelCommand;
import bittech.lib.commands.ln.channels.RegisterChannelsListenerCommand;
import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.ln.invoices.DecodeInvoiceCommand;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.ln.invoices.RegisterPaymentsListenerCommand;
import bittech.lib.commands.ln.peers.ConnectPeerCommand;
import bittech.lib.commands.ln.peers.ListPeersCommand;
import bittech.lib.commands.ln.peers.ListPeersWithChannelsCommand;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Listener;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class Tester implements Listener {

	static final int timeoutSec = 20;

	private class CatchedCommand {
		Command<?, ?> command;
	}

	private BlockingQueue<CatchedCommand> receivedCommands = new LinkedBlockingQueue<CatchedCommand>();
	private Map<Command<?, ?>, CatchedCommand> underExecution = new ConcurrentHashMap<Command<?, ?>, CatchedCommand>();

	@SuppressWarnings("unchecked")
	public <T extends Command<?, ?>> T waitFor(Class<T> classOfT) {

		CatchedCommand cm;
		try {
			cm = receivedCommands.poll(timeoutSec, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new StoredException("Poll interrupted.", e);
		}
		if (cm == null) {
			throw new StoredException("Waiting for '" + classOfT + "' timeout. No command received in next 10 seconds",
					null);
		}
		if (cm.command.getClass().equals(classOfT)) {
			underExecution.put(cm.command, cm);
			return (T) cm.command;
		} else {
			throw new StoredException("Incorrect command received. Expected is '" + classOfT + "' but we got '"
					+ cm.command.getClass() + "'", null);
		}

	}

	public void submit(Command<?, ?> command) {
		CatchedCommand cm = underExecution.get(command);
		Require.notNull(cm, "catchedCommand");
		synchronized (cm) {
			System.out.println("Unlocked: " + command.type);
			cm.notify();
		}
	}

	public void submitErrorToAll(ErrorResponse errorResponse) {
		for (CatchedCommand cm : underExecution.values()) {
			synchronized (cm) {
				cm.command.error = errorResponse;
				cm.notify();
			}
		}
	}

	@Override
	public Class<?>[] getListeningCommands() {
		// TODO Auto-generated method stub
		return new Class<?>[] { RegisterChannelsListenerCommand.class, ListPeersCommand.class, OpenChannelCommand.class,
				GetInfoCommand.class, RegisterPaymentsListenerCommand.class, AddInvoiceCommand.class,
				DecodeInvoiceCommand.class, PayInvoiceCommand.class, ListPeersWithChannelsCommand.class,
				ConnectPeerCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		try {
			System.out.println("Received: " + command.type);

			CatchedCommand cm = new CatchedCommand();
			cm.command = command;

			synchronized (cm) {
				receivedCommands.put(cm);
				System.out.println("Locked: " + command.type);
				cm.wait();
			}
		} catch (InterruptedException e) {
			// TODO Catch this somehow
			e.printStackTrace();
		}

	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}
}
