package bittech.dae.controller.ln;

import bittech.dae.controller.ln.lnd.LndModule;
import bittech.dae.controller.zone.ZoneModule;

public class App {

	public App() {
	}

	public static void main(String[] args) throws Exception {

		try (LndModule lndModule = new LndModule("lnd")) {

			ZoneModule.initIfEnabled(lndModule.getConfig().listeningPort);

//			Boolean walletModuleEnabled = Config.getInstance().getEntry("walletModule", Boolean.class, false);
//			if (walletModuleEnabled != null && walletModuleEnabled.booleanValue() == true) {
//				WalletModule wm = new WalletModule(/*lnNodeDetailsListener, */myName + "_wallet",
//						myName + "_zone", controllerPort, zonePort);
//				LOGGER.info("Wallet module started");
//			} else {
//				LOGGER.info(
//						"Wallet module not supported! To switch on, add \"walletModule\":true to config.json and restart service");
//			}

			Thread.sleep(10000000000L);
		}

	}

}
