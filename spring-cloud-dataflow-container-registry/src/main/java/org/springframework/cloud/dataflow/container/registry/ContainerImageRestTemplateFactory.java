/*
 * Copyright 2021-2024 the original author or authors.
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

package org.springframework.cloud.dataflow.container.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.transport.ProxyProvider;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ReactorNettyClientRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * On demand creates a cacheable {@link RestTemplate} instances for the purpose of the Container Registry access.
 * Created RestTemplates can be configured to use Http Proxy and/or bypassing the SSL verification.
 *
 * For configuring a Http Proxy in need to:
 *  1. Add http proxy configuration using the spring.cloud.dataflow.container.httpProxy.* properties.
 *  2. For every {@link ContainerRegistryConfiguration} that has to interact via the http proxy set the use-http-proxy
 *     flag to true. For example:
 *     <code>spring.cloud.dataflow.container.registry-configurations[reg-name].use-http-proxy=ture</code>
 *
 * Following example configures the default (e.g. DockerHub) registry to use the HTTP Proxy (my-proxy.test:8080)
 * while the dockerhub-mirror and the private-snapshots registry configurations allow direct communication:
 * <code>
 *     spring:
 *   cloud:
 *     dataflow:
 *       container:
 *         httpProxy:
 *           host: my-proxy.test
 *           port: 8080
 *         registry-configurations:
 *           default:
 *             use-http-proxy: true
 *           dockerhub-mirror:
 *             authorization-type: anonymous
 *             registry-host: containerhub.aaa.bbb.ccc:8888
 *           private-snapshots:
 *             authorization-type: anonymous
 *             registry-host: containerhub.aaa.bbb.ccc:7777
 * </code>
 *
 * @author Christian Tzolov
 * @author Cheng Guan Poh
 * @author Corneil du Plessis
 */
public class ContainerImageRestTemplateFactory {

	private static final String CUSTOM_REGISTRY = "custom-registry";
	private static final String AMZ_CREDENTIAL = "X-Amz-Credential";
	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String AZURECR_URI_SUFFIX = "azurecr.io";
	private static final String BASIC_AUTH = "Basic";

	private final RestTemplateBuilder restTemplateBuilder;

	private final ContainerRegistryProperties properties;

	/**
	 * Depends on the skipSslVerification and withHttpProxy and extra map with multiple configurations might be
	 * used at the same time for interacting with different container registries.
	 * The cache map allows reusing the RestTemplates for given withHttpProxy and skipSslVerification and extra map combination.
	 */
	private final ConcurrentHashMap<CacheKey, RestTemplate> restTemplateCache;

	/**
	 * Unique key for any withHttpProxy and skipSslVerification combination.
	 */
	record CacheKey(boolean skipSslVerification, boolean withHttpProxy, Map<String, String> extra) {};

	public ContainerImageRestTemplateFactory(RestTemplateBuilder restTemplateBuilder, ContainerRegistryProperties properties) {
		this.restTemplateBuilder = restTemplateBuilder;
		this.properties = properties;
		this.restTemplateCache = new ConcurrentHashMap();
	}

	/**
	 * Obtain a configured RestTemplate for interacting with container registry.
	 * @param skipSslVerification indicates we want to trust all certificates.
	 * @param withHttpProxy indicates we want to use configured proxy.
	 * @return A configured RestTemplate with the given ssl and proxy settings.
	 */
	public RestTemplate getContainerRestTemplate(boolean skipSslVerification, boolean withHttpProxy) {
		return this.getContainerRestTemplate(skipSslVerification, withHttpProxy, Collections.emptyMap());
	}

	/**
	 * Obtain a configured RestTemplate for interacting with container registry.
	 * @param skipSslVerification indicates that we want to trust all certificates.
	 * @param withHttpProxy indicates we want to use the configure proxy host and port.
	 * @param extra by adding entry custom-registry=registry-domain we expect to remove Authorization headers.
	 * @return A configured RestTemplate with the given ssl and proxy and extra settings.
	 */
	public RestTemplate getContainerRestTemplate(boolean skipSslVerification, boolean withHttpProxy, Map<String, String> extra) {
		var cacheKey = new CacheKey(skipSslVerification, withHttpProxy, new HashMap<>(extra));
		try {
			return this.restTemplateCache.computeIfAbsent(cacheKey, (key) -> createContainerRestTemplate(key.skipSslVerification(), key.withHttpProxy(), key.extra()));
		}
		catch (Exception e) {
			throw new ContainerRegistryException("Failed to create Container Image RestTemplate for disableSsl:" + skipSslVerification + ", httpProxy:" + withHttpProxy, e);
		}
	}

	private RestTemplate createContainerRestTemplate(boolean skipSslVerification, boolean withHttpProxy, Map<String, String> extra) {
		HttpClient client = httpClientBuilder(skipSslVerification, extra);
		return initRestTemplate(client, withHttpProxy, extra);
	}

