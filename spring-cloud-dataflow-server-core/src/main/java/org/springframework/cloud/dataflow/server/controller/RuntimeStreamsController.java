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

package org.springframework.cloud.dataflow.server.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.dataflow.server.controller.support.StreamStatus;
import org.springframework.cloud.dataflow.server.stream.StreamDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes runtime status of deployed streams.
 *
 * @author Christian Tzolov
 */
@RestController
@RequestMapping("/runtime/streams")
public class RuntimeStreamsController {

	public static final String ATTRIBUTE_SKIPPER_APPLICATION_NAME = "skipper.application.name";
	public static final String ATTRIBUTE_SKIPPER_RELEASE_VERSION = "skipper.release.version";
	public static final String ATTRIBUTE_GUID = "guid";

	private static Log logger = LogFactory.getLog(RuntimeStreamsController.class);

	private final StreamDeployer streamDeployer;

	/**
	 * Construct a new runtime apps controller.
	 * @param streamDeployer the deployer this controller will use to get the status of
	 * deployed stream apps
	 */
	public RuntimeStreamsController(StreamDeployer streamDeployer) {
		Assert.notNull(streamDeployer, "StreamDeployer must not be null");
		this.streamDeployer = streamDeployer;
	}

	@RequestMapping
	public List<StreamStatus> streamStatus(@RequestParam("names") String[] streamNames) {
		try {
			return Stream.of(streamNames).map(this::toStreamStatus).collect(Collectors.toList());
		}
		catch (Exception e) {
			logger.error("Failed to retrieve any metrics", e);
		}
		return Collections.emptyList();
	}

	private StreamStatus toStreamStatus(String streamName) {
		StreamStatus streamStatus = new StreamStatus();
		streamStatus.setName(streamName);
		streamStatus.setApplications(new ArrayList<>());

		List<AppStatus> appStatuses = this.streamDeployer.getStreamStatuses(streamName);

		if (!CollectionUtils.isEmpty(appStatuses)) {
			for (AppStatus appStatus : appStatuses) {
				try {
					StreamStatus.Application application = new StreamStatus.Application();
					streamStatus.getApplications().add(application);
					application.setInstances(new ArrayList<>());
					application.setId(appStatus.getDeploymentId());

					for (Map.Entry<String, AppInstanceStatus> instanceEntry : appStatus.getInstances().entrySet()) {
						AppInstanceStatus appInstanceStatus = instanceEntry.getValue();
						StreamStatus.Instance instance = new StreamStatus.Instance();
						application.getInstances().add(instance);

						instance.setId(appInstanceStatus.getId());
						instance.setGuid(getAppInstanceGuid(appInstanceStatus));
						instance.setState(appInstanceStatus.getState().name());
						instance.setProperties(Collections.emptyMap());

						application.setName(appInstanceStatus.getAttributes().get(ATTRIBUTE_SKIPPER_APPLICATION_NAME));
						streamStatus.setVersion(appInstanceStatus.getAttributes().get(ATTRIBUTE_SKIPPER_RELEASE_VERSION));
					}
				}
				catch (Throwable throwable) {
					logger.warn("Failed to retrieve runtime status for " + appStatus.getDeploymentId(), throwable);
				}
			}
		}
		return streamStatus;
	}

	private String getAppInstanceGuid(AppInstanceStatus instance) {
		return instance.getAttributes().containsKey(ATTRIBUTE_GUID) ?
				instance.getAttributes().get(ATTRIBUTE_GUID) : instance.getId();
	}
}
