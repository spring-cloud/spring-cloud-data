/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.cloud.dataflow.server.stream;

import java.net.MalformedURLException;

import org.junit.Test;

import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mark Pollack
 */
public class SkipperStreamDeployerTests {

	@Test
	public void testResourceProcessing() {
		MavenResource mavenResource = new MavenResource.Builder()
				.artifactId("timestamp-task")
				.groupId("org.springframework.cloud.task.app")
				.version("1.0.0.RELEASE")
				.build();
		String resourceWithoutVersion = SkipperStreamDeployer.getResourceWithoutVersion(mavenResource);
		assertThat(resourceWithoutVersion).isEqualTo("maven://org.springframework.cloud.task.app:timestamp-task");
		assertThat(SkipperStreamDeployer.getResourceVersion(mavenResource)).isEqualTo("1.0.0.RELEASE");
	}

	@Test
	public void testFileResourceProcessing() throws MalformedURLException{
		Resource resource = new UrlResource("file:/springcloudstream/file-source-kafka-10-1.2.0.RELEASE.jar");
		assertThat(SkipperStreamDeployer.getResourceWithoutVersion(resource)).isEqualTo("file:/springcloudstream/file-source-kafka-10.jar");
		assertThat(SkipperStreamDeployer.getResourceVersion(resource)).isEqualTo("1.2.0.RELEASE");

		resource = new UrlResource("file:/springcloudstream/file-source-kafka-10-1.2.0.BUILD-SNAPSHOT.jar");
		assertThat(SkipperStreamDeployer.getResourceWithoutVersion(resource)).isEqualTo("file:/springcloudstream/file-source-kafka-10.jar");
		assertThat(SkipperStreamDeployer.getResourceVersion(resource)).isEqualTo("1.2.0.BUILD-SNAPSHOT");

		resource = new UrlResource("http://springcloudstream/file-source-kafka-10-1.2.0.RELEASE.jar");
		assertThat(SkipperStreamDeployer.getResourceWithoutVersion(resource)).isEqualTo("http://springcloudstream/file-source-kafka-10.jar");
		assertThat(SkipperStreamDeployer.getResourceVersion(resource)).isEqualTo("1.2.0.RELEASE");

		resource = new UrlResource("http://springcloudstream/file-source-kafka-crap.jar");
		assertThat(SkipperStreamDeployer.getResourceWithoutVersion(resource)).isEqualTo("http://springcloudstream/file-source-kafka.jar");
		assertThat(SkipperStreamDeployer.getResourceVersion(resource)).isEqualTo("crap");
	}
}
