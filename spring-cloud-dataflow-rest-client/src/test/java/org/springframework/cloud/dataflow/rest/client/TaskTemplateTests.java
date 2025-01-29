/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.dataflow.rest.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

/**
 * Test the {@link TaskTemplate} implementation of {@link TaskOperations}.
 *
 * @author Glenn Renfro
 * @author Corneil du Plessis
 */
class TaskTemplateTests {

	private static final String CURRENT_TASK_EXECUTION_LINK = "tasks/executions/current";

	private RestTemplate restTemplate;

	@BeforeEach
	void setup() {
		restTemplate = mock(RestTemplate.class);
	}
	@Test
	void invalidVersion() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> validateExecutionLinkPresent("2.11.5"));
	}
	@Test
	void minDataFlow() {
		validateExecutionLinkPresent("3.0.0");
	}

	@Test
	void futureDataFlow() {
		validateExecutionLinkPresent("3.0.0");
	}

	@Test
	void invalidDataFlow() {
		assertThatIllegalArgumentException().isThrownBy(() -> validateExecutionLinkPresent("2.11.5"));
	}


	private void validateExecutionLinkPresent(String dataFlowVersion) {
		TestResource testResource = new TestResource();
		new TaskTemplate(this.restTemplate, testResource, dataFlowVersion);
		assertThat(testResource.isLinkRequested(CURRENT_TASK_EXECUTION_LINK)).isTrue();
	}

	public static class TestResource extends RepresentationModel<TestResource> {

		private final Map<String, Long> linksRequested = new HashMap<>();

		@Override
		public Optional<Link> getLink(String rel) {
			if (this.linksRequested.containsKey(rel)) {
				Long count = this.linksRequested.get(rel);
				this.linksRequested.put(rel, count + 1L);
			}
			else {
				this.linksRequested.put(rel, 1L);
			}

			return Optional.of(Link.of("foo", "bar"));
		}

		public boolean isLinkRequested(String linkName) {
			boolean result = this.linksRequested.containsKey(linkName) &&
				this.linksRequested.get(linkName) > 1L;

			return result;
		}

	}
}
