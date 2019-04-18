package bittech.dae.controller.ln.lnd;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.ln.GetInfoResponse;
import bittech.lib.commands.ln.channels.CloseChannelCommand;
import bittech.lib.commands.ln.channels.DescribeGraphCommand;
import bittech.lib.commands.ln.channels.DescribeGraphResponse;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.ChannelInGraph;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.NodeAddress;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.NodeInGraph;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.RoutingPolicy;
import bittech.lib.commands.ln.channels.ListChannelsCommand;
import bittech.lib.commands.ln.channels.ListChannelsResponse;
import bittech.lib.commands.ln.channels.ListChannelsResponse.ActiveChannel;
import bittech.lib.commands.ln.channels.ListPendingChannelsCommand;
import bittech.lib.commands.ln.channels.ListPendingChannelsResponse;
import bittech.lib.commands.ln.channels.ListPendingChannelsResponse.ClosedChannel;
import bittech.lib.commands.ln.channels.ListPendingChannelsResponse.ForceClosedChannel;
import bittech.lib.commands.ln.channels.ListPendingChannelsResponse.PendingHTLC;
import bittech.lib.commands.ln.channels.ListPendingChannelsResponse.PendingOpenChannel;
import bittech.lib.commands.ln.channels.ListPendingChannelsResponse.WaitingCloseChannel;
import bittech.lib.commands.ln.channels.OpenChannelCommand;
import bittech.lib.commands.ln.channels.OpenChannelResponse;
import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.ln.invoices.AddInvoiceResponse;
import bittech.lib.commands.ln.invoices.DecodeInvoiceCommand;
import bittech.lib.commands.ln.invoices.DecodeInvoiceResponse;
import bittech.lib.commands.ln.invoices.DecodeInvoiceResponse.HopHint;
import bittech.lib.commands.ln.invoices.DecodeInvoiceResponse.RouteHint;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.ln.invoices.PayInvoiceResponse;
import bittech.lib.commands.ln.invoices.PayInvoiceResponse.Hop;
import bittech.lib.commands.ln.onchain.ListChainTxnsCommand;
import bittech.lib.commands.ln.onchain.ListChainTxnsResponse;
import bittech.lib.commands.ln.onchain.ListUnspentCommand;
import bittech.lib.commands.ln.onchain.ListUnspentResponse;
import bittech.lib.commands.ln.onchain.ListUnspentResponse.Utxo;
import bittech.lib.commands.ln.onchain.NewAddressCommand;
import bittech.lib.commands.ln.onchain.NewAddressResponse;
import bittech.lib.commands.ln.onchain.SendOnChainCommand;
import bittech.lib.commands.ln.onchain.SendOnChainResponse;
import bittech.lib.commands.ln.onchain.WalletBalanceCommand;
import bittech.lib.commands.ln.onchain.WalletBalanceResponse;
import bittech.lib.commands.ln.peers.ConnectPeerCommand;
import bittech.lib.commands.ln.peers.ListPeersCommand;
import bittech.lib.commands.ln.peers.ListPeersResponse;
import bittech.lib.commands.ln.peers.ListPeersResponse.Peer;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;
import io.grpc.ManagedChannel;
import lnrpc.LightningGrpc;
import lnrpc.Rpc;
import lnrpc.Rpc.AddressType;
import lnrpc.Rpc.Chain;
import lnrpc.Rpc.ChannelEdge;
import lnrpc.Rpc.LightningAddress;
import lnrpc.Rpc.LightningNode;
import lnrpc.Rpc.Transaction;

public class LndCommandsExecutor {

	private final ManagedChannel channel;

	public LndCommandsExecutor(final ManagedChannel channel) {
		this.channel = Require.notNull(channel, "channel");
	}

