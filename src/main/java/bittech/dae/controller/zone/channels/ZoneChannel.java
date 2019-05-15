package bittech.dae.controller.zone.channels;

import org.apache.commons.codec.digest.DigestUtils;

import bittech.lib.commands.ln.channels.OpenChannelResponse;
import bittech.lib.commands.ln.invoices.AddInvoiceRequest;
import bittech.lib.commands.ln.invoices.DecodeInvoiceResponse;
import bittech.lib.commands.lnzone.EstablishedChannel;
import bittech.lib.commands.lnzone.EstablishedChannel.Status;
import bittech.lib.commands.lnzone.external.OpenZoneChannelRequest;

public class ZoneChannel {

	class HelperData {
		
		protected OpenZoneChannelRequest openChannelRequest = null;
		protected AddInvoiceRequest invoiceRequest = null;
		protected DecodeInvoiceResponse decodedInvoice = null;
		protected OpenChannelResponse fundChannelResponse = null;

	}

//	public Status status;
//	public String zoneChannelId = createzoneChannelId();
//	public boolean owner;
//	public String zonePeerUri;

	public HelperData helperData = new HelperData();
	public EstablishedChannel establishedChannel;

	public static String createzoneChannelId() {
		String randNum = Long.toString((long) (Math.random() * Long.MAX_VALUE))
				+ Long.toString((long) (Math.random() * Long.MAX_VALUE))
				+ Long.toString((long) (Math.random() * Long.MAX_VALUE))
				+ Long.toString((long) (Math.random() * Long.MAX_VALUE));
		return DigestUtils.sha1Hex(randNum);
	}

	public ZoneChannel(boolean owner) {
		establishedChannel = new EstablishedChannel();
		establishedChannel.owner = owner;
		establishedChannel.status = Status.INITIALIZED;
		establishedChannel.zoneChannelId = createzoneChannelId();
	}

	
	public OpenZoneChannelRequest getOpenChannelRequest() {
		return this.helperData.openChannelRequest;
	}
	
	public void setOpenChannelRequest(OpenZoneChannelRequest openChannelRequest) {
		this.helperData.openChannelRequest = openChannelRequest;
		
		if (this.establishedChannel.owner) {
			this.establishedChannel.myAmount = openChannelRequest.peerAmount;
			this.establishedChannel.peerAmount = openChannelRequest.myAmount;
		} else {
			this.establishedChannel.myAmount = openChannelRequest.myAmount;
			this.establishedChannel.peerAmount = openChannelRequest.peerAmount;
		}

		this.establishedChannel.feeReserve = openChannelRequest.feeReserve;

		this.establishedChannel.peerUri = openChannelRequest.peerUri;
		this.establishedChannel.offer = openChannelRequest.offer;
	}
	
	public AddInvoiceRequest getInvoiceRequest() {
		return this.helperData.invoiceRequest;
	}
	public void setInvoiceRequest(AddInvoiceRequest invoiceRequest) {
		this.helperData.invoiceRequest = invoiceRequest;
	}
	public DecodeInvoiceResponse getDecodedInvoice() {
		return this.helperData.decodedInvoice;
	}
	public void setDecodedInvoice(DecodeInvoiceResponse decodedInvoice) {
		this.helperData.decodedInvoice = decodedInvoice;
	}
	public OpenChannelResponse getFundChannelResponse() {
		return this.helperData.fundChannelResponse;
	}
	public void setFundChannelResponse(OpenChannelResponse fundChannelResponse) {
		this.helperData.fundChannelResponse = fundChannelResponse;
		this.establishedChannel.fundingTxId = fundChannelResponse.txId;
	}

}
