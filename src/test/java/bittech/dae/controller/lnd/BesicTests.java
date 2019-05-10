package bittech.dae.controller.lnd;

import bittech.dae.controller.ln.lnd.LndModule;
import bittech.dae.controller.ln.lnd.LndModuleConfig;
import bittech.lib.commands.ln.GetInfoCommand;
import bittech.lib.utils.Config;
import bittech.lib.utils.exceptions.ExceptionManager;
import bittech.lib.utils.json.JsonBuilder;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class BesicTests extends TestCase {

	public BesicTests(String testName) {
		super(testName);
	}

	protected void setUp() throws InterruptedException {
		Config.loadFromJson("{ \"connectionKeys\": { \"prv\":\"gcbacvacaeadadigbevimseg64gqcaibauaajaqbhyyieaj2aiaqaasbacbaugy3akyynoqfwjvgyhszfhwrr5rw4fcuvz6oryum7rmzopvvtm2qpozhkbxxi27kdpq56lppo3y5eocyyy2crl2xertlkcw5noexaibqcaabajaflplkb7hb7viqcqxymiorqtblgffrjkia7cyk7jjrwl43h3eyffajg3ihipadqbcula2qqrx2gjr435krwglqwtpqay4vagftwxhjqebccagljqylom77sghmi2b374qu2k6pfmodvlcijexya4j6jamzntyzk4bccafdyatpo5njxgcvsqjxhs5opn6ngloasjyrwzobq2djdcedkj4syebccaew4eu7vhpf3qriqfaqbmhk2fujp6tersk2gcxmo5oekkaggu52vmbca2qhzyzymghunqedgvsvdcqvupvauqq3sdu3bjpthhdzvimocgybaiqge44ip4yndekwk54cemfvejvxdromkq45dxj24ewmdruafnmn66y=\", \"pub\":\"gbodadigbevimseg64gqcaibauaagsyagbeaeqiaqifbwgycwgdlubnsnjwb4wjj5umpmnxbivfoptuofdh4lglt5nm3gud3wj2qn52gx2q34hps333w6hjdqwgggquk6vzem22qvxllrfycamaqaai\" }, \"saveLogs\":false }");
		ExceptionManager.getInstance().deleteAll();
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(BesicTests.class);
	}

	
	public class Configs {
		LndModuleConfig lnd1 = new LndModuleConfig();
		LndModuleConfig lnd2 = new LndModuleConfig();
	}
	
	public void addToConfig() throws InterruptedException {
		
		Configs configs = new Configs();

		configs.lnd1.moduleName = "Lnd1";
		configs.lnd1.listeningPort = 3972;
		configs.lnd1.rpcHost = "80.211.243.154";
		configs.lnd1.rpcPort = 10009;
		configs.lnd1.cert = "/home/cd/other/tls-lnd-tst-01.cert";
		
		System.out.println(JsonBuilder.build().toJson(configs.lnd1));
		
		
		configs.lnd2.moduleName = "Lnd2";
		configs.lnd2.listeningPort = 3973;
		configs.lnd2.rpcHost = "80.211.255.28";
		configs.lnd2.rpcPort = 10009;
		configs.lnd2.cert = "/home/cd/other/tls-lnd-tst-02.cert";
		
		Config.getInstance().addEntry("lnd1", configs.lnd1);
		Config.getInstance().addEntry("lnd2", configs.lnd2);
		
	}
	
	public void testBasic() throws InterruptedException {
		
		addToConfig();
		
		try (LndModule executor01 = new LndModule("lnd1"); // "Lnd1", 3972, "80.211.243.154", 10009, "/home/cd/other/tls-lnd-tst-01.cert");
				LndModule executor02 = new LndModule("lnd2") ) { //" Lnd2", 3973, "80.211.255.28", 10009, "/home/cd/other/tls-lnd-tst-02.cert")) {

			{
				GetInfoCommand cmd = new GetInfoCommand();
				executor02.execute(cmd);
				System.out.println(JsonBuilder.build().toJson(cmd));
			}

//			{
//				NewAddressCommand cmd = new NewAddressCommand();
//				executor.execute(cmd);
//				System.out.println(JsonBuilder.build().toJson(cmd));
//			}

//			{
//				WalletBalanceCommand cmd = new WalletBalanceCommand();
//				executor.execute(cmd);
//				System.out.println(JsonBuilder.build().toJson(cmd));
//			}

//			{
//				ListChainTxnsCommand cmd = new ListChainTxnsCommand();
//				executor.execute(cmd);
//				System.out.println(JsonBuilder.build().toJson(cmd));
//			}

//			{
//				ConnectCommand cmd = new ConnectCommand("036ae68d1fe7a8dcbdb39492759f6b7b985edc7852a230cc3172a4dbc8be3a5518@80.211.255.28:9735");
//				executor01.execute(cmd);
//				System.out.println(JsonBuilder.build().toJson(cmd));
//			}	

//			{
//				OpenChannelCommand cmd = new OpenChannelCommand(
//						"036ae68d1fe7a8dcbdb39492759f6b7b985edc7852a230cc3172a4dbc8be3a5518", Btc.fromSat(40000),
//						Btc.fromSat(0));
//				executor01.execute(cmd);
//				System.out.println(JsonBuilder.build().toJson(cmd));
//			}

//			{
//				AddInvoiceCommand cmd = new AddInvoiceCommand(Btc.fromSat(123), "platnosc");
//				executor02.execute(cmd);
//				System.out.println(JsonBuilder.build().toJson(cmd));
//			}

			Thread.sleep(1000000);

		}

	}

}
