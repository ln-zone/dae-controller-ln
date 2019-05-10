package bittech.dae.controller.zone;

import org.junit.Assert;

import bittech.dae.controller.zone.ZoneModule;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.exceptions.ExceptionManager;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.RawJson;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class ControllerIssuesTests extends TestCase {

	public ControllerIssuesTests(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(ControllerIssuesTests.class);
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

	}


	public void testNoControllerConnection() throws Exception {
		try {
			try (ZoneModule zm = new ZoneModule("zone_module_node" ,1746, 1546)) {
			}
			Assert.fail("No exception thrown");
		} catch (Exception ex) {
			Assert.assertEquals("Cannot create zone module", ex.getMessage());
			if (!ex.getCause().getMessage().contains("Cannot connect")) {
				ex.printStackTrace();
				Assert.fail("Wrong exception thrown");
			}
			// Fine, correct exception thrown
		}

		Assert.assertEquals(1, ExceptionManager.getInstance().getExceptionIds().size());
	}

	public void testNoCommandRegistered() throws Exception {

		try (Node node = new Node("controller", 1546)) {
			try (ZoneModule zm = new ZoneModule("zone_module_node" ,1746, 1546)) {
			}
			;
			Assert.fail("Exception not thrown");
		} catch (StoredException ex) {
			Assert.assertEquals("Cannot create zone module", ex.getMessage());
			Assert.assertEquals("Register command failed", ex.getCause().getMessage());
			Assert.assertEquals("Cannot process command bittech.lib.commands.ln.channels.RegisterChannelsListenerCommand",
					ex.getCause().getCause().getMessage());
			Assert.assertEquals(
					"This node has no listener for command class bittech.lib.commands.ln.channels.RegisterChannelsListenerCommand",
					ex.getCause().getCause().getCause().getMessage());
			// Fine, correct exception thrown
		}

		Assert.assertEquals(3, ExceptionManager.getInstance().getExceptionIds().size());

	}


}
