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

package org.springframework.cloud.dataflow.core.dsl;

import java.util.List;

/**
 * @author Andy Clement
 */
public class StreamNode extends AstNode {

	private final String streamText;

	private final String streamName;

	private final List<ModuleNode> moduleNodes;

	private SourceDestinationNode sourceDestinationNode;

	private SinkDestinationNode sinkDestinationNode;

	public StreamNode(String streamText, String streamName, List<ModuleNode> moduleNodes,
			SourceDestinationNode sourceDestinationNode, SinkDestinationNode sinkDestinationNode) {
		super(moduleNodes.get(0).getStartPos(), moduleNodes.get(moduleNodes.size() - 1).getEndPos());
		this.streamText = streamText;
		this.streamName = streamName;
		this.moduleNodes = moduleNodes;
		this.sourceDestinationNode = sourceDestinationNode;
		this.sinkDestinationNode = sinkDestinationNode;
	}

	/** @inheritDoc */
	@Override
	public String stringify(boolean includePositionalInfo) {
		StringBuilder s = new StringBuilder();
		// s.append("Stream[").append(streamText).append("]");
		s.append("[");
		if (getStreamName() != null) {
			s.append(getStreamName()).append(" = ");
		}
		if (sourceDestinationNode != null) {
			s.append(sourceDestinationNode.stringify(includePositionalInfo));
		}
		for (ModuleNode moduleNode : moduleNodes) {
			s.append(moduleNode.stringify(includePositionalInfo));
		}
		if (sinkDestinationNode != null) {
			s.append(sinkDestinationNode.stringify(includePositionalInfo));
		}
		s.append("]");
		return s.toString();
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		if (getStreamName() != null) {
			s.append(getStreamName()).append(" = ");
		}
		if (sourceDestinationNode != null) {
			s.append(sourceDestinationNode.toString());
		}
		for (int m = 0; m < moduleNodes.size(); m++) {
			ModuleNode moduleNode = moduleNodes.get(m);
			s.append(moduleNode.toString());
			if (m + 1 < moduleNodes.size()) {
				s.append(" | ");
			}
		}
		if (sinkDestinationNode != null) {
			s.append(sinkDestinationNode.toString());
		}
		return s.toString();
	}

	public List<ModuleNode> getModuleNodes() {
		return moduleNodes;
	}

	public SourceDestinationNode getSourceDestinationNode() {
		return sourceDestinationNode;
	}

	public SinkDestinationNode getSinkDestinationNode() {
		return sinkDestinationNode;
	}

	public String getStreamName() {
		return streamName;
	}

	/**
	 * Find the first reference to the named module in the stream. If the same module is referred to multiple times the
	 * secondary references cannot be accessed via this method.
	 *
	 * @return the first occurrence of the named module in the stream
	 */
	public ModuleNode getModule(String moduleName) {
		for (ModuleNode moduleNode : moduleNodes) {
			if (moduleNode.getName().equals(moduleName)) {
				return moduleNode;
			}
		}
		return null;
	}

	public int getIndexOfLabel(String labelOrModuleName) {
		for (int m = 0; m < moduleNodes.size(); m++) {
			ModuleNode moduleNode = moduleNodes.get(m);
			if (moduleNode.getLabelName().equals(labelOrModuleName)) {
				return m;
			}
		}
		return -1;
	}

	public String getStreamData() {
		return toString();
	}

	public String getStreamText() {
		return this.streamText;
	}

	public String getName() {
		return this.streamName;
	}

}
