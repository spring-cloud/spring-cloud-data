/*
 * Copyright 2018-2024 the original author or authors.
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
package org.springframework.cloud.dataflow.common.test.docker.compose.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.dataflow.common.test.docker.compose.connection.Container;
import org.springframework.cloud.dataflow.common.test.docker.compose.connection.ContainerName;
import org.springframework.cloud.dataflow.common.test.docker.compose.connection.DockerMachine;
import org.springframework.cloud.dataflow.common.test.docker.compose.connection.DockerPort;
import org.springframework.cloud.dataflow.common.test.docker.compose.connection.Ports;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.dataflow.common.test.docker.compose.execution.DockerComposeExecArgument.arguments;
import static org.springframework.cloud.dataflow.common.test.docker.compose.execution.DockerComposeExecOption.options;

class DockerComposeTests {

    private DockerComposeExecutable executor = mock(DockerComposeExecutable.class);
    private DockerMachine dockerMachine = mock(DockerMachine.class);
    private DockerCompose compose = new DefaultDockerCompose(executor, dockerMachine);
    private Process executedProcess = mock(Process.class);
    private Container container = mock(Container.class);

    @BeforeEach
    void prepareForTest() throws IOException {
        when(dockerMachine.getIp()).thenReturn("0.0.0.0");
        when(executor.commandName()).thenReturn("docker-compose");
        when(executor.execute(any())).thenReturn(executedProcess);
        when(executor.execute(any(String[].class))).thenReturn(executedProcess);
        when(executedProcess.getInputStream()).thenReturn(toInputStream("0.0.0.0:7000->7000/tcp"));
        when(executedProcess.exitValue()).thenReturn(0);
        when(container.getContainerName()).thenReturn("my-container");
    }

    @Test
    void callDockerComposeUpWithDaemonFlagOnUp() throws Exception {
        compose.up();
        verify(executor).execute("up", "-d");
    }

    @Test
    void callDockerComposeRmWithForceAndVolumeFlagsOnRm() throws Exception {
        compose.rm();
        verify(executor).execute("rm", "--force", "-v");
    }

    @Test
    void callDockerComposeStopOnStop() throws Exception {
        compose.stop(container);
        verify(executor).execute("stop", "my-container");
    }

    @Test
    void callDockerComposeStartOnStart() throws Exception {
        compose.start(container);
        verify(executor).execute("start", "my-container");
    }

    @Test
    void parseAndReturnsContainerNamesOnPs() throws Exception {
        when(executedProcess.getInputStream()).thenReturn(toInputStream("ps\n----\ndir_db_1"));
        List<ContainerName> containerNames = compose.ps();
        verify(executor).execute("ps");
        assertThat(containerNames, contains(ContainerName.builder().semanticName("db").rawName("dir_db_1").build()));
    }

    @Test
    void callDockerComposeWithNoColourFlagOnLogs() throws IOException {
        when(executedProcess.getInputStream()).thenReturn(
                toInputStream("id"),
                toInputStream("docker-compose version 1.5.6, build 1ad8866"),
                toInputStream("logs"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        compose.writeLogs("db", output);
        verify(executor).execute("logs", "--no-color", "db");
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8), is("logs"));
    }

    @Test
    void callDockerComposeWithNoContainerOnLogs() throws IOException {
        reset(executor);
        Process mockIdProcess = mock(Process.class);
        when(mockIdProcess.exitValue()).thenReturn(0);
        InputStream emptyStream = toInputStream("");
        when(mockIdProcess.getInputStream()).thenReturn(emptyStream, emptyStream, emptyStream, toInputStream("id"));
        Process mockVersionProcess = mock(Process.class);
        when(mockVersionProcess.exitValue()).thenReturn(0);
        when(mockVersionProcess.getInputStream()).thenReturn(toInputStream("docker-compose version 1.5.6, build 1ad8866"));
        when(executor.execute("ps", "-q", "db")).thenReturn(mockIdProcess);
        when(executor.execute("-v")).thenReturn(mockVersionProcess);
        when(executor.execute("logs", "--no-color", "db")).thenReturn(executedProcess);
        when(executedProcess.getInputStream()).thenReturn(toInputStream("logs"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        compose.writeLogs("db", output);
        verify(executor, times(4)).execute("ps", "-q", "db");
        verify(executor).execute("logs", "--no-color", "db");
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8), is("logs"));
    }

    @Test
    void callDockerComposeWithTheFollowFlagWhenVersionIsAtLeast_1_7_0_OnLogs() throws IOException {
        when(executedProcess.getInputStream()).thenReturn(
                toInputStream("id"),
                toInputStream("docker-compose version 1.7.0, build 1ad8866"),
                toInputStream("logs"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        compose.writeLogs("db", output);
        verify(executor).execute("logs", "--no-color", "--follow", "db");
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8), is("logs"));
    }

    @Test
    void throwExceptionWhenKillExitsWithANonZeroExitCode() {
        when(executedProcess.exitValue()).thenReturn(1);
        assertThatExceptionOfType(DockerExecutionException.class)
                .isThrownBy(() -> compose.kill())
                .withMessageStartingWith("'docker-compose kill' returned exit code 1");
    }

    @Test
    void notThrowExceptionWhenDownFailsBecauseTheCommandDoesNotExist() throws Exception {
        when(executedProcess.exitValue()).thenReturn(1);
        when(executedProcess.getInputStream()).thenReturn(toInputStream("No such command: down"));
        compose.down();
    }

    @Test
    void throwExceptionWhenDownFailsForAReasonOtherThanTheCommandNotBeingPresent() {
        when(executedProcess.exitValue()).thenReturn(1);
        when(executedProcess.getInputStream()).thenReturn(toInputStream(""));
        assertThatExceptionOfType(DockerExecutionException.class)
                .isThrownBy(() -> compose.down())
                .withMessageStartingWith("'docker-compose down --volumes' returned exit code 1");
    }

    @Test
    void useTheRemoveVolumesFlagWhenDownExists() throws Exception {
        compose.down();
        verify(executor).execute("down", "--volumes");
    }

    @Test
    void parseThePsOutputOnPorts() throws Exception {
        Ports ports = compose.ports("db");
        verify(executor).execute("ps", "db");
        assertThat(ports, is(new Ports(new DockerPort("0.0.0.0", 7000, 7000))));
    }

    @Test
    void throwIllegalStateExceptionWhereThereIsNoContainerFoundForPorts() {
        when(executedProcess.getInputStream()).thenReturn(toInputStream(""));
        assertThatIllegalStateException()
                .isThrownBy(() -> compose.ports("db"))
                .withMessage("No container with name 'db' found");
    }

    @Test
    void failOnDockerComposeExecCommandIfVersionIsNotAtLeast_1_7_0() {
        when(executedProcess.getInputStream()).thenReturn(toInputStream("docker-compose version 1.5.6, build 1ad8866"));
        assertThatIllegalStateException()
                .isThrownBy(() -> compose.exec(options("-d"), "container_1", arguments("ls")))
                .withMessage("You need at least docker-compose 1.7 to run docker-compose exec");
    }
    
    @Test
    void passConcatenatedArgumentsToExecutorOnDockerComposeExec() throws Exception {
        when(executedProcess.getInputStream()).thenReturn(toInputStream("docker-compose version 1.7.0rc1, build 1ad8866"));
        compose.exec(options("-d"), "container_1", arguments("ls"));
        verify(executor, times(1)).execute("exec", "-T", "-d", "container_1", "ls");
    }

    @Test
    void passConcatenatedArgumentsToExecutorOnDockerComposeRun() throws Exception {
        compose.run(DockerComposeRunOption.options("-d"), "container_1", DockerComposeRunArgument.arguments("ls"));
        verify(executor, times(1)).execute("run", "-d", "container_1", "ls");
    }

    @Test
    void returnTheOutputFromTheExecutedProcessOnDockerComposeExec() throws Exception {
        String lsString = String.format("-rw-r--r--  1 user  1318458867  11326 Mar  9 17:47 LICENSE%n"
                                        + "-rw-r--r--  1 user  1318458867  12570 May 12 14:51 README.md");
        String versionString = "docker-compose version 1.7.0rc1, build 1ad8866";
        DockerComposeExecutable processExecutor = mock(DockerComposeExecutable.class);
        addProcessToExecutor(processExecutor, processWithOutput(versionString), "-v");
        addProcessToExecutor(processExecutor, processWithOutput(lsString), "exec", "-T", "container_1", "ls", "-l");
        DockerCompose processCompose = new DefaultDockerCompose(processExecutor, dockerMachine);
        assertThat(processCompose.exec(options(), "container_1", arguments("ls", "-l")), is(lsString));
    }

    @Test
    void returnTheOutputFromTheExecutedProcessOnDockerComposeRun() throws Exception {
        String lsString = String.format("-rw-r--r--  1 user  1318458867  11326 Mar  9 17:47 LICENSE%n"
                                        + "-rw-r--r--  1 user  1318458867  12570 May 12 14:51 README.md");
        DockerComposeExecutable processExecutor = mock(DockerComposeExecutable.class);
        addProcessToExecutor(processExecutor, processWithOutput(lsString), "run", "-it", "container_1", "ls", "-l");
        DockerCompose processCompose = new DefaultDockerCompose(processExecutor, dockerMachine);
        assertThat(processCompose.run(DockerComposeRunOption.options("-it"), "container_1", DockerComposeRunArgument.arguments("ls", "-l")), is(lsString));
    }

    private static void addProcessToExecutor(DockerComposeExecutable dockerComposeExecutable, Process process, String... commands) throws Exception {
        when(dockerComposeExecutable.execute(commands)).thenReturn(process);
    }

    private static Process processWithOutput(String output) {
        Process mockedProcess = mock(Process.class);
        when(mockedProcess.getInputStream()).thenReturn(toInputStream(output));
        when(mockedProcess.exitValue()).thenReturn(0);
        return mockedProcess;
    }

}
