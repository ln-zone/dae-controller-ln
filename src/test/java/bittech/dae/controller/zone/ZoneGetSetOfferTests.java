package bittech.dae.controller.zone;

import java.math.BigDecimal;

import org.junit.Assert;

import bittech.dae.controller.helpers.FakeZoneNode;
import bittech.dae.controller.helpers.Feed;
import bittech.dae.controller.helpers.SimpleThread;
import bittech.dae.controller.helpers.Tester;
import bittech.dae.controller.zone.ZoneModule;
import bittech.lib.commands.lnzone.commans.Offer;
import bittech.lib.commands.lnzone.internal.GetOfferCommand;
import bittech.lib.commands.lnzone.internal.SetOfferCommand;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.ExceptionManager;
import bittech.lib.utils.json.RawJson;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class ZoneGetSetOfferTests extends TestCase {

	private Tester tester;
	private Node controllerNode;

	public ZoneGetSetOfferTests(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(ZoneGetSetOfferTests.class);
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

		tester = new Tester();
		controllerNode = new Node("controller", 1546);
		controllerNode.registerListener(tester);
	}

	protected void tearDown() {
		controllerNode.close();
	}

	private Offer getOffer(Connection connection) {
		GetOfferCommand getOfferCommand = new GetOfferCommand();
		connection.execute(getOfferCommand);

		Assert.assertNull(getOfferCommand.getError());
		Assert.assertNotNull(getOfferCommand.getResponse());

		return getOfferCommand.getResponse().offer;
	}

	public void testInitOnly() throws Exception {

		try (FakeZoneNode zoneModule = new FakeZoneNode("zone_module_node", 1741, 1511)) {

		}

		Assert.assertEquals(0, ExceptionManager.getInstance().getExceptionIds().size());

	}

	public void testDefaultOffer() throws Exception {

		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, null));

		try (ZoneModule zoneModule = new ZoneModule("zone_module_node", 1746, 1546);
				Node myNode = new Node("test-node")) {
			initFeedThread.join();

			Connection connection = myNode.connect("zone1", "localhost", zoneModule.getListeningPort());

			GetOfferCommand getOfferCommand = new GetOfferCommand();
			connection.execute(getOfferCommand);

			Assert.assertNull(getOfferCommand.getError());
			Assert.assertNotNull(getOfferCommand.getResponse());

			Assert.assertTrue(getOfferCommand.getResponse().uri.matches("(^zone:).+:" + 1746));

			Offer offer = new Offer();
			offer.feeBase = new Btc("1");
			offer.feePerSatoshi = new Btc("0:001");
			offer.fundsAllocationFee = new BigDecimal("0.1");
			offer.fixedCost = Btc.fromSat(100);
			
			Assert.assertTrue(Utils.deepEquals(offer, getOfferCommand.getResponse().offer));

		}

		Assert.assertEquals(0, ExceptionManager.getInstance().getExceptionIds().size());

	}
//
//	public void testChangeFeeBase() throws Exception {
//
//		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, null));
//
//		try (ZoneModule zoneModule = new ZoneModule("zone_module_node", 1746, 1546);
//				Node myNode = new Node("test-node")) {
//			initFeedThread.join();
//
//			Connection connection = myNode.connect("zone1", "localhost", zoneModule.getListeningPort());
//
//			Offer beforeError = getOffer(connection);
//
//			{ // Set offer
//				Offer offer = new Offer();
//				offer.feeBase = Btc.fromMsat(1);
//				offer.fundsAllocationFee = new BigDecimal("0.2");
//				offer.fixedCost = Btc.fromMsat(123456789);
//				SetOfferCommand setOfferCommand = new SetOfferCommand(offer);
//				connection.execute(setOfferCommand);
//
//				Assert.assertNull(setOfferCommand.getResponse());
//				Assert.assertNotNull(setOfferCommand.getError());
//
//				Assert.assertEquals("Cannot process command btcduke.lib.lnzone.commans.SetOfferCommand",
//						setOfferCommand.getError().message);
//				Assert.assertEquals("Cannot set new offer", setOfferCommand.getError().cause.message);
//				Assert.assertEquals("Fee base cannot be changed. This is setup in clightning configuration",
//						setOfferCommand.getError().cause.cause.message);
//			}
//
//			Offer afterError = getOffer(connection);
//			Assert.assertTrue(Utils.deepEquals(beforeError, afterError));
//		}
//
//		Assert.assertEquals(1, ExceptionManager.getInstance().getExceptionIds().size());
//	}

