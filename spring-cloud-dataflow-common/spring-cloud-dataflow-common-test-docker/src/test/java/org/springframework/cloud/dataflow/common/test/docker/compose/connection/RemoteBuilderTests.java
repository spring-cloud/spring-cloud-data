/*
 * Copyright 2018-2019 the original author or authors.
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
package org.springframework.cloud.dataflow.common.test.docker.compose.connection;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.cloud.dataflow.common.test.docker.compose.configuration.EnvironmentVariables.DOCKER_CERT_PATH;
import static org.springframework.cloud.dataflow.common.test.docker.compose.configuration.EnvironmentVariables.DOCKER_HOST;

public class RemoteBuilderTests {

	@Test
	public void throw_exception_when_building_a_docker_machine_without_a_host() {
		assertThatIllegalStateException().isThrownBy(() -> DockerMachine.remoteMachine()
					 .withoutTLS()
					 .build()).withMessageContaining("Missing required environment variables")
			.withMessageContaining("DOCKER_HOST");
	}

	@Test
	public void have_no_tls_environment_variables_when_a_docker_machine_is_built_without_tls() {
		DockerMachine dockerMachine = DockerMachine.remoteMachine()
												   .host("tcp://192.168.99.100")
												   .withoutTLS()
												   .build();

		Map<String, String> expected = new HashMap<>();
		expected.put(DOCKER_HOST, "tcp://192.168.99.100");

		validateEnvironmentConfiguredDirectly(dockerMachine, expected);
	}

	@Test
	public void have_tls_environment_variables_set_when_a_docker_machine_is_built_with_tls() {
		DockerMachine dockerMachine = DockerMachine.remoteMachine()
												   .host("tcp://192.168.99.100")
												   .withTLS("/path/to/certs")
												   .build();

		Map<String, String> expected = new HashMap<>();
		expected.put(DOCKER_HOST, "tcp://192.168.99.100");
		expected.put(DOCKER_CERT_PATH, "/path/to/certs");
		validateEnvironmentConfiguredDirectly(dockerMachine, expected);
	}

	@Test
	public void build_a_docker_machine_with_additional_environment_variables() {
		DockerMachine dockerMachine = DockerMachine.remoteMachine()
												   .host("tcp://192.168.99.100")
												   .withoutTLS()
												   .withAdditionalEnvironmentVariable("SOME_VARIABLE", "SOME_VALUE")
												   .build();

		Map<String, String> expected = new HashMap<>();
		expected.put(DOCKER_HOST, "tcp://192.168.99.100");
		expected.put("SOME_VARIABLE", "SOME_VALUE");
		validateEnvironmentConfiguredDirectly(dockerMachine, expected);
	}

	private static void validateEnvironmentConfiguredDirectly(DockerMachine dockerMachine, Map<String, String> expectedEnvironment) {
		ProcessBuilder process = dockerMachine.configuredDockerComposeProcess();

		Map<String, String> environment = process.environment();
		expectedEnvironment.forEach((var, val) -> assertThat(environment).containsEntry(var, val));
	}

}
