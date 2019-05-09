package bittech.dae.controller.zone;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.dae.controller.zone.channels.ChannelsListener;
import bittech.dae.controller.zone.channels.ClientZoneListener;
import bittech.dae.controller.zone.channels.CompoundChannels;
import bittech.dae.controller.zone.channels.CompoundChannelsListener;
import bittech.dae.controller.zone.channels.StandardChannelChangedListener;
import bittech.dae.controller.zone.channels.ZoneChannel;
import bittech.dae.controller.zone.channels.ZoneChannels;
import bittech.lib.commands.lnzone.EstablishedChannel;
import bittech.lib.commands.lnzone.commans.Offer;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelRequest;
import bittech.lib.manager.ManagerDataProvider;
import bittech.lib.manager.ManagerModule;
import bittech.lib.manager.commands.GetNodeDetailsResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.Node;
import bittech.lib.utils.Config;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class ZoneModule implements ManagerDataProvider, AutoCloseable {

	@SuppressWarnings("unused")
	private final static Logger LOGGER = LoggerFactory.getLogger(ZoneModule.class);

	private final int listeningPort;

	private Connection controllerConnection;

	private OfferListener offerListener;
	private ChannelsListener channelsListener;
	private PaymentReceivedListener paymentReceivedListener;
	private ZoneChannelsListener zoneChannelsListener;
	private ClientZoneListener zoneListener;
	private FeeListener feeListener;
	private ToOnchainListener refillListener;
	private StandardChannelChangedListener standardChannelChangedListener;

	private ToOnchainDataProvider refillDataProvider;

	private Node node;

	private ZoneChannels zoneChannels;
	private CompoundChannels allChannels;
	private CompoundChannelsListener allChannelsListener;
	private OpenZoneChannelWorker openZoneChannelWorker;

	
	public static ZoneModule initIfEnabled(int controllerPort) {
		ZoneConfig conf = Config.getInstance().getEntryOrDefault("zone", ZoneConfig.class, null);
		if(conf == null) {
			return null;
		}
		if(conf.enabled == false) {
			return null;
		}
		System.out.println("-------------- Starting zone module");
		return new ZoneModule(conf.name, conf.listeningPort, controllerPort);
	}
		
	
	public ZoneModule(String myName, int zoneListeningPort, int controllerPort) {
		this.listeningPort = Require.inRange(zoneListeningPort, 1, Short.MAX_VALUE, "zoneListeningPort");
		init(myName, controllerPort);
	}

	private void init(String myName, int controllerPort) {
		try {
			node = new Node(myName, getListeningPort());

			this.controllerConnection = node.connect("controller-abc", "localhost", controllerPort);
						
			this.zoneChannels = new ZoneChannels();
			this.allChannels = new CompoundChannels();
			this.zoneChannels.addChangeObserver(this.allChannels);

			openZoneChannelWorker = new OpenZoneChannelWorker(zoneChannels, controllerConnection);

			createListeners();
			apply(node);
			start();

			boolean connectToManager = Config.getInstance().getEntryOrDefault("connectZoneToManager", Boolean.class,
					true);
			if (connectToManager) {
				ManagerModule managerNotifier = new ManagerModule(node, myName);
				managerNotifier.addDetailsProvider(this);
				managerNotifier.start();
			}
		} catch (Exception ex) {
			try {
				close();
			} catch (Exception e) {
				new StoredException("Cannot close zone module", e);
			}
			throw new StoredException("Cannot create zone module", ex);
		}
	}

	private void createListeners() {

		this.standardChannelChangedListener = new StandardChannelChangedListener(this.controllerConnection);

		this.offerListener = new OfferListener(this.zoneChannels, this.listeningPort);
		this.zoneChannelsListener = new ZoneChannelsListener(this.zoneChannels);
		this.zoneListener = new ClientZoneListener(this.zoneChannels, this.controllerConnection);
		this.feeListener = new FeeListener(this.zoneChannels, this.controllerConnection);
		this.refillListener = new ToOnchainListener(this.zoneChannels, this.controllerConnection, allChannels);
		this.allChannelsListener = new CompoundChannelsListener(zoneChannels, allChannels, node, controllerConnection);

		this.standardChannelChangedListener.registerStandardChannelChangeObserver(this.allChannels);
		this.standardChannelChangedListener.registerStandardChannelChangeObserver(this.openZoneChannelWorker);

		this.channelsListener = new ChannelsListener(this.zoneChannels, this.controllerConnection) {

			@Override
			public ZoneChannel onOpenChannelRequest(OpenZoneChannelRequest request) {
				return openZoneChannelWorker.onOpenChannel(request);
			}

			@Override
			public void responseSent(String serviceName, Command<?, ?> command) {
				// Nothing here
			}

		};

		this.paymentReceivedListener = new PaymentReceivedListener(this.controllerConnection);
		this.paymentReceivedListener.registerPaymentReceivedObserver(openZoneChannelWorker);
		this.paymentReceivedListener.registerPaymentReceivedObserver(feeListener);
		this.paymentReceivedListener.registerPaymentReceivedObserver(refillListener);

		refillDataProvider = new ToOnchainDataProvider(refillListener, this);
		new Log().param("module", "zone").event("Zone module init");
	}

	public void apply(Node node) {
		node.registerListener(offerListener);
		node.registerListener(allChannelsListener);
		node.registerListener(channelsListener);
		node.registerListener(paymentReceivedListener);
		node.registerListener(zoneChannelsListener);
		node.registerListener(feeListener);
		node.registerListener(zoneListener);
		node.registerListener(refillListener);
		new Log().param("module", "zone").event("Listeners registered");
	}

	public void start() {
		this.paymentReceivedListener.start();
		this.refillListener.start();
		new Log().param("module", "zone").event("Zone module started");
	}

	public Offer getOffer() {
		return zoneChannels.getOffer();
	}

	@Override
	public synchronized void addCustomData(GetNodeDetailsResponse details) {

		details.summary.put("uri", offerListener.getMyUri());

		List<ZoneChannel> proposalsChannels = zoneChannels.copyProposalsChannels();
		details.details.put("proposals channels", proposalsChannels);
		details.summary.put("proposals channels", Integer.toString(proposalsChannels.size()));

		List<EstablishedChannel> establishedChannels = zoneChannels.copyEstablishedChannels();
		details.details.put("established channels", establishedChannels);
		details.summary.put("established channels", Integer.toString(establishedChannels.size()));

		refillDataProvider.addCustomData(details);
	}

	@Override
	public void close() throws Exception {
		if (node != null) {
			node.close();
		}
	}

	public int getListeningPort() {
		return listeningPort;
	}

}