//
//	public void testChangeFeePerSatoshi() throws Exception {
//
//		ListConfigsResponse listConfigsResponse = new ListConfigsResponse();
//		listConfigsResponse.feeBase = 123;
//		listConfigsResponse.feePerSatoshi = 2;
//
//		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, listConfigsResponse, null));
//
//		try (ZoneModule zoneModule = new ZoneModule("zone_module_node", 1746, 1546);
//				Node myNode = new Node("test-node")) {
//			initFeedThread.join();
//
//			Connection connection = myNode.connect("zone1", "localhost", zoneModule.getListeningPort());
//
//			Offer beforeError = getOffer(connection);
//
//			{ // Set offer
//				Offer offer = new Offer();
//				offer.feeBase = Btc.fromMsat(123);
//				offer.feePerSatoshi = Btc.fromMsat(1);
//				SetOfferCommand setOfferCommand = new SetOfferCommand(offer);
//				connection.execute(setOfferCommand);
//
//				Assert.assertNull(setOfferCommand.getResponse());
//				Assert.assertNotNull(setOfferCommand.getError());
//
//				Assert.assertEquals("Cannot process command btcduke.lib.lnzone.commans.SetOfferCommand",
//						setOfferCommand.getError().message);
//				Assert.assertEquals("Cannot set new offer", setOfferCommand.getError().cause.message);
//				Assert.assertEquals("Fee per satoshi cannot be changed. This is setup in clightning configuration",
//						setOfferCommand.getError().cause.cause.message);
//			}
//
//			Offer afterError = getOffer(connection);
//			Assert.assertTrue(Utils.deepEquals(beforeError, afterError));
//		}
//		Assert.assertEquals(1, ExceptionManager.getInstance().getExceptionIds().size());
//	}
//
	public void testNoFundsAllocation() throws Exception {

		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, null));

		try (ZoneModule zoneModule = new ZoneModule("zone_module_node", 1746, 1546);
				Node myNode = new Node("test-node")) {
			initFeedThread.join();

			Connection connection = myNode.connect("zone1", "localhost", zoneModule.getListeningPort());

			Offer beforeError = getOffer(connection);

			{ // Set offer
				Offer offer = new Offer();
				offer.feeBase = new Btc("13");
				offer.feePerSatoshi = new Btc("2");
				offer.fixedCost = new Btc("456");
				SetOfferCommand setOfferCommand = new SetOfferCommand(offer);
				connection.execute(setOfferCommand);

				Assert.assertNull(setOfferCommand.getResponse());
				Assert.assertNotNull(setOfferCommand.getError());

				Assert.assertEquals("Cannot process command bittech.lib.commands.lnzone.internal.SetOfferCommand",
						setOfferCommand.getError().message);
				Assert.assertEquals("Cannot set new offer", setOfferCommand.getError().cause.message);
				Assert.assertEquals("Funds allocation fee not specified in offer",
						setOfferCommand.getError().cause.cause.message);
			}

			Offer afterError = getOffer(connection);
			Assert.assertTrue(Utils.deepEquals(beforeError, afterError));
		}
		Assert.assertEquals(1, ExceptionManager.getInstance().getExceptionIds().size());
	}
