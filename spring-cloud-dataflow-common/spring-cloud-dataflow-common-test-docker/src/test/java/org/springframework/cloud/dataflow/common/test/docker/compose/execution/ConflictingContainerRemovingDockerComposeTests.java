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
package org.springframework.cloud.dataflow.common.test.docker.compose.execution;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


public class ConflictingContainerRemovingDockerComposeTests {
	private final DockerCompose dockerCompose = mock(DockerCompose.class);
	private final Docker docker = mock(Docker.class);

	@Test
	public void require_retry_attempts_to_be_at_least_1() {
		assertThatIllegalStateException().isThrownBy(()-> new ConflictingContainerRemovingDockerCompose(dockerCompose, docker, 0)).
			withMessageContaining("retryAttempts must be at least 1, was 0");
	}

	@Test
	public void call_up_only_once_if_successful() throws IOException, InterruptedException {
		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker);
		conflictingContainerRemovingDockerCompose.up();

		verify(dockerCompose, times(1)).up();
		verifyNoMoreInteractions(docker);
	}

	@Test
	public void call_rm_and_retry_up_if_conflicting_containers_exist() throws IOException, InterruptedException {
		String conflictingContainer = "conflictingContainer";
		doThrow(new DockerExecutionException("The name \"" + conflictingContainer + "\" is already in use")).doNothing()
				.when(dockerCompose).up();

		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker);
		conflictingContainerRemovingDockerCompose.up();

		verify(dockerCompose, times(2)).up();
		verify(docker).rm(new HashSet<>(Arrays.asList(conflictingContainer)));
	}

	@Test
	public void retry_specified_number_of_times() throws IOException, InterruptedException {
		String conflictingContainer = "conflictingContainer";
		DockerExecutionException dockerException = new DockerExecutionException(
				"The name \"" + conflictingContainer + "\" is already in use");
		doThrow(dockerException).doThrow(dockerException).doNothing().when(dockerCompose).up();

		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker, 3);
		conflictingContainerRemovingDockerCompose.up();

		verify(dockerCompose, times(3)).up();
		verify(docker, times(2)).rm(new HashSet<>(Arrays.asList(conflictingContainer)));
	}

	@Test
	public void ignore_docker_execution_exceptions_in_rm() throws IOException, InterruptedException {
		String conflictingContainer = "conflictingContainer";
		doThrow(new DockerExecutionException("The name \"" + conflictingContainer + "\" is already in use")).doNothing()
				.when(dockerCompose).up();
		doThrow(DockerExecutionException.class).when(docker).rm(anySet());

		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker);
		conflictingContainerRemovingDockerCompose.up();

		verify(dockerCompose, times(2)).up();
		verify(docker).rm(new HashSet<>(Arrays.asList(conflictingContainer)));
	}

	@Test
	public void fail_on_non_docker_execution_exceptions_in_rm() throws IOException, InterruptedException {
		String conflictingContainer = "conflictingContainer";
		doThrow(new DockerExecutionException("The name \"" + conflictingContainer + "\" is already in use")).doNothing()
				.when(dockerCompose).up();
		doThrow(RuntimeException.class).when(docker).rm(anySet());

		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker);
		assertThatRuntimeException().isThrownBy(conflictingContainerRemovingDockerCompose::up);
	}

	@Test
	public void throw_exception_if_retry_attempts_exceeded() throws IOException, InterruptedException {
		String conflictingContainer = "conflictingContainer";
		doThrow(new DockerExecutionException("The name \"" + conflictingContainer + "\" is already in use"))
				.when(dockerCompose).up();

		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker);
		assertThatExceptionOfType(DockerExecutionException.class).isThrownBy(() -> conflictingContainerRemovingDockerCompose.up()).
			withMessageContaining("docker-compose up failed");
	}

	@Test
	public void parse_container_names_from_error_message() {
		String conflictingContainer = "conflictingContainer";

		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker);
		Set<String> conflictingContainerNames = conflictingContainerRemovingDockerCompose
				.getConflictingContainerNames("The name \"" + conflictingContainer + "\" is already in use");

		assertEquals(new HashSet<>(Arrays.asList(conflictingContainer)), conflictingContainerNames);
	}

	@Test
	public void parse_container_names_from_error_message_since_v13() {
		String conflictingContainer = "conflictingContainer";

		ConflictingContainerRemovingDockerCompose conflictingContainerRemovingDockerCompose = new ConflictingContainerRemovingDockerCompose(
				dockerCompose, docker);
		Set<String> conflictingContainerNames = conflictingContainerRemovingDockerCompose
				.getConflictingContainerNames("The container name \"" + conflictingContainer + "\" is already in use");

		assertEquals(new HashSet<>(Arrays.asList(conflictingContainer)), conflictingContainerNames);
	}

}
