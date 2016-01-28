/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.dataflow.admin.config;

import static org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.HAL;

import java.sql.SQLException;
import java.util.Arrays;

import javax.sql.DataSource;

import org.h2.tools.Server;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.repository.MetricRepository;
import org.springframework.boot.actuate.metrics.repository.redis.RedisMetricRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.admin.completion.TapOnChannelExpansionStrategy;
import org.springframework.cloud.dataflow.admin.repository.InMemoryStreamDefinitionRepository;
import org.springframework.cloud.dataflow.admin.repository.InMemoryTaskDefinitionRepository;
import org.springframework.cloud.dataflow.admin.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.admin.repository.TaskDatabaseInitializer;
import org.springframework.cloud.dataflow.admin.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.artifact.registry.ArtifactRegistry;
import org.springframework.cloud.dataflow.artifact.registry.RedisArtifactRegistry;
import org.springframework.cloud.dataflow.completion.CompletionConfiguration;
import org.springframework.cloud.dataflow.completion.RecoveryStrategy;
import org.springframework.cloud.stream.module.metrics.FieldValueCounterRepository;
import org.springframework.cloud.stream.module.metrics.RedisFieldValueCounterRepository;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.support.JdbcTaskExplorerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Configuration for admin application context. This includes support
 * for the REST API framework configuration.
 *
 * @author Mark Fisher
 * @author Marius Bogoevici
 * @author Patrick Peralta
 * @author Thomas Risberg
 * @author Janne Valkealahti
 * @author Glenn Renfro
 */
@Configuration
@EnableHypermediaSupport(type = HAL)
@EnableSpringDataWebSupport
@Import(CompletionConfiguration.class)
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfiguration {

	protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(AdminConfiguration.class);

	@Value("${spring.datasource.url:#{null}}")
	private String dataSourceUrl;

	@Bean
	public MetricRepository metricRepository(RedisConnectionFactory redisConnectionFactory) {
		return new RedisMetricRepository(redisConnectionFactory);
	}

	@Bean
	public FieldValueCounterRepository fieldValueCounterReader(RedisConnectionFactory redisConnectionFactory) {
		return new RedisFieldValueCounterRepository(redisConnectionFactory, new RetryTemplate());
	}

	@Bean
	public StreamDefinitionRepository streamDefinitionRepository() {
		return new InMemoryStreamDefinitionRepository();
	}

	@Bean
	public TaskDefinitionRepository taskDefinitionRepository() {
		return new InMemoryTaskDefinitionRepository();
	}

	@Bean
	public ArtifactRegistry artifactRegistry(RedisConnectionFactory redisConnectionFactory) {
		return new RedisArtifactRegistry(redisConnectionFactory);
	}

	@Bean
	public ArtifactRegistryPopulator artifactRegistryPopulator(ArtifactRegistry artifactRegistry) {
		return new ArtifactRegistryPopulator(artifactRegistry);
	}

	@Bean
	public HttpMessageConverters messageConverters() {
		return new HttpMessageConverters(
				// Prevent default converters
				false,
				// Have Jackson2 converter as the sole converter
				Arrays.<HttpMessageConverter<?>>asList(new MappingJackson2HttpMessageConverter()));
	}

	@Bean
	public WebMvcConfigurer configurer() {
		return new WebMvcConfigurerAdapter() {

			@Override
			public void configurePathMatch(PathMatchConfigurer configurer) {
				configurer.setUseSuffixPatternMatch(false);
			}
		};
	}

	@Bean
	public RecoveryStrategy tapOnChannelExpansionStrategy() {
		return new TapOnChannelExpansionStrategy();
	}

	@Bean
	public TaskExplorer taskExplorer(DataSource dataSource) {
		JdbcTaskExplorerFactoryBean factoryBean =
				new JdbcTaskExplorerFactoryBean(dataSource);
		return factoryBean.getObject();
	}

	@Bean(destroyMethod = "stop")
	@ConditionalOnExpression("#{'${spring.datasource.url:}'.startsWith('jdbc:h2:tcp://localhost:') && '${spring.datasource.url:}'.contains('/mem:')}")
	public Server initH2TCPServer() {
		Server server = null;
		logger.info("Starting H2 Server with URL: " + dataSourceUrl);
		try {
			server = Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort",
					getH2Port(dataSourceUrl)).start();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}

		return server;
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.task.repo.initialize",
			havingValue = "true", matchIfMissing = true)
	public TaskDatabaseInitializer taskDatabaseInitializer(){
		return new TaskDatabaseInitializer();
	}

	private String getH2Port(String url){
		String[] tokens = StringUtils.tokenizeToStringArray(url,":");
		Assert.isTrue(tokens.length >= 5, "URL not properly formatted");
		return tokens[4].substring(0,tokens[4].indexOf("/"));
	}
}
