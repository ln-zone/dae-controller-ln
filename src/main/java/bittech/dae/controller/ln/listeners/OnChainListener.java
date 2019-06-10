package bittech.dae.controller.ln.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import bittech.dae.controller.ln.lnd.LndCommandsExecutor;
import bittech.lib.commands.ln.onchain.FundsReceivedCommand;
import bittech.lib.commands.ln.onchain.ListChainTxnsCommand;
import bittech.lib.commands.ln.onchain.ListUnspentCommand;
import bittech.lib.commands.ln.onchain.ListUnspentResponse.Utxo;
import bittech.lib.commands.ln.onchain.NewAddressCommand;
import bittech.lib.commands.ln.onchain.RegisterFundsReceivedCommand;
import bittech.lib.commands.ln.onchain.SendOnChainCommand;
import bittech.lib.commands.ln.onchain.WalletBalanceCommand;
import bittech.lib.commands.ln.onchain.WalletBalanceResponse;
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
import lnrpc.Rpc.Transaction;

public class OnChainListener implements Listener, ManagerDataProvider, OnChainFundsReceivedEvent, AutoCloseable {

	private final Notificator<OnChainChangedEvent> onchainAmountsChangedNotifier = new Notificator<OnChainChangedEvent>();
	private final Notificator<OnChainFundsReceivedEvent> onchainTransactionReceivedNotifier = new Notificator<OnChainFundsReceivedEvent>();
	private final CommandBroadcaster onchainTransactionReceivedBroadcaster;

	private final ManagedChannel channel;
	private final LndCommandsExecutor executor;

	private WalletBalanceResponse lastResponse = null;

	AtomicBoolean isWorking = new AtomicBoolean(true);
	final ExecutorService exNewFunds = Executors.newSingleThreadExecutor();

//	private Map<String, Btc> addrAmounts = new HashMap<String, Btc>();

	public OnChainListener(Node node, ManagedChannel channel, LndCommandsExecutor executor) {
//		this.onchainAmountsChangedBroadcaster = new CommandBroadcaster(node, "onchainAmountsChangedBroadcaster.json");
		this.onchainTransactionReceivedBroadcaster = new CommandBroadcaster(node,
				"onchainTransactionReceivedBroadcaster.json");
		this.channel = Require.notNull(channel, "channel");
		this.executor = Require.notNull(executor, "executor");
		subcribeTransactions();
		onchainTransactionReceivedNotifier.register(this);
	}

	public void start() {
		grabWalletBalance();
		waitForNewFunds();
	}

	private void waitForNewFunds() {
		exNewFunds.submit(() -> {
			Map<String, Utxo> oldUtxo = new HashMap<String, Utxo>();
			Log.build().event("Start waitForNewFunds");
			while (isWorking.get()) {
				try {
					Thread.sleep(10000);
					
					ListUnspentCommand cmd = new ListUnspentCommand();
					executor.execute(cmd);
					if (cmd.getError() != null) {
						throw new StoredException("Executing ListUnspentCommand failed", cmd.getError().toException());
					}
					for (Utxo utxo : cmd.getResponse().list) {
						String id = utxo.txId + ":" + utxo.txIndex;
						Utxo oldU = oldUtxo.get(id);
						if (oldU == null) {
							oldUtxo.put(id, utxo);
							Log.build().param("utxo", utxo).event("Notifying receivers"); 
							onchainTransactionReceivedNotifier
									.notifyThem((m) -> m.onchainFundsReceived(utxo.address, utxo.amount));
						}
					}
				} catch (InterruptedException ex) {
					throw new StoredException("Waiting for new Funds thread interrupted", ex);
				} catch (Exception ex) {
					throw new StoredException("Exception in waiting for new Funds thread", ex);
				}
			}
			return null;
		});
	}

