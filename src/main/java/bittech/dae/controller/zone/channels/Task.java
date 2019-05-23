package bittech.dae.controller.zone.channels;

import bittech.lib.utils.Require;

public class Task {
	
	public final String name;
	public Boolean succeeded = null;
	public Exception exception = null;
	
	public Task(String name) {
		this.name = Require.notEmpty(name, "task name");
	}

}
