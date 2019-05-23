package bittech.dae.controller.zone.channels;

import java.util.LinkedHashMap;
import java.util.Map;

import bittech.lib.utils.exceptions.StoredException;

public class TaskFlow {
	
	Map<String, Task> tasks;
	
	public TaskFlow(String[] names) {
		tasks = new LinkedHashMap<String, Task>(names.length);
		for(String name : names) {
			Task task = new Task(name);
			if(tasks.putIfAbsent(name, task) != null) {
				throw new StoredException("Cannot create task flow", new Exception("Name '" + name + "' provided more than once"));
			}
		}
	}
	
	public void taskSucceeded(String name) {
		Task task = tasks.get(name);
		if(task == null) {
			throw new StoredException("Cannot mark task '" + name + "' as succeeded", new Exception("No task with this name in task flow"));
		}
		task.succeeded = true;
	}
	
	public void taskFailed(String name, Exception exception) {
		Task task = tasks.get(name);
		if(task == null) {
			throw new StoredException("Cannot mark task '" + name + "' as failed", new Exception("No task with this name in task flow"));
		}
		task.succeeded = false;
		task.exception = exception;
	}

}
