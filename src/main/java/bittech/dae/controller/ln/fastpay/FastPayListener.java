package bittech.dae.controller.ln.fastpay;

import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import bittech.dae.controller.ln.lnd.LndCommandsExecutor;
import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.ln.channels.DescribeGraphCommand;
import bittech.lib.commands.ln.channels.FindFastRouteCommand;
import bittech.lib.commands.ln.channels.FindRouteResponse;
import bittech.lib.commands.ln.channels.Hop;
import bittech.lib.commands.ln.channels.PayToRouteCommand;
import bittech.lib.commands.ln.invoices.DecodeInvoiceCommand;
import bittech.lib.commands.ln.invoices.FastPayCommand;
import bittech.lib.commands.ln.invoices.PayInvoiceResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Listener;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class FastPayListener implements Listener {

	private final LndCommandsExecutor executor;
	private final GraphManager graphManager;
	private final String myNodeId;

	public FastPayListener(final LndCommandsExecutor executor) {
		try {
			this.executor = Require.notNull(executor, "executor");
			graphManager = new GraphManager();

			{
				DescribeGraphCommand cmd = new DescribeGraphCommand();
				executor.execute(cmd);
				if (cmd.getError() != null) {
					throw new StoredException("Cannot execute DescribeGraphCommand", cmd.getError().toException());
				}
				graphManager.update(cmd.getResponse());
				Log.build().param("nodes", graphManager.nodes.size()).event("Graphs loaded to manager");
			}

			{
				GetInfoCommand cmd = new GetInfoCommand();
				executor.execute(cmd);
				if (cmd.getError() != null) {
					throw new StoredException("Cannot execute GetInfoCommand", cmd.getError().toException());
				}
				myNodeId = cmd.getResponse().identity_pubkey;
			}
		} catch (Exception ex) {
			throw new StoredException("Cannot initialize FastPayListener", ex);
		}

	}

	public void pay(final String invoice, final Btc amount, final boolean feeIncluded) {
		DecodeInvoiceCommand decodeInvoieCmd = new DecodeInvoiceCommand(invoice);
		executor.execute(decodeInvoieCmd);
		if (decodeInvoieCmd.getError() != null) {
			throw new StoredException("DecodeInvoiceCommand failed", decodeInvoieCmd.getError().toException());
		}
		if (Btc.HasValue(decodeInvoieCmd.getResponse().amount)) {
			if (Btc.HasValue(amount)) {
				throw new StoredException("Invoice contains amount. You can't provide amount in request then", null);
			}
			if (feeIncluded == true) {
				throw new StoredException(
						"Invoice contains amount. FeeIncluded can be true only for no amount invoices", null);
			}
		} else {
			if (Btc.HasValue(amount) == false) {
				throw new StoredException("Invoice contains no amount. You have to provide amount in request then",
						null);
			}
		}

		Btc amountToPay = Btc.HasValue(amount) ? amount : decodeInvoieCmd.getResponse().amount;
		
		Set<String> excludedChannels = new HashSet<String>();
		
		for(int i = 0; i<10; i++) {
		
			bittech.lib.commands.ln.channels.Route route = graphManager.findRoute(myNodeId,
					decodeInvoieCmd.getResponse().destination, amountToPay, excludedChannels);
	
			addExpiryToRoute(route);
	
			{
				PayToRouteCommand cmd = new PayToRouteCommand(decodeInvoieCmd.getResponse().payment_hash, route);
				executor.execute(cmd);

				if (cmd.getError() != null) {
					// unable to route payment to destination: TemporaryChannelFailure: Link 573916:1745:0 has insufficient capacity: need 101000 mSAT, has 0 mSAT
					ErrorResponse insufRes = cmd.getError().findWithMessage("has insufficient capacity");
					if(insufRes != null) {
						String channelId = StringUtils.substringBetween(insufRes.message, "Link ", " has");
						excludedChannels.add(channelId);
						continue;
					}
					Log.build().param("route", cmd.getRequest().route).event("Exception thrown for route");
					throw new StoredException("PayToRouteCommand failed", cmd.getError().toException());
				}
				
				
				return;
			}
		}
		throw new StoredException("Fast pay failed", new Exception("Couldn't find rout after 10 tries"));
	}

	private void addExpiryToRoute(bittech.lib.commands.ln.channels.Route route) {

		GetInfoCommand getInfoCmd = new GetInfoCommand();
		executor.execute(getInfoCmd);
		if (getInfoCmd.getError() != null) {
			throw new StoredException("GetInfoCommand failed", getInfoCmd.getError().toException());
		}

//		int index = route.hops.size() - 1;
		int currentLock = getInfoCmd.getResponse().block_height; // TODO: Why 10?
//		route.totalTimeLock = blockHight + htlcDiff * (index + 1);
		
		ListIterator<Hop> listIterator = route.hops.listIterator(route.hops.size());
		
		int i = 0;
		while (listIterator.hasPrevious()) {
			Hop hop = listIterator.previous();
			if(i==0) {
				currentLock += hop.timeLockDelta;
				hop.expiry = currentLock;
			} else {
				hop.expiry = currentLock;
				currentLock += hop.timeLockDelta;
			}
			i++;
		}
		
		route.totalTimeLock = currentLock;

	}

	@Override
	public Class<?>[] getListeningCommands() {
		// TODO Auto-generated method stub
		return new Class<?>[] { FindFastRouteCommand.class, FastPayCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof FindFastRouteCommand) {
			FindFastRouteCommand cmd = (FindFastRouteCommand) command;
			bittech.lib.commands.ln.channels.Route route = graphManager.findRoute(cmd.request.sourceId,
					cmd.request.destId, cmd.request.amount, new HashSet<String>());
			if (route == null) {
				throw new StoredException("No route found", null);
			}
			addExpiryToRoute(route);
			cmd.response = new FindRouteResponse();
			cmd.response.route = route;
		} else if (command instanceof FastPayCommand) {
			FastPayCommand cmd = (FastPayCommand) command;
			pay(cmd.getRequest().invoice, cmd.getRequest().amount, cmd.getRequest().feeIncluded);
			cmd.response = new PayInvoiceResponse();
		} else {
			throw new StoredException("Command not supported: " + command.type + " by FastPayListener", null);
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// TODO Auto-generated method stub

	}

}
