package bittech.dae.controller.ln.lnd;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.ln.GetInfoResponse;
import bittech.lib.commands.ln.channels.CloseChannelCommand;
import bittech.lib.commands.ln.channels.DescribeGraphCommand;
import bittech.lib.commands.ln.channels.DescribeGraphResponse;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.ChannelInGraph;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.NodeAddress;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.NodeInGraph;
import bittech.lib.commands.ln.channels.DescribeGraphResponse.RoutingPolicy;
import bittech.lib.commands.ln.channels.FindRouteCommand;
import bittech.lib.commands.ln.channels.FindRouteResponse;
import bittech.lib.commands.ln.channels.Hop;
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
import bittech.lib.commands.ln.channels.PayToRouteCommand;
import bittech.lib.commands.ln.channels.Route;
import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.ln.invoices.AddInvoiceResponse;
import bittech.lib.commands.ln.invoices.DecodeInvoiceCommand;
import bittech.lib.commands.ln.invoices.DecodeInvoiceResponse;
import bittech.lib.commands.ln.invoices.ListInvoicesCommand;
import bittech.lib.commands.ln.invoices.ListInvoicesResponse;
import bittech.lib.commands.ln.invoices.ListInvoicesResponse.Invoice;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.ln.invoices.PayInvoiceResponse;
import bittech.lib.commands.ln.invoices.RouteHint;
import bittech.lib.commands.ln.invoices.RouteHint.HopHint;
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
import bittech.lib.utils.FormattedTime;
import bittech.lib.utils.FormattedTime.Precision;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;
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

				Log.build().param("response", response).event("Channel opened");
				cmd.response = new OpenChannelResponse();
				byte[] bb = new byte[response.getFundingTxidBytes().size()];
				int i = response.getFundingTxidBytes().size() - 1;
				for (byte b : response.getFundingTxidBytes().toByteArray()) {
					bb[i] = b;
					i--;
				}
				cmd.response.txId = Hex.encodeHexString(bb);
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
				builder.setMinConfs(0);
				builder.setMaxConfs(Integer.MAX_VALUE);

				Rpc.ListUnspentResponse response = blockingStub.listUnspent(builder.build());

				cmd.response = new ListUnspentResponse();

				cmd.response.list = new ArrayList<Utxo>(response.getUtxosCount());

				for (Rpc.Utxo rpcUtxo : response.getUtxosList()) {
					Utxo utxo = new Utxo();
					utxo.address = rpcUtxo.getAddress();
					utxo.txId = rpcUtxo.getOutpoint().getTxidStr();
					utxo.txIndex = rpcUtxo.getOutpoint().getOutputIndex();
					utxo.amount = Btc.fromSat(rpcUtxo.getAmountSat());
					utxo.confirmations = rpcUtxo.getConfirmations();
					utxo.scriptPubkey = rpcUtxo.getPkScript();
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
					channel.id = channelIdFromLong(lndChannel.getChanId());
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
				cmd.response.cltv_expiry = (int)response.getCltvExpiry();
				cmd.response.description = response.getDescription();
				cmd.response.description_hash = response.getDescriptionHash();
				cmd.response.destination = response.getDestination();
				cmd.response.expiry = response.getExpiry();
				cmd.response.fallback_addr = response.getFallbackAddr();
				cmd.response.payment_hash = response.getPaymentHash();
				cmd.response.route_hints = copyRouteHints(response.getRouteHintsList());
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
					cmd.response.route = new Route();
					cmd.response.route.totalTimeLock = response.getPaymentRoute().getTotalTimeLock();
					cmd.response.route.totalAmount = Btc.fromMsat(response.getPaymentRoute().getTotalAmtMsat());
					cmd.response.route.totalFees = Btc.fromMsat(response.getPaymentRoute().getTotalFeesMsat());
					cmd.response.route.hops = new ArrayList<Hop>(response.getPaymentRoute().getHopsCount());
					for (lnrpc.Rpc.Hop rcpHop : response.getPaymentRoute().getHopsList()) {
						Hop hop = new Hop();
						hop.amountToForward = Btc.fromMsat(rcpHop.getAmtToForwardMsat());
						hop.channelCapacity = Btc.fromSat(rcpHop.getChanCapacity());
						hop.channelId = channelIdFromLong(rcpHop.getChanId());
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
				for (LightningNode rpcNode : response.getNodesList()) {
					NodeInGraph node = new NodeInGraph();
					node.lastUpdate = rpcNode.getLastUpdate();
					node.id = rpcNode.getPubKey();
					node.alias = rpcNode.getAlias();
					node.addresses = new ArrayList<NodeAddress>(rpcNode.getAddressesCount());
					for (Rpc.NodeAddress rpcNodeAddress : rpcNode.getAddressesList()) {
						NodeAddress nodeAddress = new NodeAddress();
						nodeAddress.network = rpcNodeAddress.getNetwork();
						nodeAddress.addr = rpcNodeAddress.getAddr();
						node.addresses.add(nodeAddress);
					}
					node.color = rpcNode.getColor();
					cmd.response.nodes.add(node);
				}

				cmd.response.channels = new ArrayList<ChannelInGraph>(response.getEdgesCount());
				for (ChannelEdge rpcChannel : response.getEdgesList()) {
					ChannelInGraph channel = new ChannelInGraph();
					channel.last_update = rpcChannel.getLastUpdate();
					channel.id = channelIdFromLong(rpcChannel.getChannelId());
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

			} else if (command instanceof FindRouteCommand) {

				FindRouteCommand cmd = (FindRouteCommand) command;
				Rpc.QueryRoutesRequest.Builder builder = Rpc.QueryRoutesRequest.newBuilder();
				builder.setAmt(cmd.getRequest().amount.toSatRoundFloor());
				builder.setFinalCltvDelta(cmd.getRequest().finalCltvDelta);
			//	builder.setNumRoutes(1);
				builder.setPubKey(cmd.getRequest().destId);

				Rpc.QueryRoutesResponse response = blockingStub.queryRoutes(builder.build());

				if (response.getRoutesCount() == 0) {
					cmd.error = new ErrorResponse("No route found", 0L);
				} else {
					Rpc.Route rpcRoute = response.getRoutes(0);
					cmd.response = new FindRouteResponse();
					cmd.response.route = new Route();
					cmd.response.route.totalTimeLock = rpcRoute.getTotalTimeLock();
					cmd.response.route.totalAmount = Btc.fromMsat(rpcRoute.getTotalAmtMsat());
					cmd.response.route.totalFees = Btc.fromMsat(rpcRoute.getTotalFeesMsat());
					cmd.response.route.hops = new ArrayList<Hop>(rpcRoute.getHopsCount());
					for (Rpc.Hop rpcHop : rpcRoute.getHopsList()) {
						Hop hop = new Hop();
						hop.amountToForward = Btc.fromMsat(rpcHop.getAmtToForwardMsat());
						hop.channelCapacity = Btc.fromSat(rpcHop.getChanCapacity());
						hop.channelId = channelIdFromLong(rpcHop.getChanId());
						hop.expiry = rpcHop.getExpiry();
						hop.fee = Btc.fromMsat(rpcHop.getFeeMsat());
						hop.pubKey = rpcHop.getPubKey();
						cmd.response.route.hops.add(hop);
					}
				}

			} else if (command instanceof PayToRouteCommand) {

				PayToRouteCommand cmd = (PayToRouteCommand) command;

				Rpc.SendToRouteRequest.Builder builder = Rpc.SendToRouteRequest.newBuilder();
				builder.setPaymentHashString(cmd.getRequest().paymentHash);

				if(cmd.getRequest().route == null) {
					throw new Exception("Route not provided");
				}
				if(Btc.HasValue(cmd.getRequest().route.totalAmount) == false) {
					throw new Exception("Total amount for route not provided");
				}
				if(Btc.HasValue(cmd.getRequest().route.totalFees) == false) {
					throw new Exception("Total fees for route not provided");
				}

				Rpc.Route.Builder rpcRouteBuilder = Rpc.Route.newBuilder()
						.setTotalAmtMsat(cmd.getRequest().route.totalAmount.toMsat())
						.setTotalFeesMsat(cmd.getRequest().route.totalFees.toMsat())
						.setTotalTimeLock(cmd.getRequest().route.totalTimeLock);

				for (Hop hop : cmd.getRequest().route.hops) {
					Rpc.Hop.Builder rpcBuilder = Rpc.Hop.newBuilder();
					rpcBuilder.setAmtToForwardMsat(hop.amountToForward.toMsat());
//					rpcBuilder.setChanCapacity(value) Do we need this?
					rpcBuilder.setChanId(channelIdToLong(hop.channelId));
					rpcBuilder.setExpiry(hop.expiry);
					rpcBuilder.setFeeMsat(hop.fee.toMsat());
					rpcBuilder.setPubKey(hop.pubKey);

					rpcRouteBuilder.addHops(rpcBuilder.build());
				}

//				builder.setRoute(rpcRouteBuilder.build());
				builder.setRoute(rpcRouteBuilder.build());

				Rpc.SendToRouteRequest req = builder.build();
				Log.build().param("req", req).event("Sending to route");
				Rpc.SendResponse response = blockingStub.sendToRouteSync(req);

				if (!StringUtils.isEmpty(response.getPaymentError())) {
					throw new StoredException("Executing rpc on lnd failed", new Exception(response.getPaymentError()));
				}

//				Log.build().param("response", response).event("Send to route executed");

				cmd.response = new NoDataResponse(); // TODO: Tmp

			} else if (command instanceof ListInvoicesCommand) {
//				
//				String invoice = "lnbc10u1pwwh0l3pp55frt0rxf6nkkzjf8fxctey830sakgz9dfg6rj7v34tgv5yya5cgsdqqcqzysyylm9fw0mmffy0ps7kd9z6w09k7ngmpkuusqflu4js8u2nwjxzf3aenpsmsgh2d23czm2gg4ul3nmrhrs96sxqk3g3pfx8x2dr6pd4gpt0sqx3";
//				
//				Rpc.PayReq deodePayResponse;
//				{
//					Rpc.PayReqString.Builder builder = Rpc.PayReqString.newBuilder()
//						.setPayReq(invoice);
//					deodePayResponse = blockingStub.decodePayReq(builder.build());
//					Log.build().param("deodePayResponse", deodePayResponse).event("decodePayReq");
//				}
//				
//				Rpc.QueryRoutesResponse queryRoutesResponse = null;
//				{
//					Rpc.QueryRoutesRequest.Builder builder = Rpc.QueryRoutesRequest.newBuilder();
//					builder.setAmt(deodePayResponse.getNumSatoshis());
//					builder.setFinalCltvDelta((int)deodePayResponse.getCltvExpiry());
//					builder.setNumRoutes(1);
//					builder.setPubKey(deodePayResponse.getDestination());
//	
//					queryRoutesResponse = blockingStub.queryRoutes(builder.build());
//					Log.build().param("queryRoutesResponse", queryRoutesResponse).event("queryRoutes");
//				}
//				
//				{
//					Rpc.SendToRouteRequest.Builder builder = Rpc.SendToRouteRequest.newBuilder();
//					builder.setPaymentHashString(deodePayResponse.getPaymentHash()); // TODO: Payment hash
//					builder.addRoutes(queryRoutesResponse.getRoutes(0));
//					
//					Rpc.SendToRouteRequest req = builder.build();
//					Rpc.SendResponse response = blockingStub.sendToRouteSync(req);
//					Log.build().param("response", response).event("sendToRouteSync");
//				}
				

				ListInvoicesCommand cmd = (ListInvoicesCommand) command;
				Rpc.ListInvoiceRequest.Builder builder = Rpc.ListInvoiceRequest.newBuilder();

				Rpc.ListInvoiceResponse response = blockingStub.listInvoices(builder.build());

				cmd.response = new ListInvoicesResponse();
				cmd.response.invoices = new ArrayList<Invoice>(response.getInvoicesCount());
				for (Rpc.Invoice rpcInvoice : response.getInvoicesList()) {
					Invoice invoice = new Invoice();

					invoice.memo = rpcInvoice.getMemo(); // string An optional memo to attach along with the invoice.
															// Used for record keeping purposes for the invoice’s
															// creator, and will also be set in the description field of
															// the encoded payment request if the description_hash field
															// is not being used.
//						String r_preimage	bytes	The hex-encoded preimage (32 byte) which will allow settling an incoming HTLC payable to this preimage
					// r_hash bytes The hash of the preimage
					invoice.amount = Btc.fromSat(rpcInvoice.getValue()); // value int64 The value of this invoice in
																			// satoshis
					invoice.creation_date = new FormattedTime(1000L * rpcInvoice.getCreationDate(), Precision.SECONDS); // int64
																														// When
																														// this
																														// invoice
																														// was
																														// created
					invoice.settle_date = rpcInvoice.getSettleDate() == 0 ? null
							: new FormattedTime(1000L * rpcInvoice.getSettleDate(), Precision.SECONDS); // int64 When
																										// this invoice
																										// was settled
					invoice.payment_request = rpcInvoice.getPaymentRequest(); // string A bare-bones invoice for a
																				// payment within the Lightning Network.
																				// With the details of the invoice, the
																				// sender has all the data necessary to
																				// send a payment to the recipient.
					invoice.expiry = rpcInvoice.getExpiry(); // int64 Payment request expiry time in seconds. Default is
																// 3600 (1 hour).
					invoice.fallback_addr = rpcInvoice.getFallbackAddr(); // string Fallback on-chain address.
					invoice.cltv_expiry = rpcInvoice.getCltvExpiry(); // uint64 Delta to use for the time-lock of the
																		// CLTV extended to the final hop.
					invoice.route_hints = copyRouteHints(rpcInvoice.getRouteHintsList());
					invoice.isPrivate = rpcInvoice.getPrivate(); // bool Whether this invoice should include routing
																	// hints for private channels.
					invoice.add_index = rpcInvoice.getAddIndex(); // uint64 The “add” index of this invoice. Each newly
																	// created invoice will increment this index making
																	// it monotonically increasing. Callers to the
																	// SubscribeInvoices call can use this to instantly
																	// get notified of all added invoices with an
																	// add_index greater than this one.
					invoice.settle_index = rpcInvoice.getSettleIndex(); // uint64 The “settle” index of this invoice.
																		// Each newly settled invoice will increment
																		// this index making it monotonically
																		// increasing. Callers to the SubscribeInvoices
																		// call can use this to instantly get notified
																		// of all settled invoices with an settle_index
																		// greater than this one.
					invoice.amoutPaid = Btc.fromMsat(rpcInvoice.getAmtPaidMsat()); // int64 The amount that was accepted
																					// for this invoice, in
																					// millisatoshis. This will ONLY be
																					// set if this invoice has been
																					// settled. We provide this field as
																					// if the invoice was created with a
																					// zero value, then we need to
																					// record what amount was ultimately
																					// accepted. Additionally, it’s
																					// possible that the sender paid
																					// MORE that was specified in the
																					// original invoice. So we’ll record
																					// that here as well.
					invoice.state = rpcInvoice.getState().toString(); // InvoiceState The state the invoice is in.

					cmd.response.invoices.add(invoice);
				}

			} else {
				throw new StoredException("Command not supported by LndCommandsExecutor: " + command.type, null);
			}
		} catch (Exception ex) {
			command.response = null;
			command.error = new ErrorResponse(ex);
//			throw new StoredException("Failed to execute: " + JsonBuilder.build().toJson(command), ex);
		}
	}

	private List<RouteHint> copyRouteHints(List<lnrpc.Rpc.RouteHint> routeHints) {
		if (routeHints != null) {
			List<RouteHint> ret = new ArrayList<RouteHint>(routeHints.size());
			for (lnrpc.Rpc.RouteHint rpcHint : routeHints) {
				RouteHint hint = new RouteHint();
				List<lnrpc.Rpc.HopHint> hopHints = rpcHint.getHopHintsList();
				if (hopHints != null) {
					hint.hop_hints = new ArrayList<HopHint>();
					for (lnrpc.Rpc.HopHint rpcHop : hopHints) {
						HopHint hopHint = new HopHint();
						hopHint.channelId = channelIdFromLong(rpcHop.getChanId());
						hopHint.cltv_expiry_delta = rpcHop.getCltvExpiryDelta();
						hopHint.fee_base = Btc.fromMsat(rpcHop.getFeeBaseMsat());
						hopHint.fee_proportional_millionths = rpcHop.getFeeProportionalMillionths();
						hopHint.node_id = rpcHop.getNodeId();
						hint.hop_hints.add(hopHint);
					}
				}
				ret.add(hint);
			}
			return ret;
		}
		return null;
	}

	// NewShortChanIDFromInt returns a new ShortChannelID which is the decoded
	// version of the compact channel ID encoded within the uint64. The format of
	// the compact channel ID is as follows: 3 bytes for the block height, 3 bytes
	// for the transaction index, and 2 bytes for the output index.
//	String channelIdFromlong(long id) {
//		StringBuilder sb = new StringBuilder();
//		sb.append(Long.toString(id>>40));
//		sb.append(Long.toString(id>>16) & 0xFFFFFF);
//		sb.append(Long.toString(id>>40));
//		
	static String channelIdFromLong(long id) {
		StringBuilder sb = new StringBuilder();
		sb.append(id >> 40);
		sb.append(":");
		sb.append(id >> 16 & 0xFFFFFF);
		sb.append(":");
		sb.append(id & 0xFFFF);

		return sb.toString();
	}

	static long channelIdToLong(String id) {
		String[] list = id.split(":");
		return (Long.parseLong(list[0]) << 40) | ((Long.parseLong(list[1]) << 16)) | (Long.parseLong(list[2]));
	}

	public static void main(String[] args) {
		String s = "573916:1745:0";
		long l = channelIdToLong("573916:1745:0");
		System.out.println(l);
		System.out.println(s);
		System.out.println(channelIdFromLong(l));
	}

}
