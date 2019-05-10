package bittech.dae.controller.helpers;

import bittech.lib.utils.Require;

public class SimpleThread extends Thread {
	
	Runnable toRun;
	Exception lastException = null;
	
	public SimpleThread(Runnable toRun) {
		this.toRun = Require.notNull(toRun, "toRun");
		this.start();
	}
	
	@Override
	public void run() {
		try {
		toRun.run();
		} catch(Exception ex) {
			lastException = ex;
		}
	}
	


}
