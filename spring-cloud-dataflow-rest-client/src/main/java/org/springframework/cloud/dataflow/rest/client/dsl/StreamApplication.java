package org.springframework.cloud.dataflow.rest.client.dsl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Vinicius Carvalho
 */
public class StreamApplication {

	public StreamApplication(String name) {
		Assert.hasLength(name, "Application name can't be empty");
		this.name = name;
	}

	private final String deployerPrefix = "deployer.%s.";

	private final String name;

	private String label;

	private Map<String, Object> properties = new HashMap<>();

	private Map<String, Object> deploymentProperties = new HashMap<>();

	private ApplicationType type;

	public String getName() {
		return name;
	}

	public StreamApplication label(String label){
		Assert.hasLength(label, "Label can't be empty");
		this.label = label;
		return this;
	}

	public StreamApplication addProperty(String key, Object value){
		this.properties.put(key, value);
		return this;
	}

	public StreamApplication addDeploymentProperty(String key, Object value){
		this.deploymentProperties.put(key, value);
		return this;
	}

	public StreamApplication addProperties(Map<String, Object> properties){
		this.properties.putAll(properties);
		return this;
	}

	public String getLabel() {
		return label;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}

	public StreamApplication type(ApplicationType type){
		this.type = type;
		return this;
	}

	public Map<String, Object> getDeploymentProperties(){
		Map<String, Object> formattedProperties = new HashMap<>();
		String id = StringUtils.isEmpty(label) ? name : label;
		for(Map.Entry<String, Object> entry : deploymentProperties.entrySet()){
			formattedProperties.put(String.format(deployerPrefix, id)+entry.getKey(), entry.getValue());
		}
		return formattedProperties;
	}

	/**
	 * @return Returns the unique identity of an application in a Stream.
	 * This could be name or label: name
	 *
	 */
	public String getIdentity() {
		if(!StringUtils.isEmpty(label)){
			return label+": "+name;
		}else{
			return name;
		}
	}

	public String getDefinition(){
		StringBuilder buffer = new StringBuilder();

		buffer.append(getIdentity());
		for(Map.Entry<String, Object> entry : properties.entrySet()){
			buffer.append(" --"+entry.getKey()+"="+entry.getValue());
		}
		return buffer.toString();
	}

	public ApplicationType getType() {
		return type;
	}

	@Override
	public String toString() {
		return getDefinition();
	}

	public enum ApplicationType {
		SOURCE, PROCESSOR, SINK;
	}
}
