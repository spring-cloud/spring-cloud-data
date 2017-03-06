/*
 * Copyright 2017 the original author or authors.
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

import static org.springframework.cloud.dataflow.core.dsl.TokenKind.*;

/**
 * An AST node representing a transition found in a parsed composed task specification. A transition
 * is expressed in the form "<tt>STATE->TARGET</tt>". If <tt>STATE</tt> is unquoted it is considered a reference
 * to the exit code of the preceding app (where <tt>*</tt> means 'any exit code'). If <tt>STATE</tt> is quoted it is considered
 * a reference to the exit status of the preceding app (where <tt>'*'</tt> means 'any exit status').
 * TARGET can be either a reference to a label, <tt>:foo</tt>, or a single app name <tt>Foo</tt>.
 *
 * @author Andy Clement
 */
public class TransitionNode extends AstNode {

	public final static String FAIL = "$FAIL";

	public final static String END = "$END";

	private Token statusToken;
	
	// Holds the string value of the token, with any quotes removed
	private String status;
	
	// If true then statusToken was unquoted, indicating it relates to the preceding apps exit code.
	// If false then statusToken was quoted, indicating it relates to the preceding apps exit status.
	private boolean isExitCodeCheck;

	// Either the targetLabel or targetApp is set. Target label is for 'App1 0->:target'
	private Token targetLabel;

	// Either the targetLabel or targetApp is set. Target app is for 'App1 0->App2'
	private Token targetApp;

	/**
	 * Private constructor, use the toXXX factory methods below depending on the
	 * kind of transition specified.
	 * @param statusToken the token representing the status check
	 * @param endPos used to indicate the end of the transition spec in the original source dsl
	 */
	private TransitionNode(Token statusToken, int endPos) {
		super(statusToken.startPos, endPos);
		this.statusToken = statusToken;
		// If it is quoted, strip them off to determine real status
		if (statusToken.isKind(LITERAL_STRING)) {
			isExitCodeCheck = false;
			String quotesUsed = statusToken.data.substring(0, 1);
			this.status = statusToken.data.substring(1, statusToken.data.length() - 1).replace(
					quotesUsed + quotesUsed, quotesUsed);
		}
		else {
			isExitCodeCheck = true;
			if (statusToken.isKind(STAR)) {
				this.status = "*";
			}
			else {
				this.status = this.statusToken.stringValue();
			}
		}
	}

	static TransitionNode toLabelReference(Token transitionOnToken, Token labelReference) {
		TransitionNode t = new TransitionNode(transitionOnToken,labelReference.endPos);
		t.targetLabel = labelReference;
		return t;
	}

	static TransitionNode toAnotherTask(Token transitionOnToken, Token taskName) {
		TransitionNode t = new TransitionNode(transitionOnToken,taskName.endPos);
		t.targetApp = taskName;
		return t;
	}

	@Override
	public String stringify(boolean includePositionInfo) {
		StringBuilder s = new StringBuilder();
		s.append(statusToken.getKind()==STAR?"*":statusToken.stringValue()).append("->");
		if (targetLabel!=null) {
			s.append(":").append(targetLabel.stringValue());
		} else {
			s.append(targetApp.stringValue());
		}
		return s.toString();
	}

	/**
	 * Return the status that should trigger the transition. For an exit code check, this
	 * will be a simple number (or *) and isExitCode will be true. For an exit
	 * status check, this will a simple string (without quotes) and isExitCode will be false.
	 * @return the status to check to trigger the transition
	 */
	public String getStatusToCheck() {
		return status;
	}

	/**
	 * Return the status that should trigger the transition as it would occur in the DSL. For an 
	 * exit code check, this will be a simple number (or *) and isExitCode will be true. For an exit
	 * status check, this will a simple string (*with* quotes) and isExitCode will be false.
	 * @return the status to check to trigger the transition
	 */
	public String getStatusToCheckInDSLForm() {
		if (isExitCodeCheck) {
			return status;
		}
		else {
			return "'"+status+"'";
		}
	}

	/**
	 * The target is either an app or a reference. If it is an app then call <tt>getTargetApp</tt>
	 * otherwise call <tt>getTargetReference</tt>.
	 * @return true if the target is an app
	 */
	public boolean isTargetApp() {
		return targetApp!=null;
	}
	
	public String getTargetLabel() {
		return targetLabel==null?null:targetLabel.stringValue();
	}

	public String getTargetApp() {
		return targetApp==null?null:targetApp.stringValue();
	}

	/**
	 * Basic names do not need wrapping in quotes but special characters, like
	 * asterisk do. This returns the state name in a form suitable for inclusion
	 * in DSL text (so with the quotes if that's how it was specified
	 * when the Transition object was built).
	 * @return the transition name suitable for inclusion in the DSL
	 */
	public String getStateNameInDSLForm() {
		return statusToken.data;
	}

	/**
	 * Some target names for a transition are 'well known' like $FAIL and $END - these
	 * do not indicate a following job step, they instead indicate a termination state.
	 * @return true if the target of this transition is a special state ($FAIL/$END)
	 */
	public boolean isSpecialTransition() {
		return isFailTransition() || isEndTransition();
	}

	/**
	 * @return is the target of the transition $FAIL
	 */
	public boolean isFailTransition() {
		return getTargetApp().equals(FAIL);
	}

	/**
	 * @return is the target of the transition $END
	 */
	public boolean isEndTransition() {
		return getTargetApp().equals(END);
	}
	
	/**
	 * @return true if the status check is on the exit code, otherwise false (indicating it is an exit status check)
	 */
	public boolean isExitCodeCheck() {
		return isExitCodeCheck;
	}

	/**
	 * @return the appropriate dsl for the transition target depending on how the target was specified
	 */
	public String getTargetDslText() {
		if (targetLabel==null) {
			return targetApp.stringValue();
		}
		else {
			return ":"+targetLabel.stringValue();
		}
	}

	public void accept(ComposedTaskVisitor visitor) {
		boolean cont = visitor.preVisit(this);
		if (!cont) {
			return;
		}
		visitor.visit(this);
		visitor.postVisit(this);		
	}

}
