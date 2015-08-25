/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.data.module.deployer.local;

import java.net.Inet4Address;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.data.core.ModuleDeploymentId;
import org.springframework.cloud.data.core.ModuleDeploymentRequest;
import org.springframework.cloud.data.module.ModuleStatus;
import org.springframework.cloud.data.module.deployer.ModuleDeployer;
import org.springframework.cloud.stream.module.launcher.ModuleLauncher;
import org.springframework.util.Assert;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mark Fisher
 * @author Marius Bogoevici
 */
public class LocalModuleDeployer implements ModuleDeployer {

	private static final Logger logger = LoggerFactory.getLogger(LocalModuleDeployer.class);

	private final ModuleLauncher launcher;

	private final Map<ModuleDeploymentId, URL> deployedModules = new HashMap<>();

	private final RestTemplate restTemplate = new RestTemplate();

	public LocalModuleDeployer(ModuleLauncher launcher) {
		Assert.notNull(launcher, "Module launcher cannot be null");
		this.launcher = launcher;
	}

	@Override
	public ModuleDeploymentId deploy(ModuleDeploymentRequest request) {
		String module = request.getCoordinates().toString();
		if (request.getCount() != 1) {
			logger.warn(String.format("%s only supports a single instance per module; ignoring count=%d for %s",
					this.getClass().getSimpleName(), request.getCount(), request.getDefinition().getLabel()));			
		}
		List<String> args = new ArrayList<>();
		for (Map.Entry<String, String> entry : request.getDefinition().getParameters().entrySet()) {
			args.add(String.format("--%s.%s=%s", module, entry.getKey(), entry.getValue()));
		}
		for (Map.Entry<String, String> entry: request.getDeploymentProperties().entrySet()) {
			args.add(String.format("--%s.%s=%s", module, entry.getKey(), entry.getValue()));
		}
		logger.info("deploying module: " + module);
		int port = SocketUtils.findAvailableTcpPort(8080);
		URL moduleUrl;
		try {
			moduleUrl = new URL("http", Inet4Address.getLocalHost().getHostAddress(), port, "");
		}
		catch (Exception e) {
			throw new IllegalStateException("failed to determine URL for module: " + module, e);
		}
		args.add(String.format("--%s.server.port=%d", module, port));
		args.add(String.format("--%s.endpoints.shutdown.enabled=true", module));
		launcher.launch(new String[] { module }, args.toArray(new String[args.size()]));
		ModuleDeploymentId id = new ModuleDeploymentId(request.getDefinition().getGroup(),
				request.getDefinition().getLabel());
		this.deployedModules.put(id, moduleUrl);
		return id;
	}

	@Override
	public void undeploy(ModuleDeploymentId id) {
		URL url = this.deployedModules.get(id);
		if (url != null) {
			logger.info("undeploying module: " + id);
			this.restTemplate.postForObject(url + "/shutdown", null, String.class);
			this.deployedModules.remove(id);
		}
	}

	@Override
	public ModuleStatus status(ModuleDeploymentId id) {
		boolean deployed = this.deployedModules.containsKey(id);
		LocalModuleInstanceStatus status = new LocalModuleInstanceStatus(id.toString(), deployed, null);
		return ModuleStatus.of(id).with(status).build();
	}

	@Override
	public Map<ModuleDeploymentId, ModuleStatus> status() {
		Map<ModuleDeploymentId, ModuleStatus> statusMap = new HashMap<>();
		for (ModuleDeploymentId id : this.deployedModules.keySet()) {
			statusMap.put(id, status(id));
		}
		return statusMap;
	}
}
