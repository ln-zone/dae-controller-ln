package bittech.dae.controller.zone.channels;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.commands.ln.invoices.AddInvoiceRequest;
import bittech.lib.commands.ln.invoices.DecodeInvoiceResponse;
import bittech.lib.commands.lnzone.EstablishedChannel;
import bittech.lib.commands.lnzone.commans.Offer;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelRequest;
import bittech.lib.utils.Config;
import bittech.lib.utils.Notificator;
import bittech.lib.utils.Require;
import bittech.lib.utils.Utils;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;
import bittech.lib.utils.logs.Log;

public class ZoneChannels {

	private final static Logger LOGGER = LoggerFactory.getLogger(ZoneChannels.class);

	private final static String fileName = Config.getInstance().getEntryOrDefault("channelsFile", String.class,
			"/root/ln/channels.json");

	private final static boolean saveChannels = Config.getInstance().getEntryOrDefault("saveChannels", Boolean.class,
			true);

	public static class Data {
		private Offer offer;
		private List<ZoneChannel> channelsProposals = new LinkedList<ZoneChannel>();
	}

	private Data data = new Data();

	private Notificator<ZoneChannelChangeObserver> changeNotificator = new Notificator<ZoneChannelChangeObserver>();

	public ZoneChannels() {
		if (saveChannels) {
			load();
			addChangeObserver(new ZoneChannelChangeObserver() {

				@Override
				public void onZoneChannelChanged(EstablishedChannel zoneEstablishedChannel) {
					save();
				}
				
			});
		}
	}

	private synchronized void save() throws StoredException {
		try {
			try (PrintWriter out = new PrintWriter(fileName)) {
				JsonBuilder.build().toJson(data, out);
			}
		} catch (Exception ex) {
			throw new StoredException("Cannot save channels", ex);
		}
	}

	private synchronized void load() {
		try {
			File file = new File(fileName);
			LOGGER.debug("Loading channels");
			if (file.exists()) {
				LOGGER.debug("File exists");
				try (FileReader reader = new FileReader(file)) {
					data = JsonBuilder.build().fromJson(reader, Data.class);
				}
				LOGGER.info("Loaded channels: " + data.channelsProposals.size());
			}
		} catch (Exception ex) {
			new StoredException("Cannot load channels", ex);
		}
	}

	public synchronized void addChangeObserver(ZoneChannelChangeObserver chageObserver) {
		changeNotificator.register(chageObserver);
	}

	public synchronized ZoneChannel newChannel(OpenZoneChannelRequest openChannelRequest, AddInvoiceRequest invoiceRequest,
			DecodeInvoiceResponse decodedInvoice) {
		ZoneChannel ch = new ZoneChannel(true);
		ch.setOpenChannelRequest(Require.notNull(openChannelRequest, "openChannelRequest"));
		ch.setInvoiceRequest(Require.notNull(invoiceRequest, "invoiceRequest"));
		ch.setDecodedInvoice(Require.notNull(decodedInvoice, "decodedInvoice"));
		ch.establishedChannel.status = bittech.lib.commands.lnzone.EstablishedChannel.Status.WAITING_FOR_PAYMENT;
		put(ch);
		return ch;
	}

	public synchronized ZoneChannel newChannelNoOwner() {
		ZoneChannel ch = new ZoneChannel(false);
		put(ch);
		return ch;
	}

	public synchronized ZoneChannel channelPaid(String invoiceLabel) {
		Log log = new Log().param("invoiceLabel", invoiceLabel);
		try {
			log.event("channelPaid called");
			for (ZoneChannel ch : data.channelsProposals) {
				if ((ch.helperData.invoiceRequest != null) && invoiceLabel.equals(ch.helperData.invoiceRequest.label)) {
					ch.establishedChannel.status = bittech.lib.commands.lnzone.EstablishedChannel.Status.FUNDING;
					return ch;
				}
			}
		} catch (Exception ex) {
			log.param("exception", ex).event("Exception thrown");
			throw new StoredException("Cannot mark channelPaid", ex);
		}
		return null;
	}

