package bittech.dae.controller.ln.fastpay;

import java.util.LinkedList;
import java.util.List;

import bittech.lib.utils.Btc;
import bittech.lib.utils.Require;

public class Route {
	
	static class GraphChannel implements Comparable<GraphChannel> {
		public long id;
		public long minToTransfer;
		public long maxToTransfer;
		public long feeBaseMsat;
		public long feeRateMilliMsat;
		public String peerId;
		
		@Override
		public int compareTo(GraphChannel o) {
			return -Long.compare(this.maxToTransfer,  o.maxToTransfer);
		}
		
	}

	public static class GraphNode {
		public String id;
		public String alias;
		public List<GraphChannel> channels = new LinkedList<GraphChannel>();
	}
	
	public String firstNodId;
	public List<GraphChannel> hops = new LinkedList<GraphChannel>();
	public Btc amount;
	
	public void add(GraphChannel channel) {
		hops.add(0, channel);
	}
	
	public Route(Btc amount) {
		this.amount = Require.notNull(amount, "amount");
	}

}
