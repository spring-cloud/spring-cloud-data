/*
 * Copyright 2018-2020 the original author or authors.
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
package org.springframework.cloud.dataflow.common.test.docker.compose.configuration;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DockerComposeFilesTests {

	@Rule
	public final TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void throw_exception_when_compose_file_is_not_specified() {
		assertThatIllegalStateException().isThrownBy(DockerComposeFiles::from).
			withMessageContaining("A docker compose file must be specified.");
	}

	@Test
	public void throw_exception_when_compose_file_does_not_exist() {
		assertThatIllegalStateException().isThrownBy(() -> DockerComposeFiles.from("does-not-exist.yaml")).
			withMessageContaining("The following docker-compose files:").withMessageContaining("does-not-exist.yaml").
			withMessageContaining("do not exist.");
	}

	@Test
	public void
			throw_correct_exception_when_there_is_a_single_missing_compose_file_with_an_existing_compose_file()
			throws Exception {

		assertThatThrownBy(() -> {
			File composeFile = tempFolder.newFile("docker-compose.yaml");
			DockerComposeFiles.from("does-not-exist.yaml", composeFile.getAbsolutePath());
		}).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("The following docker-compose files:")
			.hasMessageContaining("does-not-exist.yaml")
			.hasMessageContaining("do not exist.")
			.hasMessageNotContaining("docker-compose.yaml");
	}

	@Test
	public void generate_docker_compose_file_command_correctly_for_single_compose_file() throws Exception {
		File composeFile = tempFolder.newFile("docker-compose.yaml");
		DockerComposeFiles dockerComposeFiles = DockerComposeFiles.from(composeFile.getAbsolutePath());
		assertThat(dockerComposeFiles.constructComposeFileCommand()).containsExactly("--file",
				composeFile.getAbsolutePath());
	}

	@Test
	public void generate_docker_compose_file_command_correctly_for_multiple_compose_files() throws Exception {
		File composeFile1 = tempFolder.newFile("docker-compose1.yaml");
		File composeFile2 = tempFolder.newFile("docker-compose2.yaml");
		DockerComposeFiles dockerComposeFiles = DockerComposeFiles.from(composeFile1.getAbsolutePath(), composeFile2.getAbsolutePath());
		assertThat(dockerComposeFiles.constructComposeFileCommand()).containsExactly("--file",
				composeFile1.getAbsolutePath(), "--file", composeFile2.getAbsolutePath());
	}

	@Test
	public void testFromClasspathExist() {
		DockerComposeFiles dockerComposeFiles = DockerComposeFiles.from("classpath:docker-compose-cp1.yaml",
				"classpath:org/springframework/cloud/dataflow/common/test/docker/compose/docker-compose-cp2.yaml");
		assertThat(dockerComposeFiles.constructComposeFileCommand()).has(matchAll(is("--file"),
				containsString("docker-compose-cp1.yaml"),
				is("--file"),
				containsString("docker-compose-cp2.yaml")
		));
	}

	@Test
	public void testFromClasspathDoesNotExist() {
		assertThatIllegalArgumentException().isThrownBy(() -> DockerComposeFiles.from("classpath:does-not-exist.yaml")).
			withMessageContaining("Can't find resource classpath:does-not-exist.yaml");
	}

	private static Condition<String> is(String value) {
		return new Condition<>(s -> s.equals(value), "equals:" + value);
	}

	private static Condition<String> containsString(String value) {
		return new Condition<>(s -> s.contains(value), "contains:" + value);
	}
	private static <T> Condition<? super List<? extends T>> matchAll(Condition<T> ... conditions) {
		return new Condition<>(list -> {
			assertThat(list).hasSize(conditions.length);
			Iterator<? extends T> iterList = list.iterator();
			Iterator<Condition<T>> conditionIterator = Arrays.asList(conditions).iterator();
			while(iterList.hasNext() && conditionIterator.hasNext()) {
				Condition<T> condition = conditionIterator.next();
				if(!condition.matches(iterList.next())) {
					throw new AssertionError(condition.description());
				}
			}
			return true;
		}, "matches all");
	}
}
