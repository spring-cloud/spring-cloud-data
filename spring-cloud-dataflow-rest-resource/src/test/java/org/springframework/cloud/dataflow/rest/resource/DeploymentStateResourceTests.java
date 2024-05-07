/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.dataflow.rest.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Gunnar Hillert
 * @author Corneil du Plessis
 */
public class DeploymentStateResourceTests {

	@Test
	public void testSerializationOfSingleStepExecution() throws JsonProcessingException {

		final ObjectMapper objectMapper = new ObjectMapper();

		final DeploymentStateResource deploymentStateResource = DeploymentStateResource.DEPLOYED;
		final String result = objectMapper.writeValueAsString(deploymentStateResource);

		final DocumentContext documentContext = JsonPath.parse(result);

		assertEquals("deployed", documentContext.read("$.key"));
		assertEquals("Deployed", documentContext.read("$.displayName"));
		assertEquals("The stream has been successfully deployed", documentContext.read("$.description"));

	}

}
