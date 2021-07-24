/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.dataflow.autoconfigure.local;

import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesSchedulerProperties;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Christian Tzolov
 */
@RunWith(Enclosed.class)
public class SchedulerPerPlatformTest {

	@TestPropertySource(properties = { "spring.cloud.dataflow.features.schedules-enabled=false" })
	public static class AllSchedulerDisabledTests extends AbstractSchedulerPerPlatformTest {

		@Test
		public void testLocalSchedulerEnabled() {
			assertThrows(NoSuchBeanDefinitionException.class, () -> {
				assertFalse(context.getEnvironment().containsProperty("kubernetes_service_host"));
				assertFalse(CloudPlatform.CLOUD_FOUNDRY.isActive(context.getEnvironment()));
				context.getBean(Scheduler.class);
			});
		}
	}

	@TestPropertySource(properties = { "spring.cloud.dataflow.features.schedules-enabled=true" })
	public static class LocalSchedulerTests extends AbstractSchedulerPerPlatformTest {

		@Test
		public void testLocalSchedulerEnabled() {
			assertFalse(context.getEnvironment().containsProperty("kubernetes_service_host"), "K8s should be disabled");
			assertFalse(CloudPlatform.CLOUD_FOUNDRY.isActive(context.getEnvironment()), "CF should be disabled");

			Scheduler scheduler = context.getBean(Scheduler.class);

			assertNotNull(scheduler);
			assertTrue(scheduler.getClass().getName().contains("LocalSchedulerAutoConfiguration"));
		}
	}

	@TestPropertySource(properties = { "spring.cloud.dataflow.features.schedules-enabled=true",
			"kubernetes_service_host=dummy" })
	public static class KubernetesSchedulerActivatedTests extends AbstractSchedulerPerPlatformTest {

		@Test
		public void testKubernetesSchedulerEnabled() {
			assertTrue(context.getEnvironment().containsProperty("kubernetes_service_host"), "K8s should be enabled");
			assertFalse(CloudPlatform.CLOUD_FOUNDRY.isActive(context.getEnvironment()), "CF should be disabled");


			KubernetesSchedulerProperties props = context.getBean(KubernetesSchedulerProperties.class);
			assertNotNull(props);
		}

	}

	@TestPropertySource(properties = { "spring.cloud.dataflow.features.schedules-enabled=true",
			"VCAP_APPLICATION=\"{\"instance_id\":\"123\"}\"" })
	public static class CloudFoundrySchedulerActivatedTests extends AbstractSchedulerPerPlatformTest {

		@Test
		public void testCloudFoundryScheudlerEnabled() {
			assertFalse(context.getEnvironment().containsProperty("kubernetes_service_host"), "K8s should be disabled");
			assertTrue(CloudPlatform.CLOUD_FOUNDRY.isActive(context.getEnvironment()), "CF should be enabled");

		}
	}
}