	private void subcribeTransactions() {

		LightningGrpc.LightningStub blockingStub = LightningGrpc.newStub(channel);

		Rpc.GetTransactionsRequest request = Rpc.GetTransactionsRequest.newBuilder().build();
		blockingStub.subscribeTransactions(request, new StreamObserver<Transaction>() {

			@Override
			public void onNext(Transaction tx) {
				try {
					Log.build().param("transaction", tx).event("New transaction returned");
//					onchainTransactionReceivedNotifier.notifyThem((m) -> m.onchainFundsReceived("addr", new Btc()));

				} catch (Exception ex) {
					new StoredException("Grab wallet balance failed", ex);
				}
			}

			@Override
			public void onError(Throwable t) {
				new StoredException("Subscibed transaction thrown error", t);
			}

			@Override
			public void onCompleted() {
				// Everything is OK
			}

		});

	}

//	private void extaractAmountPerAddr(Transaction tx) {
//		ListUnspentCommand listUnspentCmd = new ListUnspentCommand();
//		executor.execute(listUnspentCmd);
//		
//		if(listUnspentCmd.getError() != null) {
//			throw new StoredException("Cannot list unspent", listUnspentCmd.getError().toException());
//		}
//		
//		Map<String, Btc> newAddrAmounts = new HashMap<String, Btc>();
//		for(Utxo utxo : listUnspentCmd.getResponse().list) {
//			Btc amount = newAddrAmounts.get(utxo.address);
//			if(amount == null) {
//				newAddrAmounts.put(utxo.address, utxo.amount);
//			} else {
//				newAddrAmounts.put(utxo.address, amount.add(utxo.amount));
//			}
//		}	
//		
//		for(String addr : tx.getDestAddressesList()) {
//			Btc oldValue = addrAmounts.get(addr);
//			Btc newValue = newAddrAmounts.get(addr);

//			if(newValue.equals(oldValue))
//			
//			
//			FundsReceivedCommand cmd = new FundsReceivedCommand(addr, tx.);
//			commandBroadcaster.broadcast(cmd);
//		}
//	}
//		

//	
//	public void start() {
//		th.start();
//	}

	public void registerObserver(OnChainChangedEvent observer) {
		onchainAmountsChangedNotifier.register(observer);
	}

	public synchronized void grabWalletBalance() {
		WalletBalanceCommand cmd = new WalletBalanceCommand();
		executor.execute(cmd);
		if ((lastResponse == null) || Utils.deepEquals(lastResponse, cmd.getResponse()) == false) {
			lastResponse = cmd.getResponse();
//			Log.build().param("balance", lastResponse).event("Wallet balance changed");
			onchainAmountsChangedNotifier.notifyThem((m) -> m.onChange(lastResponse));
		}
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { WalletBalanceCommand.class, NewAddressCommand.class, SendOnChainCommand.class,
				ListChainTxnsCommand.class, RegisterFundsReceivedCommand.class, ListUnspentCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof WalletBalanceCommand) {
			WalletBalanceCommand cmd = (WalletBalanceCommand) command;
			grabWalletBalance();
			cmd.response = lastResponse;
		} else if (command instanceof RegisterFundsReceivedCommand) {
			RegisterFundsReceivedCommand cmd = (RegisterFundsReceivedCommand) command;
			onchainTransactionReceivedBroadcaster.addService(fromServiceName);
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
		try {
			isWorking.set(false);
			exNewFunds.shutdown();
			exNewFunds.awaitTermination(15, TimeUnit.SECONDS);
		} catch (Exception ex) {
			throw new StoredException("Exception during closing OnChainListener", ex);
		}
	}

	@Override
	public void addCustomData(GetNodeDetailsResponse details) {
		if (lastResponse != null) {
			details.summary.put("onchain_conf_", lastResponse.confirmed_balance.toString());
			details.summary.put("onchain_uconf", lastResponse.unconfirmed_balance.toString());
			details.summary.put("onchain_total", lastResponse.total_balance.toString());
		} else {
			details.summary.put("onchain_conf_", "pending...");
			details.summary.put("onchain_uconf", "pending...");
			details.summary.put("onchain_total", "pending...");
		}
	}

	@Override
	public void onchainFundsReceived(String addr, Btc amount) {
		FundsReceivedCommand cmd = new FundsReceivedCommand(addr, amount);
		Log.build().param("cmd", cmd).event("Broadcasting FundsReceivedCommand: " + onchainTransactionReceivedBroadcaster);
		onchainTransactionReceivedBroadcaster.broadcast(cmd);
	}

}
