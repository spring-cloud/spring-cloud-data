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

package org.springframework.cloud.dataflow.completion;

import java.util.List;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.cloud.stream.configuration.metadata.AppJarLauncher;

/**
 * Extension of {@link AppJarLauncher} used for exposing a {@link ClassLoader}
 * for the provided {@link Archive}.
 */
class ClassLoaderExposingJarLauncher extends AppJarLauncher {

	public ClassLoaderExposingJarLauncher(Archive archive) {
		super(archive);
	}

	protected ClassLoader createClassLoader() throws Exception {
		List<Archive> classPathArchives = getClassPathArchives();
		return createClassLoader(classPathArchives);
	}
}
