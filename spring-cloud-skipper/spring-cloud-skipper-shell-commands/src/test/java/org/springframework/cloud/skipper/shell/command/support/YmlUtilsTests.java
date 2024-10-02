/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.cloud.skipper.shell.command.support;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Corneil du Plessis
 */
class YmlUtilsTests {

	@Test
	void simpleConversion() {
		String stringToConvert = "hello=oi,world=mundo";
		String yml = YmlUtils.getYamlConfigValues(null, stringToConvert);
		assertThat(yml).isEqualTo("hello: oi\nworld: mundo\n");
	}

	@Test
	void propertiesParsingWithPackageDeps() throws IOException {
		String properties = "log.spec.deploymentProperties.spring.cloud.deployer.cloudfoundry.route=mlp3-helloworld.cfapps.io,"
				+ "time.spec.deploymentProperties.spring.cloud.deployer.cloudfoundry.route=mlp1-helloworld.cfapps.io";
		String propertiesYml = YmlUtils.getYamlConfigValues(null, properties);
		assertThat(propertiesYml).isEqualTo(
				"""
				log:
				  spec:
				    deploymentProperties:
				      spring.cloud.deployer.cloudfoundry.route: mlp3-helloworld.cfapps.io
				time:
				  spec:
				    deploymentProperties:
				      spring.cloud.deployer.cloudfoundry.route: mlp1-helloworld.cfapps.io
				""");
	}

	@Test
	void propertiesParsing() throws IOException {
		String properties = "spec.deploymentProperties.spring.cloud.deployer.cloudfoundry.route=mlp3-helloworld.cfapps.io";
		String propertiesYml = YmlUtils.getYamlConfigValues(null, properties);
		assertThat(propertiesYml).isEqualTo("""
				spec:
				  deploymentProperties:
				    spring.cloud.deployer.cloudfoundry.route: mlp3-helloworld.cfapps.io
				""");
	}

	@Test
	void logVersion() throws IOException {
		String properties = "log.version=1.1.1.RELEASE";
		String propertiesYml = YmlUtils.getYamlConfigValues(null, properties);
		assertThat(propertiesYml).isEqualTo("log:\n  version: 1.1.1.RELEASE\n");
	}

}
