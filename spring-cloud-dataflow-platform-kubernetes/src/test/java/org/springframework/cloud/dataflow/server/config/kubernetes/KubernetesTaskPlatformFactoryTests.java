/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.cloud.dataflow.server.config.kubernetes;

import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

import org.springframework.cloud.dataflow.core.TaskPlatform;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesDeployerProperties;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesTaskLauncher;
import org.springframework.cloud.deployer.spi.scheduler.kubernetes.KubernetesSchedulerProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author David Turanski
 **/
public class KubernetesTaskPlatformFactoryTests {

	@Test
	public void kubernetesTaskPlatformNoScheduler() {
		KubernetesPlatformProperties platformProperties = new KubernetesPlatformProperties();
		KubernetesDeployerProperties deployerProperties = new KubernetesDeployerProperties();
		platformProperties.setAccounts(Collections.singletonMap("k8s", deployerProperties));

		KubernetesTaskPlatformFactory kubernetesTaskPlatformFactory = new KubernetesTaskPlatformFactory(
				platformProperties, Optional.empty(), false);

		TaskPlatform taskPlatform = kubernetesTaskPlatformFactory.createTaskPlatform();
		assertThat(taskPlatform.getName()).isEqualTo("Kubernetes");
		assertThat(taskPlatform.getLaunchers()).hasSize(1);
		assertThat(taskPlatform.getLaunchers().get(0).getScheduler()).isNull();
		assertThat(taskPlatform.getLaunchers().get(0).getTaskLauncher()).isInstanceOf(KubernetesTaskLauncher.class);
		assertThat(taskPlatform.getLaunchers().get(0).getName()).isEqualTo("k8s");
		assertThat(taskPlatform.getLaunchers().get(0).getType()).isEqualTo("Kubernetes");
		assertThat(taskPlatform.getLaunchers().get(0).getDescription()).matches("^master url = \\[.+\\], namespace = "
			+ "\\[.+\\], api version = \\[.+\\]$");

	}

	@Test
	public void kubernetesTaskPlatformWithScheduler() {
		KubernetesPlatformProperties platformProperties = new KubernetesPlatformProperties();
		KubernetesDeployerProperties deployerProperties = new KubernetesDeployerProperties();
		platformProperties.setAccounts(Collections.singletonMap("k8s", deployerProperties));

		KubernetesTaskPlatformFactory kubernetesTaskPlatformFactory = new KubernetesTaskPlatformFactory(
				platformProperties,
				Optional.of(new KubernetesSchedulerProperties()), true);

		TaskPlatform taskPlatform = kubernetesTaskPlatformFactory.createTaskPlatform();
		assertThat(taskPlatform.getName()).isEqualTo("Kubernetes");
		assertThat(taskPlatform.getLaunchers()).hasSize(1);
		assertThat(taskPlatform.getLaunchers().get(0).getScheduler()).isNotNull();
		assertThat(taskPlatform.getLaunchers().get(0).getTaskLauncher()).isInstanceOf(KubernetesTaskLauncher.class);
		assertThat(taskPlatform.getLaunchers().get(0).getName()).isEqualTo("k8s");
		assertThat(taskPlatform.getLaunchers().get(0).getType()).isEqualTo("Kubernetes");
		assertThat(taskPlatform.getLaunchers().get(0).getDescription()).matches("^master url = \\[.+\\], namespace = "
			+ "\\[.+\\], api version = \\[.+\\]$");

	}

}