	public synchronized List<ZoneChannel> copyProposalsChannels() {
		return new LinkedList<ZoneChannel>(data.channelsProposals);
	}

	public synchronized List<EstablishedChannel> copyEstablishedChannels() {
		List<EstablishedChannel> list = new LinkedList<EstablishedChannel>();
		for (ZoneChannel channel : data.channelsProposals) {
			list.add(Utils.deepCopy(channel.establishedChannel, EstablishedChannel.class));
		}
		return list;
	}

	public synchronized Offer getOffer() {
		if (data.offer == null) {
			return null;
		} else {
			return Utils.deepCopy(data.offer, data.offer.getClass());
		}
	}

	public synchronized void setOffer(final Offer offer) {

		try {
		Require.notNull(offer, "offer");

		if (offer.feeBase == null || offer.feeBase.hasValue() == false) {
			throw new StoredException("Fee base not specified for offer", null);
		}

		if (offer.feeBase.toMsat() < 0) {
			throw new StoredException("Fee base cannot be negative value", null);
		}
		
		if (offer.feePerSatoshi == null || offer.feePerSatoshi.hasValue() == false) {
			throw new StoredException("Fee per satoshi not specified for offer", null);
		}

		if (offer.feePerSatoshi.toMsat() < 0) {
			throw new StoredException("Fee per satoshi cannot be negative value", null);
		}
		
		if (offer.fixedCost == null || offer.fixedCost.hasValue() == false) {
			throw new StoredException("Fixed cost not specified for offer", null);
		}

		if (offer.fixedCost.toMsat() < 0) {
			throw new StoredException("Fixed cost cannot be negative value", null);
		}

		if (offer.fundsAllocationFee == null) {
			throw new StoredException("Funds allocation fee not specified in offer", null);
		}

		if (offer.fundsAllocationFee.compareTo(BigDecimal.ZERO) < 0) {
			throw new StoredException("Fixed cost cannot be negative value", null);
		}

		data.offer = Utils.deepCopy(offer, offer.getClass());

		} catch(Exception ex) {
			throw new StoredException("Cannot set new offer", ex);
		}
	}

	public synchronized ZoneChannel findByTxId(String txId) {
		Require.notNull(txId, "txId");
		for (ZoneChannel channel : data.channelsProposals) {
			if (txId.equals(channel.establishedChannel.fundingTxId)) {
				return channel;
			}
		}
		return null;
	}

	public synchronized ZoneChannel findZoneChannelById(String zoneChannelId) {
		Require.notNull(zoneChannelId, "zoneChannelId");
		for (ZoneChannel channel : data.channelsProposals) {
			if (zoneChannelId.equals(channel.establishedChannel.zoneChannelId)) {
				return channel;
			}
		}
		return null;
	}

	private void removeByZoneChannelId(String zoneChannelId) {
		Require.notNull(zoneChannelId, "zoneChannelId");
		Iterator<ZoneChannel> i = data.channelsProposals.iterator();
		while (i.hasNext()) {
			if (zoneChannelId.equals(i.next().establishedChannel.zoneChannelId)) {
				i.remove();
				break;
			}
		}
	}

	private void put(ZoneChannel channel) {
		removeByZoneChannelId(channel.establishedChannel.zoneChannelId);
		data.channelsProposals.add(Utils.deepCopy(channel, ZoneChannel.class));
		changeNotificator.notifyThem((m) -> m.onZoneChannelChanged(channel.establishedChannel));
	}
	
	public void replace(String tmpZoneChannelId, ZoneChannel channel) {
		removeByZoneChannelId(tmpZoneChannelId);
		channel.establishedChannel.calcFields();
		put(channel);
	}
	
	public synchronized void update(ZoneChannel channel) {
		removeByZoneChannelId(channel.establishedChannel.zoneChannelId);
		channel.establishedChannel.calcFields();
		put(channel);
	}
}
