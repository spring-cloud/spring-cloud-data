/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.admin.service.NoSuchStepExecutionException;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.launch.NoSuchJobInstanceException;
import org.springframework.boot.actuate.endpoint.mvc.MetricsMvcEndpoint;
import org.springframework.cloud.dataflow.server.repository.DuplicateStreamDefinitionException;
import org.springframework.cloud.dataflow.server.repository.DuplicateTaskException;
import org.springframework.cloud.dataflow.server.repository.NoSuchStreamDefinitionException;
import org.springframework.cloud.dataflow.server.repository.NoSuchTaskDefinitionException;
import org.springframework.cloud.dataflow.server.repository.NoSuchTaskExecutionException;
import org.springframework.hateoas.VndErrors;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Apply common behavior (exception handling etc.,) to all the REST controllers.
 *
 * @author Eric Bottard
 * @author Gunnar Hillert
 * @author Ilayaperumal Gopinathan
 */
@ControllerAdvice
public class RestControllerAdvice {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Handles the general error case. Report server-side error.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public VndErrors onException(Exception e) {
		String logref = logError(e);
		String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
		return new VndErrors(logref, msg);
	}

	@ExceptionHandler({DuplicateStreamDefinitionException.class, DuplicateTaskException.class})
	@ResponseStatus(HttpStatus.CONFLICT)
	@ResponseBody
	public VndErrors onConflictException(Exception e) {
		String logref = logError(e);
		String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
		return new VndErrors(logref, msg);
	}

	@ExceptionHandler({NoSuchStreamDefinitionException.class,
			NoSuchTaskDefinitionException.class,
			NoSuchTaskExecutionException.class,
			NoSuchJobExecutionException.class,
			NoSuchJobInstanceException.class,
			NoSuchJobException.class,
			NoSuchStepExecutionException.class,
			MetricsMvcEndpoint.NoSuchMetricException.class})
	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ResponseBody
	public VndErrors onNotFoundException(Exception e) {
		String logref = logError(e);
		String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
		return new VndErrors(logref, msg);
	}



	private String logError(Throwable t) {
		logger.error("Caught exception while handling a request", t);
		return t.getClass().getSimpleName();
	}
}
