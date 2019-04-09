package bittech.dae.controller.ln;

import bittech.dae.controller.ln.lnd.LndModule;
import bittech.lib.utils.Config;
import btcduke.dea.node.controller.zone.ZoneModule;

public class App {

//	private final static Logger LOGGER = LoggerFactory.getLogger(App.class);

	public App() {
	}

	public static void main(String[] args) throws Exception {
//		LOGGER.info("V. 0.2");
//		String myName;
//		String rpcFilePath;
//		if (args.length != 2) {
//			myName = "dea-local";
//			rpcFilePath = "/home/cd/.lightning/lightning-rpc";
//		} else {
//			myName = args[0];
//			rpcFilePath = args[1];
//		}
//		
//		int controllerPort = 1546; 
//		int zonePort = 1746;
		
		
		try(LndModule lndModule = new LndModule("lnd")) {
			

//		try (ControllerModule controllerModule = new ControllerModule(myName, rpcFilePath, controllerPort)) {
//			LOGGER.info(myName + " started on port " + controllerPort);

			ZoneModule.initIfEnabled(lndModule.getConfig().listeningPort);
			
//			if() {
//				
//			}
//			
//			Boolean zoneModuleEnabled = Config.getInstance().getEntry("zoneModule", Boolean.class, false);
//			if (zoneModuleEnabled != null && zoneModuleEnabled.booleanValue() == true) {
//				new ZoneModule(myName + "_zone", zonePort, controllerPort);
//				LOGGER.info("Zone module started");
//			} else {
//				LOGGER.info(
//						"Zone module  not supported! To switch on, add \"zoneModule\":true to config.json and restart service");
//			}

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