	public void execute(Command<?, ?> command) {
		try {
			LightningGrpc.LightningBlockingStub blockingStub = LightningGrpc.newBlockingStub(channel);

			// !!! Commented out, because no blockingStub.unlockWallet method :/ !!!
//			if (command instanceof UnlockWalletCommand) { 
//				UnlockWalletCommand cmd = (UnlockWalletCommand)command;
//				Rpc.UnlockWalletRequest request = Rpc.UnlockWalletRequest.newBuilder().setWalletPassword(ByteString.copyFromUtf8(cmd.getRequest().password)).build();
////				Rpc.UnlockWalletResponse response = blockingStub.
//			}

			if (command instanceof GetInfoCommand) {

				GetInfoCommand cmd = (GetInfoCommand) command;
				Rpc.GetInfoRequest request = Rpc.GetInfoRequest.newBuilder().build();
				Rpc.GetInfoResponse response = blockingStub.getInfo(request);

				cmd.response = new GetInfoResponse();
				cmd.response.alias = response.getAlias();
				cmd.response.best_header_timestamp = response.getBestHeaderTimestamp();
				cmd.response.block_hash = response.getBlockHash();
				cmd.response.block_height = response.getBlockHeight();
				cmd.response.chains = new ArrayList<String>(response.getChainsCount());
				for (Chain chain : response.getChainsList()) {
					cmd.response.chains.add(chain.getChain());
				}
				cmd.response.identity_pubkey = response.getIdentityPubkey();
				cmd.response.num_active_channels = response.getNumActiveChannels();
				cmd.response.num_inactive_channels = response.getNumInactiveChannels();
				cmd.response.num_peers = response.getNumPeers();
				cmd.response.num_pending_channels = response.getNumPendingChannels();
				cmd.response.synced_to_chain = response.getSyncedToChain();
				cmd.response.uris = response.getUrisList();
				cmd.response.version = response.getVersion();

			} else if (command instanceof NewAddressCommand) {

				NewAddressCommand cmd = (NewAddressCommand) command;
				Rpc.NewAddressRequest request = Rpc.NewAddressRequest.newBuilder()
						.setType(AddressType.WITNESS_PUBKEY_HASH).build();
				Rpc.NewAddressResponse response = blockingStub.newAddress(request);
				cmd.response = new NewAddressResponse();
				cmd.response.address = response.getAddress();

			} else if (command instanceof WalletBalanceCommand) {

				WalletBalanceCommand cmd = (WalletBalanceCommand) command;
				Rpc.WalletBalanceRequest request = Rpc.WalletBalanceRequest.newBuilder().build();
				Rpc.WalletBalanceResponse response = blockingStub.walletBalance(request);

				cmd.response = new WalletBalanceResponse();
				cmd.response.confirmed_balance = Btc.fromSat(response.getConfirmedBalance());
				cmd.response.total_balance = Btc.fromSat(response.getTotalBalance());
				cmd.response.unconfirmed_balance = Btc.fromSat(response.getUnconfirmedBalance());

			} else if (command instanceof ListChainTxnsCommand) {

				ListChainTxnsCommand cmd = (ListChainTxnsCommand) command;
				Rpc.GetTransactionsRequest request = Rpc.GetTransactionsRequest.newBuilder().build();
				Rpc.TransactionDetails response = blockingStub.getTransactions(request);

				cmd.response = new ListChainTxnsResponse();
				for (Transaction tx : response.getTransactionsList()) {
					ListChainTxnsResponse.OnchainTransaction otx = new ListChainTxnsResponse.OnchainTransaction();
					otx.amount = Btc.fromSat(tx.getAmount());
					otx.block_hash = tx.getBlockHash();
					otx.block_height = tx.getBlockHeight();
					otx.dest_addresses = tx.getDestAddressesList();
					otx.num_confirmations = tx.getNumConfirmations();
					otx.time_stamp = tx.getTimeStamp();
					otx.total_fees = Btc.fromMsat(tx.getTotalFees());
					otx.tx_hash = tx.getTxHash();

					cmd.response.transactions.add(otx);
				}

			} else if (command instanceof ConnectPeerCommand) {

				ConnectPeerCommand cmd = (ConnectPeerCommand) command;
				String[] splited = cmd.getRequest().uri.split("@");
				if (splited.length != 2) {
					throw new Exception("Incorrect uri format: " + cmd.getRequest().uri);
				}
				LightningAddress addr = LightningAddress.newBuilder().setPubkey(splited[0]).setHost(splited[1]).build();
				Rpc.ConnectPeerRequest request = Rpc.ConnectPeerRequest.newBuilder().setAddr(addr).build();
				/* Rpc.ConnectPeerResponse response = */blockingStub.connectPeer(request);

				cmd.response = new NoDataResponse();

			} else if (command instanceof OpenChannelCommand) {

				OpenChannelCommand cmd = (OpenChannelCommand) command;
				Rpc.OpenChannelRequest request = Rpc.OpenChannelRequest.newBuilder()
						.setNodePubkeyString(cmd.getRequest().nodeId)
						.setLocalFundingAmount(cmd.getRequest().capacity.toSatRoundFloor())
						.setPushSat(cmd.getRequest().pushAmount.toSatRoundFloor()).build();
				Rpc.ChannelPoint response = blockingStub.openChannelSync(request);

				cmd.response = new OpenChannelResponse();
				cmd.response.txId = response.getFundingTxidStr();
				cmd.response.outputIndex = response.getOutputIndex();

			} else if (command instanceof AddInvoiceCommand) {

				AddInvoiceCommand cmd = (AddInvoiceCommand) command;
				Rpc.Invoice.Builder builder = Rpc.Invoice.newBuilder();

				if (cmd.getRequest().value.hasValue()) {
					builder.setValue(cmd.getRequest().value.toSatRoundFloor());
				}

				if (!cmd.getRequest().memo.isEmpty()) {
					builder.setMemo(cmd.getRequest().memo);
				}

				Rpc.AddInvoiceResponse response = blockingStub.addInvoice(builder.build());

				cmd.response = new AddInvoiceResponse();
				cmd.response.payment_request = response.getPaymentRequest();
				cmd.response.add_index = response.getAddIndex();

			} else if (command instanceof SendOnChainCommand) {

				SendOnChainCommand cmd = (SendOnChainCommand) command;
				Rpc.SendCoinsRequest.Builder builder = Rpc.SendCoinsRequest.newBuilder();

				if (cmd.getRequest().amount.hasValue()) {
					builder.setAmount(cmd.getRequest().amount.toSatRoundFloor());
				}

				builder.setAddr(cmd.getRequest().addr);

				Rpc.SendCoinsResponse response = blockingStub.sendCoins(builder.build());

				cmd.response = new SendOnChainResponse();
				cmd.response.txId = response.getTxid();

			} else if (command instanceof ListUnspentCommand) {

				ListUnspentCommand cmd = (ListUnspentCommand) command;
				Rpc.ListUnspentRequest.Builder builder = Rpc.ListUnspentRequest.newBuilder();

				Rpc.ListUnspentResponse response = blockingStub.listUnspent(builder.build());

				cmd.response = new ListUnspentResponse();
				
				cmd.response.list = new ArrayList<Utxo>(response.getUtxosCount());
				
				for(Rpc.Utxo rpcUtxo : response.getUtxosList()) {
					Utxo utxo = new Utxo();
					utxo.address = rpcUtxo.getAddress();
					utxo.amount = Btc.fromSat(rpcUtxo.getAmountSat());
					utxo.confirmations = rpcUtxo.getConfirmations();
					utxo.scriptPubkey = rpcUtxo.getScriptPubkey();
					utxo.type = rpcUtxo.getType().toString();
					cmd.response.list.add(utxo);
				}			
				
			} else if (command instanceof ListChannelsCommand) {

				ListChannelsCommand cmd = (ListChannelsCommand) command;
				Rpc.ListChannelsRequest.Builder builder = Rpc.ListChannelsRequest.newBuilder();

				Rpc.ListChannelsResponse response = blockingStub.listChannels(builder.build());

				cmd.response = new ListChannelsResponse();
				cmd.response.channels = new ArrayList<ActiveChannel>(response.getChannelsCount());

				for (lnrpc.Rpc.Channel lndChannel : response.getChannelsList()) {
					ActiveChannel channel = new ActiveChannel();
					channel.remote_node_pub = lndChannel.getRemotePubkey();
					channel.active = lndChannel.getActive();
					channel.capacity = Btc.fromSat(lndChannel.getCapacity());
					channel.chan_id = lndChannel.getChanId();
					channel.channel_point = lndChannel.getChannelPoint();
					channel.commit_fee = Btc.fromSat(lndChannel.getCommitFee());
					channel.commit_weight = lndChannel.getCommitWeight();
					channel.csv_delay = lndChannel.getCsvDelay();
					channel.fee_per_kw = Btc.fromSat(lndChannel.getFeePerKw());
					channel.initiator = lndChannel.getInitiator();
					channel.isPrivate = lndChannel.getPrivate();
					channel.local_balance = Btc.fromSat(lndChannel.getLocalBalance());
					channel.num_updates = lndChannel.getNumUpdates();
					channel.pending_htlcs = null; // Todo: change
					channel.remote_balance = Btc.fromSat(lndChannel.getRemoteBalance());
					channel.total_satoshis_received = Btc.fromSat(lndChannel.getTotalSatoshisReceived());
					channel.total_satoshis_sent = Btc.fromSat(lndChannel.getTotalSatoshisSent());
					channel.unsettled_balance = Btc.fromSat(lndChannel.getUnsettledBalance());

					cmd.response.channels.add(channel);
				}
			} else if (command instanceof DecodeInvoiceCommand) {
				DecodeInvoiceCommand cmd = (DecodeInvoiceCommand) command;

				Rpc.PayReqString.Builder builder = Rpc.PayReqString.newBuilder()
						.setPayReq(cmd.getRequest().paymentRequest);
				Rpc.PayReq response = blockingStub.decodePayReq(builder.build());

				cmd.response = new DecodeInvoiceResponse();
				cmd.response.amount = response.getNumSatoshis() > 0 ? Btc.fromSat(response.getNumSatoshis()) : null;
				cmd.response.cltv_expiry = response.getCltvExpiry();
				cmd.response.description = response.getDescription();
				cmd.response.description_hash = response.getDescriptionHash();
				cmd.response.destination = response.getDescription();
				cmd.response.expiry = response.getExpiry();
				cmd.response.fallback_addr = response.getFallbackAddr();
				cmd.response.payment_hash = response.getPaymentHash();
				List<lnrpc.Rpc.RouteHint> routeHints = response.getRouteHintsList();
				if (routeHints != null) {
					cmd.response.route_hints = new ArrayList<RouteHint>(routeHints.size());
					for (lnrpc.Rpc.RouteHint rpcHint : routeHints) {
						RouteHint hint = new RouteHint();
						List<lnrpc.Rpc.HopHint> hopHints = rpcHint.getHopHintsList();
						if (hopHints != null) {
							hint.hop_hints = new ArrayList<HopHint>();
							for (lnrpc.Rpc.HopHint rpcHop : hopHints) {
								HopHint hopHint = new HopHint();
								hopHint.chan_id = rpcHop.getChanId();
								hopHint.cltv_expiry_delta = rpcHop.getCltvExpiryDelta();
								hopHint.fee_base = Btc.fromMsat(rpcHop.getFeeBaseMsat());
								hopHint.fee_proportional_millionths = rpcHop.getFeeProportionalMillionths();
								hopHint.node_id = rpcHop.getNodeId();
								hint.hop_hints.add(hopHint);
							}
						}
						cmd.response.route_hints.add(hint);
					}
				}
				cmd.response.timestamp = response.getTimestamp();

			} else if (command instanceof PayInvoiceCommand) {
				PayInvoiceCommand cmd = (PayInvoiceCommand) command;

				Rpc.SendRequest.Builder builder = Rpc.SendRequest.newBuilder()
						.setPaymentRequest(cmd.getRequest().paymentRequest);

				if (cmd.getRequest().feeLimit.hasValue()) {
					builder.setFeeLimit(
							Rpc.FeeLimit.newBuilder().setFixed(cmd.getRequest().feeLimit.toSatRoundFloor()).build());
				}

				if (cmd.getRequest().amount.hasValue()) {
					builder.setAmt(cmd.getRequest().amount.toSatRoundFloor());
				}

				Rpc.SendResponse response = blockingStub.sendPaymentSync(builder.build());
				if (response.getPaymentError() != null && !response.getPaymentError().isEmpty()) {
					cmd.error = new ErrorResponse(
							new Exception("Payment failed. Reason from LND: " + response.getPaymentError()));
				} else {
					cmd.response = new PayInvoiceResponse();
					cmd.response.paymentPreimage = Base64
							.encodeBase64String(response.getPaymentPreimage().toByteArray());
					cmd.response.route = new PayInvoiceResponse.Route();
					cmd.response.route.totalTimeLock = response.getPaymentRoute().getTotalTimeLock();
					cmd.response.route.totalAmount = Btc.fromMsat(response.getPaymentRoute().getTotalAmtMsat());
					cmd.response.route.totalFees = Btc.fromMsat(response.getPaymentRoute().getTotalFeesMsat());
					cmd.response.route.hops = new ArrayList<Hop>(response.getPaymentRoute().getHopsCount());
					for (lnrpc.Rpc.Hop rcpHop : response.getPaymentRoute().getHopsList()) {
						Hop hop = new Hop();
						hop.amountToForward = Btc.fromMsat(rcpHop.getAmtToForwardMsat());
						hop.channelCapacity = Btc.fromSat(rcpHop.getChanCapacity());
						hop.channelId = rcpHop.getChanId();
						hop.expiry = rcpHop.getExpiry();
						hop.fee = Btc.fromMsat(rcpHop.getFeeMsat());
						hop.pubKey = rcpHop.getPubKey();
						cmd.response.route.hops.add(hop);
					}

				}
			} else if (command instanceof CloseChannelCommand) {
				CloseChannelCommand cmd = (CloseChannelCommand) command;
				Rpc.CloseChannelRequest.Builder builder = Rpc.CloseChannelRequest.newBuilder();

				String[] point = cmd.getRequest().channelPoint.split(":");
				if (point.length != 2) {
					throw new Exception("Incorrect channel point format: " + cmd.getRequest().channelPoint);
				}

				builder.setChannelPoint(Rpc.ChannelPoint.newBuilder().setFundingTxidStr(point[0])
						.setOutputIndex(Integer.parseInt(point[1])));
				builder.setForce(cmd.getRequest().force);

				blockingStub.closeChannel(builder.build());

				cmd.response = new NoDataResponse();

			} else if (command instanceof ListPeersCommand) {
				ListPeersCommand cmd = (ListPeersCommand) command;
				Rpc.ListPeersRequest.Builder builder = Rpc.ListPeersRequest.newBuilder();

				Rpc.ListPeersResponse response = blockingStub.listPeers(builder.build());

				cmd.response = new ListPeersResponse();

				cmd.response.peers = new ArrayList<Peer>(response.getPeersCount());
				for (Rpc.Peer rpcPeer : response.getPeersList()) {
					ListPeersResponse.Peer peer = new ListPeersResponse.Peer();
					peer.id = rpcPeer.getPubKey();
					peer.address = rpcPeer.getAddress();
					peer.bytesReceived = rpcPeer.getBytesRecv();
					peer.bytesSent = rpcPeer.getBytesSent();
					peer.inbound = rpcPeer.getInbound();
					peer.pingTime = rpcPeer.getPingTime();
					peer.receivedAmount = Btc.fromSat(rpcPeer.getSatRecv());
					peer.sentAmount = Btc.fromSat(rpcPeer.getSatSent());
					cmd.response.peers.add(peer);
				}

			} else if (command instanceof ListPendingChannelsCommand) {
				ListPendingChannelsCommand cmd = (ListPendingChannelsCommand) command;
				Rpc.PendingChannelsRequest.Builder builder = Rpc.PendingChannelsRequest.newBuilder();

				Rpc.PendingChannelsResponse response = blockingStub.pendingChannels(builder.build());

				cmd.response = new ListPendingChannelsResponse();

				cmd.response.total_limbo_balance = Btc.fromSat(response.getTotalLimboBalance());

				cmd.response.pending_open_channels = new ArrayList<PendingOpenChannel>(
						response.getPendingOpenChannelsCount());
				for (Rpc.PendingChannelsResponse.PendingOpenChannel rpcChannel : response
						.getPendingOpenChannelsList()) {
					PendingOpenChannel channel = new PendingOpenChannel();
					channel.remote_node_pub = rpcChannel.getChannel().getRemoteNodePub();
					channel.capacity = Btc.fromSat(rpcChannel.getChannel().getCapacity());
					channel.channel_point = rpcChannel.getChannel().getChannelPoint();
					channel.local_balance = Btc.fromSat(rpcChannel.getChannel().getLocalBalance());
					channel.remote_balance = Btc.fromSat(rpcChannel.getChannel().getRemoteBalance());
					channel.commit_fee = Btc.fromSat(rpcChannel.getCommitFee());
					channel.commit_weight = rpcChannel.getCommitWeight();
					channel.confirmation_height = rpcChannel.getConfirmationHeight();
					channel.fee_per_kw = Btc.fromSat(rpcChannel.getFeePerKw());
					cmd.response.pending_open_channels.add(channel);
				}

				cmd.response.pending_closing_channels = new ArrayList<ClosedChannel>(
						response.getPendingClosingChannelsCount());
				for (Rpc.PendingChannelsResponse.ClosedChannel rpcChannel : response.getPendingClosingChannelsList()) {
					ClosedChannel channel = new ClosedChannel();
					channel.remote_node_pub = rpcChannel.getChannel().getRemoteNodePub();
					channel.capacity = Btc.fromSat(rpcChannel.getChannel().getCapacity());
					channel.channel_point = rpcChannel.getChannel().getChannelPoint();
					channel.local_balance = Btc.fromSat(rpcChannel.getChannel().getLocalBalance());
					channel.remote_balance = Btc.fromSat(rpcChannel.getChannel().getRemoteBalance());
					channel.closing_txid = rpcChannel.getClosingTxid();
					cmd.response.pending_closing_channels.add(channel);
				}

				cmd.response.pending_force_closing_channels = new ArrayList<ForceClosedChannel>(
						response.getPendingForceClosingChannelsCount());
				for (Rpc.PendingChannelsResponse.ForceClosedChannel rpcChannel : response
						.getPendingForceClosingChannelsList()) {
					ForceClosedChannel channel = new ForceClosedChannel();
					channel.remote_node_pub = rpcChannel.getChannel().getRemoteNodePub();
					channel.capacity = Btc.fromSat(rpcChannel.getChannel().getCapacity());
					channel.channel_point = rpcChannel.getChannel().getChannelPoint();
					channel.local_balance = Btc.fromSat(rpcChannel.getChannel().getLocalBalance());
					channel.remote_balance = Btc.fromSat(rpcChannel.getChannel().getRemoteBalance());
					channel.closing_txid = rpcChannel.getClosingTxid();
					channel.limbo_balance = Btc.fromSat(rpcChannel.getLimboBalance());
					channel.maturity_height = rpcChannel.getMaturityHeight();
					channel.pending_htlcs = new ArrayList<PendingHTLC>(rpcChannel.getPendingHtlcsCount());
					for (Rpc.PendingHTLC rpcHtlc : rpcChannel.getPendingHtlcsList()) {
						PendingHTLC htlc = new PendingHTLC();
						htlc.amount = Btc.fromSat(rpcHtlc.getAmount());
						htlc.blocks_til_maturity = rpcHtlc.getBlocksTilMaturity();
						htlc.incoming = rpcHtlc.getIncoming();
						htlc.maturity_height = rpcHtlc.getMaturityHeight();
						htlc.outpoint = rpcHtlc.getOutpoint();
						htlc.stage = rpcHtlc.getStage();
					}
					channel.recovered_balance = Btc.fromSat(rpcChannel.getRecoveredBalance());
					cmd.response.pending_force_closing_channels.add(channel);
				}

				cmd.response.waiting_close_channels = new ArrayList<WaitingCloseChannel>(
						response.getWaitingCloseChannelsCount());
				for (Rpc.PendingChannelsResponse.WaitingCloseChannel rpcChannel : response
						.getWaitingCloseChannelsList()) {
					WaitingCloseChannel channel = new WaitingCloseChannel();
					channel.remote_node_pub = rpcChannel.getChannel().getRemoteNodePub();
					channel.capacity = Btc.fromSat(rpcChannel.getChannel().getCapacity());
					channel.channel_point = rpcChannel.getChannel().getChannelPoint();
					channel.local_balance = Btc.fromSat(rpcChannel.getChannel().getLocalBalance());
					channel.remote_balance = Btc.fromSat(rpcChannel.getChannel().getRemoteBalance());
					channel.limbo_balance = Btc.fromSat(rpcChannel.getLimboBalance());
					cmd.response.waiting_close_channels.add(channel);
				}

			} else if (command instanceof DescribeGraphCommand) {
				
				DescribeGraphCommand cmd = (DescribeGraphCommand) command;
				Rpc.ChannelGraphRequest.Builder builder = Rpc.ChannelGraphRequest.newBuilder();

				Rpc.ChannelGraph response = blockingStub.describeGraph(builder.build());
				
				cmd.response = new DescribeGraphResponse();
				
				cmd.response.nodes = new ArrayList<NodeInGraph>(response.getNodesCount());
				for(LightningNode rpcNode : response.getNodesList()) {
					NodeInGraph node = new NodeInGraph();
					node.lastUpdate = rpcNode.getLastUpdate();
					node.id = rpcNode.getPubKey();
					node.alias = rpcNode.getAlias();
					node.addresses = new ArrayList<NodeAddress>(rpcNode.getAddressesCount());
					for(Rpc.NodeAddress rpcNodeAddress : rpcNode.getAddressesList()) {
						NodeAddress nodeAddress = new NodeAddress();
						nodeAddress.network = rpcNodeAddress.getNetwork();
						nodeAddress.addr = rpcNodeAddress.getAddr();
						node.addresses.add(nodeAddress);
					}
					node.color = rpcNode.getColor();
					cmd.response.nodes.add(node);
				}
				
				cmd.response.channels = new ArrayList<ChannelInGraph>(response.getEdgesCount());
				for(ChannelEdge rpcChannel : response.getEdgesList()) {
					ChannelInGraph channel = new ChannelInGraph();
					channel.last_update = rpcChannel.getLastUpdate();
					channel.id = rpcChannel.getChannelId();
					channel.point = rpcChannel.getChanPoint();
					channel.capacitySat = rpcChannel.getCapacity();
					channel.node1Id = rpcChannel.getNode1Pub();
					channel.node1_policy = new RoutingPolicy();
					channel.node1_policy.time_lock_delta = rpcChannel.getNode1Policy().getTimeLockDelta();
					channel.node1_policy.min_htlc = rpcChannel.getNode1Policy().getMinHtlc();
					channel.node1_policy.max_htlc_msat = 0; // TODO: Use the newset rpc version
					channel.node1_policy.fee_base_msat = rpcChannel.getNode1Policy().getFeeBaseMsat();
					channel.node1_policy.fee_rate_milli_msat = rpcChannel.getNode1Policy().getFeeRateMilliMsat();
					channel.node1_policy.disabled = rpcChannel.getNode1Policy().getDisabled();
					channel.node2Id = rpcChannel.getNode2Pub();
					channel.node2_policy = new RoutingPolicy();
					channel.node2_policy.time_lock_delta = rpcChannel.getNode2Policy().getTimeLockDelta();
					channel.node2_policy.min_htlc = rpcChannel.getNode2Policy().getMinHtlc();
					channel.node2_policy.max_htlc_msat = 0; // TODO: Use the newset rpc version
					channel.node2_policy.fee_base_msat = rpcChannel.getNode2Policy().getFeeBaseMsat();
					channel.node2_policy.fee_rate_milli_msat = rpcChannel.getNode2Policy().getFeeRateMilliMsat();
					channel.node2_policy.disabled = rpcChannel.getNode2Policy().getDisabled();
					cmd.response.channels.add(channel);
				}

				
			} else {

				throw new StoredException("Command not supported by LndCommandsExecutor: " + command.type, null);
			}
		} catch (Exception ex) {
			throw new StoredException("Failed to execute: " + JsonBuilder.build().toJson(command), ex);
		}
	}

}
