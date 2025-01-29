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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class AdditionalEnvironmentValidatorTests {

	@Test
	public void throwExceptionWhenAdditionalEnvironmentVariablesContainDockerVariables() {
		Map<String, String> variables = new HashMap<>();
		variables.put("DOCKER_HOST", "tcp://some-host:2376");
		variables.put("SOME_VARIABLE", "Some Value");

		assertThatIllegalStateException().isThrownBy(() ->AdditionalEnvironmentValidator.validate(variables)).
			withMessageContaining("The following variables").
			withMessageContaining("DOCKER_HOST").
			withMessageContaining("cannot exist in your additional environment");
	}

	@Test
	public void validate_arbitrary_environment_variables() {
		Map<String, String> variables = new HashMap<>();
		variables.put("SOME_VARIABLE", "Some Value");

		assertThat(AdditionalEnvironmentValidator.validate(variables)).isEqualTo(variables);
	}
}
