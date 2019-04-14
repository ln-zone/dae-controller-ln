package bittech.dae.controller.zone;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import bittech.lib.commands.ln.invoices.GetPaymentStatusResponse.InvoiceStatus;
import bittech.lib.utils.Btc;
import bittech.lib.utils.Config;
import bittech.lib.utils.FormattedTime;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;

public class Invoices {

	private final static Logger LOGGER = LoggerFactory.getLogger(Invoices.class);

	private final static String fileName = Config.getInstance().getEntryOrDefault("invoicesFile", String.class,
			"/root/ln/invoices.json");

	private final static boolean saveInvoices = Config.getInstance().getEntryOrDefault("saveInvoices", Boolean.class,
			true);

	public static final class Invoice {
		String label;
		InvoiceStatus status;
		String zoneChannelId;
		String addr;
		Btc amount;
		String bolt_11;
		FormattedTime created_at;
		FormattedTime paid_at;
		FormattedTime refund_at;

		public Invoice copy() {
			Invoice inv = new Invoice();
			inv.label = label;
			inv.zoneChannelId = zoneChannelId;
			inv.status = status;
			inv.bolt_11 = bolt_11;
			inv.created_at = created_at;
			inv.paid_at = paid_at;
			inv.refund_at = refund_at;
			return inv;
		}
	}

	private final Map<String, Invoice> invoices = new ConcurrentHashMap<String, Invoice>(); // label,

	public Invoices() {
		if (saveInvoices) {
			load();
			new SavingThread().start();
		}
	}

	private synchronized void save() throws StoredException {
		try {
			try (PrintWriter out = new PrintWriter(fileName)) {
				JsonBuilder.build().toJson(invoices, out);
			}
		} catch (Exception ex) {
			throw new StoredException("Cannot save invoices", ex);
		}
	}

	private synchronized void load() {
		try {
			File file = new File(fileName);
			LOGGER.debug("Loading invoices");
			if (file.exists()) {
				LOGGER.debug("File exists");
				try (FileReader reader = new FileReader(file)) {
					Type mapType = new TypeToken<Map<String, Invoice>>() {
					}.getType();
					invoices.clear();
					invoices.putAll(JsonBuilder.build().fromJson(reader, mapType));
				}
				LOGGER.info("Loaded invoices: " + invoices.size());
			}
		} catch (Exception ex) {
			new StoredException("Cannot load invoices", ex);
		}
	}

	public synchronized void add(Invoice invoice) {
		Require.notNull(invoice, "invoice");
		if (invoices.get(invoice.label) != null) {
			throw new StoredException("Cannot add invoice.",
					new StoredException("Invoice with label '" + invoice.label + "' was added before", null));
		}
		invoices.put(invoice.label, invoice);
	}

	public synchronized boolean contains(String label) {
		return invoices.containsKey(label);
	}

	public synchronized Invoice getByLabel(String label) {
		return invoices.get(label);
	}

	public synchronized Invoice getByRequestId(String requestId) {
		for (Invoice inv : invoices.values()) {
			if (inv.zoneChannelId.equals(requestId)) {
				return inv;
			}
		}
		return null;
	}

	public synchronized List<Invoice> getAsSortedList(Comparator<Invoice> comparator) {
		List<Invoice> invoices = new ArrayList<Invoice>(this.invoices.values());

		Collections.sort(invoices, comparator);
		return invoices;
	}

	public synchronized void updateForExpired() {
		long oneHourDelta = 3610000L;
		long nowMillisec = (new Date()).getTime();
		for (Invoice inv : invoices.values()) {
			long invMillisec = inv.created_at.getDate().getTime();
			if (inv.status == InvoiceStatus.UNPAID && nowMillisec - invMillisec > oneHourDelta) {
				inv.status = InvoiceStatus.EXPIRED;
			}
		}
	}

	private class SavingThread extends Thread {
		@Override
		public void run() {
			while (true) {
				try {
					updateForExpired();
					save();
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

}
