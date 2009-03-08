/*
 * Copyright 2008-2009 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.store.logging;

import java.util.List;

import voldemort.store.AbstractStoreTest;
import voldemort.store.Store;
import voldemort.store.memory.InMemoryStorageEngine;

/**
 * @author jay
 * 
 */
public class LoggingStoreTest extends AbstractStoreTest<String, String> {

    @Override
    public List<String> getKeys(int numKeys) {
        return getStrings(numKeys, 8);
    }

    @Override
    public Store<String, String> getStore() {
        return new LoggingStore<String, String>(new InMemoryStorageEngine<String, String>("test-store"));
    }

    @Override
    public List<String> getValues(int numValues) {
        return getStrings(numValues, 8);
    }

}
