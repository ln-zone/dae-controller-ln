package bittech.dae.controller.ln.general;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.commans.general.DeleteExceptionsCommand;
import bittech.lib.commans.general.DeleteLogsCommand;
import bittech.lib.commans.general.GetExceptionCommand;
import bittech.lib.commans.general.GetExceptionResponse;
import bittech.lib.commans.general.PingCommand;
import bittech.lib.commans.general.PingResponse;
import bittech.lib.commans.general.ValidateBtcAddrCommand;
import bittech.lib.commans.general.ValidateBtcAddrResponse;
import bittech.lib.protocol.Command;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.common.NoDataResponse;
import bittech.lib.utils.ExecResponse;
import bittech.lib.utils.SystemExec;
import bittech.lib.utils.exceptions.ExceptionManager;
import bittech.lib.utils.logs.Logs;

public class MixListener implements Listener {

	private final static Logger LOGGER = LoggerFactory.getLogger(MixListener.class);

	public MixListener() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { PingCommand.class, GetExceptionCommand.class, DeleteExceptionsCommand.class,
				DeleteLogsCommand.class, ValidateBtcAddrCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) {
		if (command instanceof PingCommand) {
			PingCommand cmd = (PingCommand) command;
			cmd.response = new PingResponse("PONG: " + cmd.request.message);
		} else if (command instanceof GetExceptionCommand) {
			GetExceptionCommand cmd = (GetExceptionCommand) command;
			ExceptionManager exMan = ExceptionManager.getInstance();
			cmd.response = new GetExceptionResponse(exMan.get(cmd.getRequest().exceptionId));
		} else if (command instanceof DeleteExceptionsCommand) {
			DeleteExceptionsCommand cmd = (DeleteExceptionsCommand) command;
			ExceptionManager exMan = ExceptionManager.getInstance();
			exMan.deleteAll();
			cmd.response = new NoDataResponse();
		} else if (command instanceof DeleteLogsCommand) {
			DeleteLogsCommand cmd = (DeleteLogsCommand) command;
			Logs.getInstance().clear();
			cmd.response = new NoDataResponse();
		} else if (command instanceof ValidateBtcAddrCommand) {
			try {
				ValidateBtcAddrCommand cmd = (ValidateBtcAddrCommand) command;
				cmd.response = new ValidateBtcAddrResponse(validateAddr(cmd.getRequest().addr));
			} catch (Exception ex) {
				throw new RuntimeException("Cannot process ValidateBtcAddrCommand", ex);
			}
		} else {
			throw new RuntimeException("Command not supported: " + command);
		}

	}

	public final static boolean validateAddr(String addr) {
		if (addr.contains(" ") || addr.contains("\"")) {
			LOGGER.info("Address NOT correct: " + addr);
			return false;
		} else {
			ExecResponse resp = SystemExec.exec(
					"bitcoin-cli -rpcconnect=80.211.252.169 -rpcport=8332 -rpcuser=bitUser -rpcpassword=bitUser&7623GW6756@)! validateaddress "
							+ addr,
					5000);
			// SystemExecResponse resp = SystemExec.exec("bitcoin-cli validateaddress " +
			// addr, 5000);
			if (resp.output.contains("\"isvalid\": true")) {
				LOGGER.debug("Address IS correct: " + addr);
				return true;
			} else {
				LOGGER.debug("Address NOT correct: " + addr + ". Output: " + resp.output);
				return false;
			}

		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}