/**
 * Copyright (c) 2008, SnakeYAML
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.mcmanus.scm.stash.hook;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * A SnakeYAML Constructor which ignores and value tags.
 *
 * Slightly adapted version of SnakeYAML's examples.IgnoreTagsExampleTest.MyConstructor.
 */
public class YamlValidatorConstructor extends Constructor {

    public YamlValidatorConstructor(LoaderOptions loadingConfig) {
        super(loadingConfig);

        this.yamlConstructors.put(null, new IgnoringConstruct());
    }

    private class IgnoringConstruct extends AbstractConstruct {

        @Override
        public Object construct(Node node) {
            switch (node.getNodeId()) {
                case scalar:
                    return yamlConstructors.get(Tag.STR).construct(node);
                case sequence:
                    return yamlConstructors.get(Tag.SEQ).construct(node);
                case mapping:
                    return yamlConstructors.get(Tag.MAP).construct(node);
                default:
                    throw new YAMLException("Unexpected node");
            }
        }

    }
}
