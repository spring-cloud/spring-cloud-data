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
package org.springframework.cloud.dataflow.common.test.docker.compose.connection.waiting;

import org.joda.time.Duration;
import org.junit.Test;
import org.springframework.cloud.dataflow.common.test.docker.compose.connection.Cluster;
import org.springframework.cloud.dataflow.common.test.docker.compose.connection.ContainerCache;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.dataflow.common.test.docker.compose.connection.waiting.SuccessOrFailure.failure;
import static org.springframework.cloud.dataflow.common.test.docker.compose.connection.waiting.SuccessOrFailure.success;

public class ClusterWaitTests {

    private static final Duration DURATION = Duration.standardSeconds(1);
    private static final String IP = "192.168.100.100";

    private final ContainerCache containerCache = mock(ContainerCache.class);
    private final ClusterHealthCheck clusterHealthCheck = mock(ClusterHealthCheck.class);

    private final Cluster cluster = Cluster.builder()
            .containerCache(containerCache)
            .ip(IP)
            .build();

    @Test public void
    return_when_a_cluster_is_ready() throws InterruptedException {
        when(clusterHealthCheck.isClusterHealthy(cluster)).thenReturn(success());
        ClusterWait wait = new ClusterWait(clusterHealthCheck, DURATION);
        wait.waitUntilReady(cluster);
    }

    @Test public void
    check_until_a_cluster_is_ready() throws InterruptedException {
        when(clusterHealthCheck.isClusterHealthy(cluster)).thenReturn(failure("failure!"), success());
        ClusterWait wait = new ClusterWait(clusterHealthCheck, DURATION);
        wait.waitUntilReady(cluster);
        verify(clusterHealthCheck, times(2)).isClusterHealthy(cluster);
    }

    @Test(timeout = 2000L) public void
    timeout_if_the_cluster_is_not_healthy() throws InterruptedException {
        when(clusterHealthCheck.isClusterHealthy(cluster)).thenReturn(failure("failure!"));

        ClusterWait wait = new ClusterWait(clusterHealthCheck, DURATION);

		assertThatIllegalStateException().isThrownBy(() -> wait.waitUntilReady(cluster)).withMessageContaining("failure!");
    }
}
