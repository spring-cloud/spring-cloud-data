/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.shell.command;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.shell.AbstractShellIntegrationTest;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.skipper.domain.Deployer;
import org.springframework.cloud.skipper.domain.Info;
import org.springframework.cloud.skipper.domain.Status;
import org.springframework.cloud.skipper.domain.StatusCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.shell.core.CommandResult;
import org.springframework.shell.table.Table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Ilayaperumal Gopinathan
 * @author Mark Fisher
 * @author Glenn Renfro
 */
public class StreamCommandTests extends AbstractShellIntegrationTest {

	private static final String APPS_URI = "META-INF/test-stream-apps.properties";

	private static final Logger logger = LoggerFactory.getLogger(StreamCommandTests.class);

	@BeforeEach
	public void registerApps() {
		AppRegistryService registry = applicationContext.getBean(AppRegistryService.class);
		registry.importAll(true, new ClassPathResource(APPS_URI));
	}

	@AfterEach
	public void destroyStreams() {
		stream().destroyCreatedStreams();
	}

	@Test
	public void testStreamLifecycleForTickTock() throws InterruptedException {
		logger.info("Starting Stream Test for TickTock");
		Thread.sleep(2000);
		String streamName = generateUniqueStreamOrTaskName();
		Info info = new Info();
		Status status = new Status();
		status.setStatusCode(StatusCode.UNKNOWN);
		status.setPlatformStatus(null);
		info.setStatus(status);

		when(skipperClient.status(ArgumentMatchers.anyString())).thenReturn(info);
		AppDeployer appDeployer = applicationContext.getBean(AppDeployer.class);
		Deployer deployer = new Deployer("testDeployer", "testType", appDeployer);
		when(skipperClient.listDeployers()).thenReturn(Arrays.asList(deployer));
		stream().create(streamName, "time | log");
	}

	@Test
	public void testValidate() throws InterruptedException {
		Thread.sleep(2000);
		String streamName = generateUniqueStreamOrTaskName();
		Info info = new Info();
		Status status = new Status();
		status.setStatusCode(StatusCode.UNKNOWN);
		status.setPlatformStatus(null);
		info.setStatus(status);

		when(skipperClient.status(ArgumentMatchers.anyString())).thenReturn(info);
		AppDeployer appDeployer = applicationContext.getBean(AppDeployer.class);
		Deployer deployer = new Deployer("testDeployer", "testType", appDeployer);
		when(skipperClient.listDeployers()).thenReturn(Arrays.asList(deployer));

		//stream().create(streamName, "time | log");
		stream().createDontDeploy(streamName, "time | log");

		CommandResult cr = stream().validate(streamName);
		assertTrue(cr.isSuccess(), "task validate status command must be successful");
		List results = (List) cr.getResult();
		Table table = (Table)results.get(0);
		assertEquals(2, table.getModel().getColumnCount(), "Number of columns returned was not expected");
		assertEquals("Stream Name", table.getModel().getValue(0, 0), "First Row First Value should be: Stream Name");
		assertEquals("Stream Definition", table.getModel().getValue(0, 1), "First Row Second Value should be: Stream Definition");
		assertEquals(streamName, table.getModel().getValue(1, 0), "Second Row First Value should be: " + streamName);
		assertEquals("time | log", table.getModel().getValue(1, 1), "Second Row Second Value should be: time | log");

		String message = String.format("\n%s is a valid stream.", streamName);
		assertEquals(message, results.get(1), String.format("Notification should be: %s",message ));

		table = (Table)results.get(2);
		assertEquals(2, table.getModel().getColumnCount(), "Number of columns returned was not expected");
		assertEquals("App Name", table.getModel().getValue(0, 0), "First Row First Value should be: App Name");
		assertEquals("Validation Status", table.getModel().getValue(0, 1), "First Row Second Value should be: Validation Status");
		assertEquals("source:time" , table.getModel().getValue(1, 0), "Second Row First Value should be: source:time");
		assertEquals("valid", table.getModel().getValue(1, 1), "Second Row Second Value should be: valid");
		assertEquals("sink:log" , table.getModel().getValue(2, 0), "Third Row First Value should be: sink:log");
		assertEquals("valid", table.getModel().getValue(2, 1), "Third Row Second Value should be: valid");
	}

}
