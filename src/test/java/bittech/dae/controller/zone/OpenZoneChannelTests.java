package bittech.dae.controller.zone;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

import bittech.dae.controller.helpers.FakeZoneNode;
import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.commands.ln.GetInfoResponse;
import bittech.lib.commands.ln.channels.OpenChannelCommand;
import bittech.lib.commands.ln.channels.OpenChannelResponse;
import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.ln.invoices.AddInvoiceResponse;
import bittech.lib.commands.ln.invoices.PayInvoiceCommand;
import bittech.lib.commands.ln.invoices.PayInvoiceResponse;
import bittech.lib.commands.ln.invoices.PaymentReceivedCommand;
import bittech.lib.commands.ln.invoices.PaymentReceivedRequest;
import bittech.lib.commands.ln.peers.ConnectPeerCommand;
import bittech.lib.commands.ln.peers.ListPeersWithChannelsCommand;
import bittech.lib.commands.ln.peers.ListPeersWithChannelsResponse;
import bittech.lib.commands.ln.peers.ListPeersWithChannelsResponse.Peer;
import bittech.lib.commands.lnzone.commans.OpenPeerChannelCommand;
import bittech.lib.commands.lnzone.external.GetPeerOfferCommand;
import bittech.lib.commands.lnzone.internal.GetOfferCommand;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Node;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.ExceptionManager;
import bittech.lib.utils.json.RawJson;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class OpenZoneChannelTests extends TestCase {

	ExecutorService executor;

	public OpenZoneChannelTests(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(OpenZoneChannelTests.class);
	}

	protected void setUp() throws InterruptedException {
		Config.loadEmptyConfig();
		Config.getInstance().addEntry("connectionKeys", new RawJson(Crypto.generateKeys()));
		Config.getInstance().addEntry("saveLogs", false);
		Config.getInstance().addEntry("saveChannels", false);
		Config.getInstance().addEntry("saveInvoices", false);
		Config.getInstance().addEntry("printExceptions", false);
		Config.getInstance().addEntry("connectZoneToManager", false);

		ExceptionManager.getInstance().deleteAll();

		executor = Executors.newCachedThreadPool();
	}

	protected void tearDown() {
		Assert.assertEquals(0, ExceptionManager.getInstance().getExceptionIds().size());
	}

	public void testInitOnly() throws Exception {

		int zone1Port = 1741;
		int zone2Port = 1742;

		try (FakeZoneNode zone1 = new FakeZoneNode("zone_module_node", zone1Port, 1511);
				FakeZoneNode zone2 = new FakeZoneNode("zone_module_node", zone2Port, 1512)) {
		}

		Assert.assertEquals(0, ExceptionManager.getInstance().getExceptionIds().size());
	}

	public void testGetPeerOffer() throws Exception {

		try (FakeZoneNode zone1 = new FakeZoneNode("zone_module_node", 1741, 1511);
				FakeZoneNode zone2 = new FakeZoneNode("zone_module_node", 1742, 1512);
				Node myNode = new Node("test-node")) {

			myNode.connect("zone1", "localhost", zone1.listeningPort);
			myNode.connect("zone2", "localhost", zone2.listeningPort);

			// Get offer and uri directly from zone1
			GetOfferCommand getOfferCommand = new GetOfferCommand();
			myNode.execute("zone1", getOfferCommand);
			Assert.assertNull(getOfferCommand.getError());
			Assert.assertNotNull(getOfferCommand.getResponse());

			// Ask zone2 to get offer from zone1
			GetPeerOfferCommand getZoneOfferCommand = new GetPeerOfferCommand(getOfferCommand.getResponse().uri);
			myNode.execute("zone2", getZoneOfferCommand);
			Assert.assertNull(getZoneOfferCommand.getError());
			Assert.assertNotNull(getZoneOfferCommand.getResponse());

			// Check if this is exactly the same offer
			Assert.assertTrue(
					Utils.deepEquals(getOfferCommand.getResponse().offer, getZoneOfferCommand.getResponse().offer));
		}

		if (ExceptionManager.getInstance().getExceptionIds().size() != 0) {
			for (long id : ExceptionManager.getInstance().getExceptionIds()) {
				Utils.prn(ExceptionManager.getInstance().get(id));
			}
			Assert.fail("There are exceptions");
		}

	}

	private String openZoneChannel(Node myNode, FakeZoneNode zone1, FakeZoneNode zone2, Btc myAmount, Btc peerAmount,
			Btc feeReserve) throws Exception {
		// Get offer and uri directly from zone1
		GetOfferCommand getOfferCommand = new GetOfferCommand();
		myNode.execute("zone1", getOfferCommand);
		Assert.assertNull(getOfferCommand.getError());
		Assert.assertNotNull(getOfferCommand.getResponse());

		Future<Void> future = executor.submit(() -> {
			// Thread initFeedThread = new SimpleThread(() -> {
			try {
				{ // Zone2 getting his lnd port and IP address to prepare openZoneChannel
					// request to zone1
					GetInfoCommand cmd = zone2.tester.waitFor(GetInfoCommand.class);
					cmd.response = new GetInfoResponse();
					cmd.response.alias = "zone2_clightning_node_id";
					cmd.response.uris = new LinkedList<String>();
					cmd.response.uris.add("peer_node_id@127.0.0.1:3281");
					cmd.response.block_height = 0;
					zone2.tester.submit(cmd);
				}

				// Here openZoneChannel request is sent from zone2 to zone1

				{ // Zone1 needs to check if there is no channel created already
					ListPeersWithChannelsCommand cmd = zone1.tester.waitFor(ListPeersWithChannelsCommand.class);
					cmd.response = new ListPeersWithChannelsResponse();
					cmd.response.peers = new LinkedList<Peer>();
					zone1.tester.submit(cmd);
				}

				{ // Zone1 asks his lnd module to connect to Zone2 lnd module to be
					// sure if this is possible
					ConnectPeerCommand cmd = zone1.tester.waitFor(ConnectPeerCommand.class);
					cmd.response = new NoDataResponse();
					zone1.tester.submit(cmd);
				}
				
				String invoiceLabel;
				{ // Zone1 creates invoice for Zone2 to pay all costs of channel opening
					AddInvoiceCommand cmd = zone1.tester.waitFor(AddInvoiceCommand.class);

					Btc costs = getOfferCommand.getResponse().offer.fixedCost.add(feeReserve).add(myAmount);
					Assert.assertTrue(costs.equals(cmd.request.value));

					invoiceLabel = cmd.getRequest().label;
					cmd.response = new AddInvoiceResponse();
					cmd.response.payment_request = "bolt11_encoded_for_client_payment";
					cmd.response.add_index = 345;
					zone1.tester.submit(cmd);
				}
				
				{ // Zone2 pays this invoice
					PayInvoiceCommand cmd = zone2.tester.waitFor(PayInvoiceCommand.class);
					cmd.response = new PayInvoiceResponse();
					zone2.tester.submit(cmd);
				}
				
				{ // clightning informs Zone1 that payment was received
					PaymentReceivedRequest req = new PaymentReceivedRequest();
					req.label = invoiceLabel;
					PaymentReceivedCommand paymentReceivedCommand = new PaymentReceivedCommand(req);
					myNode.execute("zone1", paymentReceivedCommand);
				}
				
				{ // Zone1 again asks his lnd module to connect to Zone2 lnd module
					// in case if old connection lost
					ConnectPeerCommand cmd = zone1.tester.waitFor(ConnectPeerCommand.class);
					cmd.response = new NoDataResponse();
					zone1.tester.submit(cmd);
				}
				
				{
					OpenChannelCommand cmd = zone1.tester.waitFor(OpenChannelCommand.class);

					Assert.assertEquals("peer_node_id", cmd.getRequest().nodeId);
					Assert.assertEquals(myAmount.toSatRoundFloor() + peerAmount.toSatRoundFloor(),
							cmd.getRequest().capacity.toSatRoundFloor());

					cmd.response = new OpenChannelResponse();
					cmd.response.txId = "fund_channel_txid";
					cmd.response.outputIndex = 123;
					zone1.tester.submit(cmd);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				ErrorResponse errorResponse = new ErrorResponse(0, "Test failed: " + ex.getMessage());
				zone1.tester.submitErrorToAll(errorResponse);
				zone2.tester.submitErrorToAll(errorResponse);
				throw new Exception("Test failed", ex);
			}
			return null;
		});

		// Ask zone2 to open channel with zone1
		OpenPeerChannelCommand openZoneChannelCommand = new OpenPeerChannelCommand(peerAmount, feeReserve, myAmount,
				getOfferCommand.getResponse().offer, getOfferCommand.getResponse().uri);

		openZoneChannelCommand.setTimeout(120000);
		myNode.execute("zone2", openZoneChannelCommand);
		
		Assert.assertNull(openZoneChannelCommand.getError());

		future.get(110, TimeUnit.SECONDS);
		// Can throw exception

		Assert.assertNull(openZoneChannelCommand.getError());
		Assert.assertNotNull(openZoneChannelCommand.getResponse());
		Assert.assertNotNull(openZoneChannelCommand.getResponse().zoneChannelId);

		return openZoneChannelCommand.getResponse().zoneChannelId;

	}

//	private void verifyChannelStatus(Connection connection, String zoneChannelId, EstablishedChannel.Status expected) {
//		GetChannelStatusCommand getChannelStatusCommand = new GetChannelStatusCommand(zoneChannelId);
//
//		connection.execute(getChannelStatusCommand);
//
//		Utils.prn(getChannelStatusCommand);
//
//		Assert.assertNull(getChannelStatusCommand.getError());
//		Assert.assertNotNull(getChannelStatusCommand.getResponse());
//
//		Assert.assertEquals(expected, getChannelStatusCommand.getResponse().status);
//	}
//
//	private void verifyChannelData(Connection connection, String zoneChannelId, String requireStatus) {
//		ListChannelsCommand listChannelsCommand = new ListChannelsCommand();
//
//		connection.execute(listChannelsCommand);
//
//		Utils.prn(listChannelsCommand);
//
//		Assert.assertNull(listChannelsCommand.getError());
//		Assert.assertNotNull(listChannelsCommand.getResponse());
//
//		Assert.assertEquals(requireStatus, listChannelsCommand.getResponse().channels.get(0).status);
//
//	}

//	private void channelChanged(Connection connection, Btc myAmount, Btc peerAmount, String state) {
//		ChannelChangedRequest req = new ChannelChangedRequest();
//		req.capacity = myAmount.add(peerAmount);
//		req.myAmount = Btc.fromMsat(0);
//		req.peerAmount = req.capacity;
//		req.fundingTxId = "fund_channel_txid";
//		req.peerAlias = "added_channel_peer_alias";
//		req.peerId = "peer_node_id_pus_other_chars_to_make_it_66_length_1234567890123456";
//		req.shortChannelId = "short:channel:id";
//		req.state = state;
//
//		ChannelChangedCommand channelChangedCommand = new ChannelChangedCommand(req);
//		connection.execute(channelChangedCommand);
//		Utils.prn(channelChangedCommand);
//		Assert.assertNull(channelChangedCommand.getError());
//		Assert.assertNotNull(channelChangedCommand.getResponse());
//	}
//
//	private void channelConfirmAndFundsTransfer(Connection zone1Connection, Connection zone2Connection,
//			FakeZoneNode zone1, String zoneChannelId, Btc myAmount, Btc peerAmount)
//			throws InterruptedException, ExecutionException {
//
//		verifyChannelStatus(zone1Connection, zoneChannelId, EstablishedChannel.Status.WAITING_FOR_CONFIRMATION);
//		verifyChannelStatus(zone2Connection, zoneChannelId, EstablishedChannel.Status.WAITING_FOR_CONFIRMATION);
//
//		verifyChannelData(zone2Connection, zoneChannelId, "not_funded_yet");
//		channelChanged(zone2Connection, myAmount, peerAmount, "CHANNELD_AWAITING_LOCKIN");
//		verifyChannelData(zone2Connection, zoneChannelId, "opening");
//		channelChanged(zone2Connection, myAmount, peerAmount, "CHANNELD_NORMAL");
//		verifyChannelData(zone2Connection, zoneChannelId, "normal");
//
//		Future<Void> future = executor.submit(() -> {
//			{ // Transfer funds from zone1 to zone2
//				SendPayCommand cmd = zone1.tester.waitFor(SendPayCommand.class);
//				Utils.prn(cmd);
//				cmd.response = new NoDataResponse();
//				zone1.tester.submit(cmd);
//			}
//			{ // Transfer funds from zone1 to zone2
//				WaitSendPayCommand cmd = zone1.tester.waitFor(WaitSendPayCommand.class);
//				Utils.prn(cmd);
//				cmd.response = new WaitSendPayResponse();
//				zone1.tester.submit(cmd);
//			}
//			return null;
//		});
//
//		verifyChannelData(zone1Connection, zoneChannelId, "not_funded_yet");
//		channelChanged(zone1Connection, myAmount, peerAmount, "CHANNELD_AWAITING_LOCKIN");
//		verifyChannelData(zone1Connection, zoneChannelId, "opening");
//		channelChanged(zone1Connection, myAmount, peerAmount, "CHANNELD_NORMAL");
//
//		future.get();
//	}
//
//	private ListChannelsResponse getListChannels(Connection connection) {
//		ListChannelsCommand listChannelsCommand = new ListChannelsCommand();
//
//		connection.execute(listChannelsCommand);
//
//		Utils.prn(listChannelsCommand);
//
//		Assert.assertNull(listChannelsCommand.getError());
//		Assert.assertNotNull(listChannelsCommand.getResponse());
//
//		return listChannelsCommand.getResponse();
//	}
//
//	private void increaseFeeDeposit(FakeZoneNode zone1, FakeZoneNode zone2, Connection zone1Connection,
//			Connection zone2Connection, String zoneChannelId) throws Exception {
//
//		Btc increaseAmount = Btc.fromSat(123);
//
//		Btc zone1BeforeFeeReserve = getListChannels(zone1Connection).channels.get(0).zoneChannelData.feeReserve;
//		Btc zone2BeforeFeeReserve = getListChannels(zone2Connection).channels.get(0).zoneChannelData.feeReserve;
//
//		Assert.assertEquals(zone1BeforeFeeReserve.toString(), zone2BeforeFeeReserve.toString());
//
//		Future<Void> future = executor.submit(() -> {
//			String label = null;
//			{ // Transfer funds from zone1 to zone2
//				InvoiceCommand cmd = zone1.tester.waitFor(InvoiceCommand.class);
//				Utils.prn(cmd);
//				label = cmd.getRequest().label;
//				cmd.response = new InvoiceResponse();
//				cmd.response.bolt11 = "bolt11_increase_fee_deposit_invoice";
//				zone1.tester.submit(cmd);
//			}
//			{ // Transfer funds from zone1 to zone2
//				PayCommand cmd = zone2.tester.waitFor(PayCommand.class);
//				Utils.prn(cmd);
//				cmd.response = new PayResponse();
//				cmd.response.payment_preimage = "payment_preimage";
//				zone2.tester.submit(cmd);
//			}
//			{ // clightning informs Zone1 that payment was received
//				PaymentReceivedRequest req = new PaymentReceivedRequest();
//				req.label = label;
//				req.amount_received = increaseAmount;
//				PaymentReceivedCommand paymentReceivedCommand = new PaymentReceivedCommand(req);
//				zone1Connection.execute(paymentReceivedCommand);
//			}
//			return null;
//		});
//
//		IncreaseZoneFeeDepositCommand increaseFeeDepositCommand = new IncreaseZoneFeeDepositCommand(zoneChannelId,
//				increaseAmount);
//		zone2Connection.execute(increaseFeeDepositCommand);
//		Utils.prn(increaseFeeDepositCommand);
//
//		future.get();
//
//		Thread.sleep(100);
//
//		Btc zone1AfterFeeReserve = getListChannels(zone1Connection).channels.get(0).zoneChannelData.feeReserve;
//		Btc zone2AfterFeeReserve = getListChannels(zone2Connection).channels.get(0).zoneChannelData.feeReserve;
//
//		Assert.assertEquals(zone1BeforeFeeReserve.add(increaseAmount).toString(), zone1AfterFeeReserve.toString());
//		Assert.assertEquals(zone2BeforeFeeReserve.add(increaseAmount).toString(), zone2AfterFeeReserve.toString());
//		Assert.assertEquals(zone1AfterFeeReserve.toString(), zone2AfterFeeReserve.toString());
//	}

//	private void rebalanceToOnchain(FakeZoneNode zone1, FakeZoneNode zone2, Connection zone1Connection,
//			Connection zone2Connection, String zoneChannelId) throws Exception {
//
//		Btc amount = Btc.fromSat(20);
//
//		Btc zone1myAmountBefore = getListChannels(zone1Connection).channels.get(0).zoneChannelData.myAmount;
//		Btc zone1peerAmountBefore = getListChannels(zone1Connection).channels.get(0).zoneChannelData.peerAmount;
//		Btc zone2myAmountBefore = getListChannels(zone2Connection).channels.get(0).zoneChannelData.myAmount;
//		Btc zone2peerAmountBefore = getListChannels(zone2Connection).channels.get(0).zoneChannelData.peerAmount;
//
//		Assert.assertEquals(zone1myAmountBefore.toString(), zone2peerAmountBefore.toString());
//		Assert.assertEquals(zone1peerAmountBefore.toString(), zone2myAmountBefore.toString());
//
//		Future<Void> future = executor.submit(() -> {
//			String label = null;
//			{ // Transfer funds from zone1 to zone2
//				NewAddrCommand cmd = zone2.tester.waitFor(NewAddrCommand.class);
//				Utils.prn(cmd);
//				cmd.response = new NewAddrResponse();
//				cmd.response.address = "addressForRebalanceToOnchain";
//				zone2.tester.submit(cmd);
//			}
//			{
//				InvoiceCommand cmd = zone1.tester.waitFor(InvoiceCommand.class);
//				Utils.prn(cmd);
//				label = cmd.getRequest().label;
//				cmd.response = new InvoiceResponse();
//				cmd.response.bolt11 = "bolt11_increase_fee_deposit_invoice";
//				zone1.tester.submit(cmd);
//			}
//			{ // Transfer funds from zone1 to zone2
//				DecodePayCommand cmd = zone2.tester.waitFor(DecodePayCommand.class);
//				Utils.prn(cmd);
//				cmd.response = new DecodePayResponse();
//				cmd.response.msatoshi = amount.toMsat();
//				cmd.response.payment_hash = "payment_hash";
//				zone2.tester.submit(cmd);
//			}
//			{ // Transfer funds from zone1 to zone2
//				SendPayCommand cmd = zone2.tester.waitFor(SendPayCommand.class);
//				Utils.prn(cmd);
//				cmd.response = new NoDataResponse();
//				zone2.tester.submit(cmd);
//			}
//			{ // Transfer funds from zone1 to zone2
//				WaitSendPayCommand cmd = zone2.tester.waitFor(WaitSendPayCommand.class);
//				Utils.prn(cmd);
//				cmd.response = new WaitSendPayResponse();
//				zone2.tester.submit(cmd);
//			}
//			{
//				ChannelChangedRequest req = new ChannelChangedRequest();
//				req.capacity = myAmount.add(peerAmount);
//				req.myAmount = Btc.fromMsat(0);
//				req.peerAmount = req.capacity;
//				req.fundingTxId = "fund_channel_txid";
//				req.peerAlias = "added_channel_peer_alias";
//				req.peerId = "peer_node_id_pus_other_chars_to_make_it_66_length_1234567890123456";
//				req.shortChannelId = "short:channel:id";
//				req.state = state;
//
//				ChannelChangedCommand channelChangedCommand = new ChannelChangedCommand(req);
//				connection.execute(channelChangedCommand);
//				Utils.prn(channelChangedCommand);
//				Assert.assertNull(channelChangedCommand.getError());
//				Assert.assertNotNull(channelChangedCommand.getResponse());
//			}
//
//			return null;
//		});
//
//		RebalanceToOnchainCommand rebalanceToOnchainCommand = new RebalanceToOnchainCommand(zoneChannelId, amount);
//		zone2Connection.execute(rebalanceToOnchainCommand);
//		Utils.prn(rebalanceToOnchainCommand);
//
//		future.get();
//
//		Thread.sleep(1000);
//
//		Btc zone1myAmountAfter = getListChannels(zone1Connection).channels.get(0).zoneChannelData.myAmount;
//		Btc zone1peerAmountAfter = getListChannels(zone1Connection).channels.get(0).zoneChannelData.peerAmount;
//		Btc zone2myAmountAfter = getListChannels(zone2Connection).channels.get(0).zoneChannelData.myAmount;
//		Btc zone2peerAmountAfter = getListChannels(zone2Connection).channels.get(0).zoneChannelData.peerAmount;
//
//		Assert.assertEquals(zone1myAmountAfter.toString(), zone2peerAmountAfter.toString());
//		Assert.assertEquals(zone1peerAmountAfter.toString(), zone2myAmountAfter.toString());
//	}

	public void testOpenZoneChannel() throws Exception {

		Btc peerAmount = new Btc("0.01");
		Btc feeReserve = new Btc("0.0002");
		Btc myAmount = new Btc("0.003");

		try (FakeZoneNode zone1 = new FakeZoneNode("zone_module_node", 1741, 1511);
				FakeZoneNode zone2 = new FakeZoneNode("zone_module_node", 1742, 1512);
				Node myNode = new Node("test-node")) {

			Connection zone1Connection = myNode.connect("zone1", "localhost", zone1.listeningPort);
			Connection zone2Connection = myNode.connect("zone2", "localhost", zone2.listeningPort);

			String zoneChannelId = openZoneChannel(myNode, zone1, zone2, myAmount, peerAmount, feeReserve);

//			channelConfirmAndFundsTransfer(zone1Connection, zone2Connection, zone1, zoneChannelId, myAmount,
//					peerAmount);
//
//			increaseFeeDeposit(zone1, zone2, zone1Connection, zone2Connection, zoneChannelId);
//
//			rebalanceToOnchain(zone1, zone2, zone1Connection, zone2Connection, zoneChannelId);
		}

		if (ExceptionManager.getInstance().getExceptionIds().size() != 0) {
			for (long id : ExceptionManager.getInstance().getExceptionIds()) {
				Utils.prn(ExceptionManager.getInstance().get(id));
			}
			Assert.fail("There are exceptions");
		}

	}

}
