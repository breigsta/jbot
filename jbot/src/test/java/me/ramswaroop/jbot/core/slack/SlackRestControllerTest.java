package me.ramswaroop.jbot.core.slack;

import org.junit.Before;
import org.junit.Test;

public class SlackRestControllerTest {

	private SlackRestController controller;
	
	@Before
	public void setUp() {
		this.controller = new SlackRestController();
	}
	
	@Test
	public void testGetApprovalFeatureList() {
		runInstance(1);
		runInstance(2);
		runInstance(3);
		runInstance(4);
		runInstance(5);
		runInstance(6);
		runInstance(7);
		runInstance(8);
		runInstance(9);
		runInstance(10);
	}
	
	private void runInstance(Integer instance) {
		String i = instance.toString();
		String result = this.controller.getApprovalFeatureList(i);
		System.out.println(result);
	}
}
