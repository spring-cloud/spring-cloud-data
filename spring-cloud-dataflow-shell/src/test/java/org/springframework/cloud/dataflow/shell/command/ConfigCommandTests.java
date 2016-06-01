/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.shell.command;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.dataflow.shell.ShellConfiguration;
import org.springframework.cloud.dataflow.shell.config.DataFlowShell;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.shell.CommandLine;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link ConfigCommands}.
 *
 * @author Gunnar Hillert
 *
 */
public class ConfigCommandTests {

	private ConfigCommands configCommands = new ConfigCommands();

	private DataFlowShell dataFlowShell = new DataFlowShell();

	@Mock
	private RestTemplate restTemplate;

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);

		final CommandLine commandLine = Mockito.mock(CommandLine.class);

		when(commandLine.getArgs()).thenReturn(null);

		final Exception e = new RestClientException("FooBar");
		when(restTemplate.getForObject(Mockito.any(URI.class), Mockito.eq(ResourceSupport.class))).thenThrow(e);

		configCommands.setShellConfiguration(new ShellConfiguration());
		configCommands.setCommandLine(commandLine);
		configCommands.setRestTemplate(restTemplate);
		configCommands.setDataFlowShell(dataFlowShell);
		configCommands.setServerUri("http://localhost:9393");
		configCommands.onApplicationEvent(null);
	}

	@Test
	public void testInfo() {
		final String infoResult = configCommands.info();
		assertThat(infoResult, containsString("Target│http://localhost:9393"));
		assertThat(infoResult, containsString("RestClientException: FooBar"));
	}

}
