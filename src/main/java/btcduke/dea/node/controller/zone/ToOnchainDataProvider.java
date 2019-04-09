package btcduke.dea.node.controller.zone;

import java.util.Comparator;
import java.util.List;

import bittech.lib.manager.ManagerDataProvider;
import bittech.lib.manager.commands.GetNodeDetailsResponse;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;
import bittech.lib.utils.json.RawJson;
import bittech.lib.utils.logs.Logs;
import btcduke.dea.node.controller.zone.Invoices.Invoice;

public class ToOnchainDataProvider implements ManagerDataProvider {

	private final ToOnchainListener refillListener;
	private final ZoneModule zoneModule;

	public ToOnchainDataProvider(final ToOnchainListener refillListener, ZoneModule zoneModule) {
		this.refillListener = Require.notNull(refillListener, "refillListener");
		this.zoneModule = Require.notNull(zoneModule, "zoneModule");
	}

	@Override
	public void addCustomData(GetNodeDetailsResponse details) throws StoredException {

		List<Invoice> invoices = refillListener.getInvoices().getAsSortedList(new Comparator<Invoice>() {
			@Override
			public int compare(Invoice o1, Invoice o2) {
				return o1.created_at.toString().compareTo(o2.created_at.toString());
			}
		});

		details.details.put("offer", zoneModule.getOffer());
		details.details.put("invoices", invoices);
		details.details.put("scheduled_payments", refillListener.getPayments());
		details.details.put("logs", new RawJson(Logs.getInstance().getAsJson()));

		try {

			details.summary.put("status", "ok");
			details.summary.put("statusCode", "ok");

			details.summary.put("invoices", Integer.toString(invoices.size()));

			int paid = 0;
			int unpaid = 0;
			int completed = 0;
			int errors = 0;
			int expired = 0;
			for (Invoice inv : invoices) {
				switch (inv.status) {
				case PAID:
					paid++;
					break;
				case UNPAID:
					unpaid++;
					break;
				case EXPIRED:
					expired++;
					break;
				case COMPLETED:
					completed++;
					break;
				case ERROR:
					errors++;
					break;
				default:
					throw new Exception("Unknown invoice status: " + inv.status + " for invoice "
							+ JsonBuilder.build().toJson(inv));
				}
			}

			details.summary.put("expired", Integer.toString(expired));
			details.summary.put("unpaid", Integer.toString(unpaid));
			details.summary.put("paid", Integer.toString(paid));
			details.summary.put("completed", Integer.toString(completed));
			details.summary.put("errors", Integer.toString(errors));
			details.summary.put("scheduled_payments", Integer.toString(refillListener.getPayments().size()));

			details.summary.put("logs", Integer.toString(Logs.getInstance().count()));
			details.summary.put("exceptions", Integer.toString(details.exceptions.size()));
		} catch (Exception ex) {
			throw new StoredException("Cannot convert from NodesDetails to NodeSummary", ex);
		}

	}

}