//
//	public void testNoFixedCost() throws Exception {
//
//		ListConfigsResponse listConfigsResponse = new ListConfigsResponse();
//		listConfigsResponse.feeBase = 13;
//		listConfigsResponse.feePerSatoshi = 2;
//
//		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, listConfigsResponse, null));
//
//		try (ZoneModule zoneModule = new ZoneModule("zone_module_node", 1746, 1546);
//				Node myNode = new Node("test-node")) {
//			initFeedThread.join();
//
//			Connection connection = myNode.connect("zone1", "localhost", zoneModule.getListeningPort());
//
//			Offer beforeError = getOffer(connection);
//
//			{ // Set offer
//				Offer offer = new Offer();
//				offer.fundsAllocationFee = new BigDecimal("0.2");
//				SetOfferCommand setOfferCommand = new SetOfferCommand(offer);
//				connection.execute(setOfferCommand);
//
//				Assert.assertNull(setOfferCommand.getResponse());
//				Assert.assertNotNull(setOfferCommand.getError());
//
//				Assert.assertEquals("Cannot process command btcduke.lib.lnzone.commans.SetOfferCommand",
//						setOfferCommand.getError().message);
//				Assert.assertEquals("Cannot set new offer", setOfferCommand.getError().cause.message);
//				Assert.assertEquals("Fixed cost not specified for offer",
//						setOfferCommand.getError().cause.cause.message);
//			}
//
//			Offer afterError = getOffer(connection);
//			Assert.assertTrue(Utils.deepEquals(beforeError, afterError));
//		}
//		Assert.assertEquals(1, ExceptionManager.getInstance().getExceptionIds().size());
//	}
//
	public void testSetOfferProperly1() throws Exception {

		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, null));

		try (ZoneModule zoneModule = new ZoneModule("zone_module_node", 1746, 1546);
				Node myNode = new Node("test-node")) {
			initFeedThread.join();

			Connection connection = myNode.connect("zone1", "localhost", zoneModule.getListeningPort());

			Offer offer = new Offer();
			offer.feeBase = new Btc("1");
			offer.feePerSatoshi = new Btc("0:001");
			offer.fundsAllocationFee = new BigDecimal("0.2");
			offer.fixedCost = Btc.fromMsat(123456789);

			{ // Set offer
				SetOfferCommand setOfferCommand = new SetOfferCommand(offer);
				connection.execute(setOfferCommand);

				Assert.assertNull(setOfferCommand.getError());
				Assert.assertNotNull(setOfferCommand.getResponse());
			}

			{ // Get offer
				GetOfferCommand getOfferCommand = new GetOfferCommand();
				connection.execute(getOfferCommand);

				Assert.assertNull(getOfferCommand.getError());
				Assert.assertNotNull(getOfferCommand.getResponse());

				Assert.assertTrue(Utils.deepEquals(offer, getOfferCommand.getResponse().offer));

			}

		}
		Assert.assertEquals(0, ExceptionManager.getInstance().getExceptionIds().size());
	}
//
//	public void testSetOfferProperly2() throws Exception {
//
//		ListConfigsResponse listConfigsResponse = new ListConfigsResponse();
//		listConfigsResponse.feeBase = 123;
//		listConfigsResponse.feePerSatoshi = 9;
//
//		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, listConfigsResponse, null));
//
//		try (ZoneModule zoneModule = new ZoneModule("zone_module_node", 1746, 1546);
//				Node myNode = new Node("test-node")) {
//			initFeedThread.join();
//
//			Connection connection = myNode.connect("zone1", "localhost", zoneModule.getListeningPort());
//
//			{ // Set offer
//				Offer offer = new Offer();
//				offer.feeBase = Btc.fromMsat(123);
//				offer.feePerSatoshi = Btc.fromMsat(9);
//				offer.fundsAllocationFee = new BigDecimal("0.2");
//				offer.fixedCost = Btc.fromMsat(123456789);
//				SetOfferCommand setOfferCommand = new SetOfferCommand(offer);
//				connection.execute(setOfferCommand);
//
//				Assert.assertNull(setOfferCommand.getError());
//				Assert.assertNotNull(setOfferCommand.getResponse());
//			}
//
//			{ // Get offer
//				GetOfferCommand getOfferCommand = new GetOfferCommand();
//				connection.execute(getOfferCommand);
//
//				Assert.assertNull(getOfferCommand.getError());
//				Assert.assertNotNull(getOfferCommand.getResponse());
//
//				Assert.assertTrue(
//						getOfferCommand.getResponse().uri.matches("(^zone:).+:" + zoneModule.getListeningPort()));
//
//				Assert.assertEquals(listConfigsResponse.feeBase, getOfferCommand.getResponse().offer.feeBase.toMsat());
//
//				Assert.assertEquals(listConfigsResponse.feePerSatoshi,
//						getOfferCommand.getResponse().offer.feePerSatoshi.toMsat());
//
//				Assert.assertEquals("0.2", getOfferCommand.getResponse().offer.fundsAllocationFee.toPlainString());
//
//				Assert.assertEquals(123456789, getOfferCommand.getResponse().offer.fixedCost.toMsat());
//			}
//		}
//
//		Assert.assertEquals(0, ExceptionManager.getInstance().getExceptionIds().size());
//	}

}
