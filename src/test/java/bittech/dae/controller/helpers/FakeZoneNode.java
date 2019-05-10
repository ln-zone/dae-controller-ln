package bittech.dae.controller.helpers;

import bittech.dae.controller.zone.ZoneModule;
import bittech.lib.protocol.Node;

public class FakeZoneNode implements AutoCloseable {
	
	public final Tester tester;
	private final Node controllerNode;
	
	public final ZoneModule zoneModule;
	
	public final int listeningPort;
	
	public FakeZoneNode(String name, int zoneListeningPort, int controllerPort) throws Exception {
		
		this.listeningPort = zoneListeningPort;
		
		tester = new Tester();

		controllerNode = new Node("controller", controllerPort);
		controllerNode.registerListener(tester);
		
		Thread initFeedThread = new SimpleThread(() -> Feed.forInit(tester, null));
		zoneModule = new ZoneModule(name, zoneListeningPort, controllerPort);
		initFeedThread.join();
	}

	@Override
	public void close() throws Exception {
		zoneModule.close();
		controllerNode.close();
	}
	
}
