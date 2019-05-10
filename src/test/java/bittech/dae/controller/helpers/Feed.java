package bittech.dae.controller.helpers;

import java.util.ArrayList;

import bittech.lib.commands.ln.channels.RegisterChannelsListenerCommand;
import bittech.lib.commands.ln.invoices.RegisterPaymentsListenerCommand;
import bittech.lib.commands.ln.peers.ListPeersCommand;
import bittech.lib.commands.ln.peers.ListPeersResponse;
import bittech.lib.commands.ln.peers.ListPeersResponse.Peer;
import bittech.lib.protocol.common.NoDataResponse;

public class Feed {

	public static void forInit(Tester tester,
			ListPeersResponse listPeersResponse) {
		{
			RegisterChannelsListenerCommand cmd = tester.waitFor(RegisterChannelsListenerCommand.class);
			cmd.response = new NoDataResponse();
			tester.submit(cmd);
		}
//		{
//			ListConfigsCommand cmd = tester.waitFor(ListConfigsCommand.class);
//			if (listConfigsResponse == null) {
//				cmd.response = new ListConfigsResponse();
//				cmd.response.feeBase = 10;
//				cmd.response.feePerSatoshi = 1;
//			} else {
//				cmd.response = listConfigsResponse;
//			}
//			tester.submit(cmd);
//		}
		{
			ListPeersCommand cmd = tester.waitFor(ListPeersCommand.class);
			if (listPeersResponse == null) {
				cmd.response = new ListPeersResponse();
				cmd.response.peers = new ArrayList<Peer>();
			} else {
				cmd.response = listPeersResponse;
			}
			tester.submit(cmd);
		}
		{
			RegisterPaymentsListenerCommand cmd = tester.waitFor(RegisterPaymentsListenerCommand.class);
			cmd.response = new NoDataResponse();
			tester.submit(cmd);
		}
		{
			RegisterPaymentsListenerCommand cmd = tester.waitFor(RegisterPaymentsListenerCommand.class);
			cmd.response = new NoDataResponse();
			tester.submit(cmd);
		}
	}

}
