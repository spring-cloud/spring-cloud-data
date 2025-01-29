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

import java.io.IOException;

import org.junit.Test;
import org.springframework.cloud.dataflow.common.test.docker.compose.configuration.MockDockerEnvironment;
import org.springframework.cloud.dataflow.common.test.docker.compose.execution.Docker;
import org.springframework.cloud.dataflow.common.test.docker.compose.execution.DockerCompose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.dataflow.common.test.docker.compose.connection.waiting.SuccessOrFailureMatchers.failureWithMessage;
import static org.springframework.cloud.dataflow.common.test.docker.compose.connection.waiting.SuccessOrFailureMatchers.successful;
// @checkstyle:on

public class ContainerTests {

    private static final String IP = "127.0.0.1";

    private final Docker docker = mock(Docker.class);
    private final DockerCompose dockerCompose = mock(DockerCompose.class);
    private final MockDockerEnvironment env = new MockDockerEnvironment(dockerCompose);
    private final Container container = new Container("service", docker, dockerCompose);

    @Test
    public void return_port_for_container_when_external_port_number_given() throws Exception {
        DockerPort expected = env.availableService("service", IP, 5433, 5432);
        DockerPort port = container.portMappedExternallyTo(5433);
		assertThat(port).isEqualTo(expected);
    }

    @Test
    public void return_port_for_container_when_internal_port_number_given() throws Exception {
        DockerPort expected = env.availableService("service", IP, 5433, 5432);
        DockerPort port = container.port(5432);
		assertThat(port).isEqualTo(expected);
    }

    @Test
    public void call_docker_ports_once_when_two_ports_are_requested() throws Exception {
        env.ports("service", IP, 8080, 8081);
        container.port(8080);
        container.port(8081);
        verify(dockerCompose, times(1)).ports("service");
    }

    @Test
    public void return_updated_external_port_on_restart() throws IOException, InterruptedException {
        int internalPort = 5432;
        env.ephemeralPort("service", IP, internalPort);

        DockerPort port = container.port(internalPort);
        int prePort = port.getExternalPort();

        DockerPort samePort = container.port(internalPort);
		assertThat(prePort).isEqualTo(samePort.getExternalPort());

        container.stop();
        container.start();

        DockerPort updatedPort = container.port(internalPort);
        assertThat(prePort, not(is(updatedPort.getExternalPort())));
    }

    @Test
    public void throw_illegal_argument_exception_when_a_port_for_an_unknown_external_port_is_requested()
            throws Exception {
        // Service must have ports otherwise we end up with an exception telling you the service is listening at all
        env.availableService("service", IP, 5400, 5400);
        assertThatIllegalArgumentException().isThrownBy(() -> container.portMappedExternallyTo(5432)).
			withMessageContaining("No port mapped externally to '5432' for container 'service'");
    }

    @Test
    public void throw_illegal_argument_exception_when_a_port_for_an_unknown_internal_port_is_requested()
            throws Exception {
        env.availableService("service", IP, 5400, 5400);
		assertThatIllegalArgumentException().isThrownBy(() -> container.port(5432)).
			withMessageContaining("No internal port '5432' for container 'service'");
    }

    @Test
    public void have_all_ports_open_if_all_exposed_ports_are_open() throws Exception {
        env.availableHttpService("service", IP, 1234, 1234);

		assertThat(container.areAllPortsOpen(), successful());
    }

    @Test
    public void not_have_all_ports_open_if_has_at_least_one_closed_port_and_report_the_name_of_the_port() throws Exception {
        int unavailablePort = 4321;
        String unavailablePortString = Integer.toString(unavailablePort);

        env.availableService("service", IP, 1234, 1234);
        env.unavailableService("service", IP, unavailablePort, unavailablePort);

		assertThat(container.areAllPortsOpen(), failureWithMessage(containsString(unavailablePortString)));
    }

    @Test
    public void be_listening_on_http_when_the_port_is() throws Exception {
        env.availableHttpService("service", IP, 1234, 2345);

        assertThat(
                container.portIsListeningOnHttp(2345, port -> "http://some.url:" + port),
				successful());
    }

    @Test
    public void not_be_listening_on_http_when_the_port_is_not_and_reports_the_port_number_and_url() throws Exception {
        int unavailablePort = 1234;
        String unvaliablePortString = Integer.toString(unavailablePort);

        env.unavailableHttpService("service", IP, unavailablePort, unavailablePort);

        assertThat(
                container.portIsListeningOnHttp(unavailablePort, port -> "http://some.url:" + port.getInternalPort()),
				failureWithMessage(
                    containsString("http://some.url:" + unvaliablePortString)
				));
    }

}