	/**
	 * Amazon, Azure and Custom Container Registry services require special treatment for the Authorization headers when the
	 * HTTP request are forwarded to 3rd party services.
	 *
	 * Amazon:
	 *   The Amazon S3 API supports two Authentication Methods (https://amzn.to/2Dg9sga):
	 *   (1) HTTP Authorization header and (2) Query string parameters (often referred to as a pre-signed URL).
	 *
	 *   But only one auth mechanism is allowed at a time. If the http request contains both an Authorization header and
	 *   an pre-signed URL parameters then an error is thrown.
	 *
	 *   Container Registries often use AmazonS3 as a backend object store. If HTTP Authorization header
	 *   is used to authenticate with the Container Registry and then this registry redirect the request to a S3 storage
	 *   using pre-signed URL authentication, the redirection will fail.
	 *
	 *   Solution is to implement a HTTP redirect strategy that removes the original Authorization headers when the request is
	 *   redirected toward an Amazon signed URL.
	 *
	 * Azure:
	 *   Azure have same type of issues as S3 so header needs to be dropped as well.
	 *   (https://docs.microsoft.com/en-us/azure/container-registry/container-registry-faq#authentication-information-is-not-given-in-the-correct-format-on-direct-rest-api-calls)
	 *
	 * Custom:
	 *   Custom Container Registry may have same type of issues as S3 so header needs to be dropped as well.
	 */
	private HttpClient httpClientBuilder(boolean skipSslVerification, Map<String, String> extra) {

		try {
			SslContextBuilder builder = skipSslVerification
					? SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
					: SslContextBuilder.forClient();
			SslContext sslContext = builder.build();
			HttpClient client = HttpClient.create().secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));

			return client.followRedirect(true, (entries, httpClientRequest) -> {
				if (shouldRemoveAuthorization(httpClientRequest, extra)) {
					HttpHeaders httpHeaders = httpClientRequest.requestHeaders();
					removeAuthorization(httpHeaders);
					removeAuthorization(entries);
					httpClientRequest.headers(httpHeaders);
				}
			});
		}
		catch (SSLException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean shouldRemoveAuthorization(HttpClientRequest request, Map<String, String> extra) {
		HttpMethod method = request.method();
		if(!method.equals(HttpMethod.GET) && !method.equals(HttpMethod.HEAD)) {
			return false;
		}
		if (request.uri().contains(AMZ_CREDENTIAL)) {
			return true;
		}
		if (request.uri().contains(AZURECR_URI_SUFFIX)) {
			return request.requestHeaders()
				.entries()
				.stream()
				.anyMatch(entry -> entry.getKey().equalsIgnoreCase(AUTHORIZATION_HEADER)
						&& entry.getValue().contains(BASIC_AUTH));
		}
		return extra.containsKey(CUSTOM_REGISTRY) && request.uri().contains(extra.get(CUSTOM_REGISTRY));
	}

	private static void removeAuthorization(HttpHeaders headers) {
		Set<String> authHeaders = headers.entries()
				.stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase(AUTHORIZATION_HEADER)).map(Map.Entry::getKey).collect(Collectors.toSet());
		authHeaders.forEach(authHeader -> headers.remove(authHeader));
	}

	private RestTemplate initRestTemplate(HttpClient client, boolean withHttpProxy, Map<String, String> extra) {

		// Set the HTTP proxy if configured.
		if (withHttpProxy) {
			if (!properties.getHttpProxy().isEnabled()) {
				throw new ContainerRegistryException("Registry Configuration uses a HttpProxy but non is configured!");
			}
			ProxyProvider.Builder builder = ProxyProvider.builder()
					.type(ProxyProvider.Proxy.HTTP)
					.host(properties.getHttpProxy().getHost())
					.port(properties.getHttpProxy().getPort());
			client.proxy(typeSpec -> builder.build());
		}

		ClientHttpRequestFactory customRequestFactory = new ReactorNettyClientRequestFactory(client);

		// Extend the MappingJackson2HttpMessageConverter to support the following:
		// - DockerHub response media-type is application/octet-stream although the content is in JSON
		// - Github CR response media-type is text/plain although the content is in JSON
		// - quay.io response media-type is binary/octet-stream although the content is in JSON
		MappingJackson2HttpMessageConverter octetSupportJsonConverter = new MappingJackson2HttpMessageConverter();
		ArrayList<MediaType> mediaTypeList = new ArrayList(octetSupportJsonConverter.getSupportedMediaTypes());
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		mediaTypeList.add(MediaType.APPLICATION_OCTET_STREAM);
		mediaTypeList.add(MediaType.TEXT_PLAIN);
		mediaTypeList.add(MediaType.parseMediaType("binary/octet-stream")); // quay.io responds with binary/octet-stream instead of application/octet-stream
		octetSupportJsonConverter.setSupportedMediaTypes(mediaTypeList);

		return restTemplateBuilder
				.additionalMessageConverters(octetSupportJsonConverter)
				.requestFactory(() -> customRequestFactory)
				.build();
	}
}
