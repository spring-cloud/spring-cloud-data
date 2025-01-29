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
package org.springframework.cloud.dataflow.common.test.docker.compose.configuration;

import java.util.List;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class ProjectNameTests {

    @Test
    public void use_project_name_prefix_in_construct_compose_command() {
        List<String> command = ProjectName.random().constructComposeFileCommand();

		assertThat(command).hasSize(2);
		assertThat(command.get(0)).isEqualTo("--project-name");
    }

    @Test
    public void produce_different_names_on_successive_calls_to_random() {
        List<String> firstCommand = ProjectName.random().constructComposeFileCommand();
        List<String> secondCommand = ProjectName.random().constructComposeFileCommand();

		assertThat(firstCommand).isNotEqualTo(secondCommand);
    }

    @Test
    public void have_eight_characters_long_random() {
        String randomName = ProjectName.random().constructComposeFileCommand().get(1);
		assertThat(randomName).hasSize(8);
    }

    @Test
    public void should_pass_name_to_command_in_from_string_factory() {
        List<String> command = ProjectName.fromString("projectname").constructComposeFileCommand();
		assertThat(command).containsExactly("--project-name", "projectname");
    }

    @Test
    public void should_disallow_names_in_from_string_factory() {
        List<String> command = ProjectName.fromString("projectname").constructComposeFileCommand();
		assertThat(command).containsExactly("--project-name", "projectname");
    }

    @Test
    public void reject_blanks_in_from_string() {
        assertThatIllegalStateException().isThrownBy(() -> ProjectName.fromString(" ")).
			withMessageContaining("ProjectName must not be blank.");
    }

    @Test
    public void match_validation_behavior_of_docker_compose_cli() {
		assertThatIllegalStateException().isThrownBy(() -> ProjectName.fromString("Crazy#Proj ect!Name")).
			withMessageContaining("ProjectName 'Crazy#Proj ect!Name' not allowed, please use lowercase letters and numbers only.");
    }

    @Test
    public void should_return_the_project_name_when_asString_called() {
        String projectName = ProjectName.fromString("projectname").asString();
		assertThat(projectName).isEqualTo("projectname");
    }
}

