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
package org.springframework.cloud.dataflow.common.test.docker.compose.logging;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.springframework.cloud.dataflow.common.test.docker.compose.execution.DockerCompose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.dataflow.common.test.docker.compose.matchers.IOMatchers.containsInAnyOrder;
import static org.springframework.cloud.dataflow.common.test.docker.compose.matchers.IOMatchers.fileContainingString;
import static org.springframework.cloud.dataflow.common.test.docker.compose.matchers.IOMatchers.fileWithName;

public class FileLogCollectorTests {

    @Rule
    public TemporaryFolder logDirectoryParent = new TemporaryFolder();

    private final DockerCompose compose = mock(DockerCompose.class);
    private File logDirectory;
    private LogCollector logCollector;

    @Before
    public void before() throws IOException {
        logDirectory = logDirectoryParent.newFolder();
        logCollector = new FileLogCollector(logDirectory);
    }

    @Test
    public void throw_exception_when_created_with_file_as_the_log_directory() throws IOException {
        File file = logDirectoryParent.newFile("cannot-use");
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->  new FileLogCollector(file)).
			withMessageContaining("cannot be a file");
    }

    @Test
    public void create_the_log_directory_if_it_does_not_already_exist() {
        File doesNotExistYetDirectory = logDirectoryParent.getRoot()
                .toPath()
                .resolve("doesNotExist")
                .toFile();
        new FileLogCollector(doesNotExistYetDirectory);
		assertThat(doesNotExistYetDirectory.exists()).isEqualTo(true);
    }

    @Test
    public void throw_exception_when_created_if_the_log_directory_does_not_exist_and_cannot_be_created() {
        File cannotBeCreatedDirectory = cannotBeCreatedDirectory();

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new FileLogCollector(cannotBeCreatedDirectory)).
			withMessageContaining("Error making").withMessageContaining(cannotBeCreatedDirectory.getAbsolutePath());
	}

    @Test
    public void not_collect_any_logs_when_no_containers_are_running() throws IOException, InterruptedException {
        when(compose.services()).thenReturn(Collections.emptyList());
        logCollector.startCollecting(compose);
        logCollector.stopCollecting();
		assertThat(logDirectory).isEmptyDirectory();
    }

    @Test
    public void collect_logs_when_one_container_is_running_and_terminates_before_start_collecting_is_run()
            throws Exception {
        when(compose.services()).thenReturn(Collections.singletonList("db"));
        when(compose.writeLogs(eq("db"), any(OutputStream.class))).thenAnswer(args -> {
            OutputStream outputStream = (OutputStream) args.getArguments()[1];
            IOUtils.write("log", outputStream, Charset.defaultCharset());
            return false;
        });
        logCollector.startCollecting(compose);
        logCollector.stopCollecting();
		assertThat(logDirectory.listFiles()).have(fileWithName("db.log"));
        assertThat(new File(logDirectory, "db.log")).has(fileContainingString("log"));
    }

    @Test
    public void collect_logs_when_one_container_is_running_and_does_not_terminate_until_after_start_collecting_is_run()
            throws Exception {
        when(compose.services()).thenReturn(Collections.singletonList("db"));
        CountDownLatch latch = new CountDownLatch(1);
        when(compose.writeLogs(eq("db"), any(OutputStream.class))).thenAnswer(args -> {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new RuntimeException("Latch was not triggered");
            }
            OutputStream outputStream = (OutputStream) args.getArguments()[1];
            IOUtils.write("log", outputStream, Charset.defaultCharset());
            return false;
        });
        logCollector.startCollecting(compose);
        latch.countDown();
        logCollector.stopCollecting();
		assertThat(logDirectory.listFiles()).have(fileWithName("db.log"));
        assertThat(new File(logDirectory, "db.log")).is(fileContainingString("log"));
    }

    @Test
    public void collect_logs_when_one_container_is_running_and_does_not_terminate()
            throws IOException, InterruptedException {
        when(compose.services()).thenReturn(Collections.singletonList("db"));
        CountDownLatch latch = new CountDownLatch(1);
        when(compose.writeLogs(eq("db"), any(OutputStream.class))).thenAnswer(args -> {
            OutputStream outputStream = (OutputStream) args.getArguments()[1];
            IOUtils.write("log", outputStream, Charset.defaultCharset());
            try {
                latch.await(1, TimeUnit.SECONDS);
                fail("Latch was not triggered");
            } catch (InterruptedException e) {
                // Success
                return true;
            }
            fail("Latch was not triggered");
            return false;
        });
        logCollector.startCollecting(compose);
        logCollector.stopCollecting();
		assertThat(logDirectory.listFiles()).have(fileWithName("db.log"));
        assertThat(new File(logDirectory, "db.log")).is(fileContainingString("log"));
        latch.countDown();
    }

    @Test
    public void collect_logs_in_parallel_for_two_containers() throws IOException, InterruptedException {
        when(compose.services()).thenReturn(Arrays.asList("db", "db2"));
        CountDownLatch dbLatch = new CountDownLatch(1);
        when(compose.writeLogs(eq("db"), any(OutputStream.class))).thenAnswer(args -> {
            OutputStream outputStream = (OutputStream) args.getArguments()[1];
            IOUtils.write("log", outputStream, Charset.defaultCharset());
            dbLatch.countDown();
            return true;
        });
        CountDownLatch db2Latch = new CountDownLatch(1);
        when(compose.writeLogs(eq("db2"), any(OutputStream.class))).thenAnswer(args -> {
            OutputStream outputStream = (OutputStream) args.getArguments()[1];
            IOUtils.write("other", outputStream, Charset.defaultCharset());
            db2Latch.countDown();
            return true;
        });

        logCollector.startCollecting(compose);
		assertThat(dbLatch.await(1, TimeUnit.SECONDS)).isEqualTo(true);
		assertThat(db2Latch.await(1, TimeUnit.SECONDS)).isEqualTo(true);

		assertThat(logDirectory.listFiles()).has(containsInAnyOrder(fileWithName("db.log"), fileWithName("db2.log")));
        assertThat(new File(logDirectory, "db.log")).is(fileContainingString("log"));
        assertThat(new File(logDirectory, "db2.log")).is(fileContainingString("other"));

        logCollector.stopCollecting();
    }

    @Test
    public void throw_exception_when_trying_to_start_a_started_collector_a_second_time()
            throws IOException, InterruptedException {
        when(compose.services()).thenReturn(Collections.singletonList("db"));
        logCollector.startCollecting(compose);
		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> logCollector.startCollecting(compose)).
			withMessageContaining("Cannot start collecting the same logs twice");
    }

    private static File cannotBeCreatedDirectory() {
        File cannotBeCreatedDirectory = mock(File.class);
        when(cannotBeCreatedDirectory.isFile()).thenReturn(false);
        when(cannotBeCreatedDirectory.mkdirs()).thenReturn(false);
        when(cannotBeCreatedDirectory.getAbsolutePath()).thenReturn("cannot/exist/directory");
        return cannotBeCreatedDirectory;
    }
}
