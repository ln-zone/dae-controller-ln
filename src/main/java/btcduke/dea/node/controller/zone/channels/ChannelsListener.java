package btcduke.dea.node.controller.zone.channels;

import java.util.Date;

import bittech.lib.commands.ln.invoices.AddInvoiceCommand;
import bittech.lib.commands.lnzone.commans.GetChannelStatusCommand;
import bittech.lib.commands.lnzone.commans.GetChannelStatusResponse;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelCommand;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelRequest;
import bittech.lib.commands.lnzone.commans.OpenZoneChannelResponse;
import bittech.lib.commands.lnzone.commans.WaitForChannelFundedCommand;
import bittech.lib.commands.lnzone.commans.WaitForChannelFundedResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Connection;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Listener;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public abstract class ChannelsListener implements Listener {

	private final ZoneChannels channels;
	private final Connection controllerConnection;

	public ChannelsListener(ZoneChannels channels, Connection controllerConnection) {
		this.channels = Require.notNull(channels, "channels");
		this.controllerConnection = Require.notNull(controllerConnection, "controllerConnection");
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { OpenZoneChannelCommand.class, GetChannelStatusCommand.class,
				WaitForChannelFundedCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof OpenZoneChannelCommand) {

			OpenZoneChannelCommand cmd = (OpenZoneChannelCommand) command;

			try {

				ZoneChannel channel = onOpenChannelRequest(cmd.getRequest());

				AddInvoiceCommand invoiceCmd = new AddInvoiceCommand(channel.getInvoiceRequest());

				controllerConnection.execute(invoiceCmd);
				
				if(invoiceCmd.getResponse().payment_request == null) {
					throw new Exception("Controller returned null bolt11 after call invoice command");
				}
				
				if (invoiceCmd.getError() != null) {
					cmd.error = invoiceCmd.getError();
				} else {
					cmd.response = new OpenZoneChannelResponse();
					cmd.response.zoneChannelId = channel.establishedChannel.zoneChannelId;
					cmd.response.invoice = invoiceCmd.getResponse().payment_request;
				}
			} catch (Exception ex) {
				cmd.error = new ErrorResponse(ex);
			}

		} else if (command instanceof GetChannelStatusCommand) {

			GetChannelStatusCommand cmd = (GetChannelStatusCommand) command;

			ZoneChannel channel = channels.findZoneChannelById(cmd.getRequest().zoneChannelId);
//			EstablishedChannel channelData = channels.findZoneChannelById(cmd.getRequest().zoneChannelId);

			if (channel == null) {
				throw new StoredException("No channel for given request id: " + cmd.getRequest().zoneChannelId, null);
			}

			cmd.response = new GetChannelStatusResponse(channel.establishedChannel.status);

		} else if (command instanceof WaitForChannelFundedCommand) {

			WaitForChannelFundedCommand cmd = (WaitForChannelFundedCommand) command;

			ZoneChannel channel;

			long timeout = (new Date()).getTime() + cmd.getTimeout() - 1000;
			while (true) { // TODO: More clever way than loop

				channel = channels.findZoneChannelById(cmd.getRequest().zoneChannelId);

				if(channel == null) {
					throw new StoredException("No channel with zoneChannelId = '" + cmd.getRequest().zoneChannelId + "'", null);
				}

				if (channel.establishedChannel.fundingTxId != null) {
					// throw new StoredException("No channel for given request id: " +
					// cmd.getRequest().zoneChannelId,
					// null);s
					(new Log()).param("channel", channel).event("Checking fundChannelResponse");
					break;
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new StoredException("Cannot wait for channel funded", e);
				}

				if ((new Date()).getTime() > timeout) {
					throw new StoredException("No channel funded before timeout", null);
				}
			}

			cmd.response = new WaitForChannelFundedResponse(channel.establishedChannel.fundingTxId);

		} else {
			throw new StoredException("Usupported command type: " + command, null);
		}
	}

	public abstract ZoneChannel onOpenChannelRequest(OpenZoneChannelRequest request);

//	public abstract void onChannelChangedRequest(ChannelChangedRequest request);

}
