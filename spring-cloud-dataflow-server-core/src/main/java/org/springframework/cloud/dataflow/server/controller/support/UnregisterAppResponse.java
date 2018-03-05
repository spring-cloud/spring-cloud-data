/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller.support;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * @author Vinicius Carvalho
 *
 */
public class UnregisterAppResponse {

	public UnregisterAppResponse(RegisteredApp app, String status, String message) {
		this.app = app;
		this.status = status;
		this.message = message;
	}

	public UnregisterAppResponse(){}

	@JsonUnwrapped
	private RegisteredApp app;

	private String status;

	private String message;

	public RegisteredApp getApp() {
		return app;
	}

	public void setApp(RegisteredApp app) {
		this.app = app;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
