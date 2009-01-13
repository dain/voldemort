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

package voldemort.store.versioned;

import java.util.List;

import voldemort.VoldemortException;
import voldemort.store.DelegatingStore;
import voldemort.store.Store;
import voldemort.versioning.InconsistencyResolver;
import voldemort.versioning.Versioned;

/**
 * A Store that uses a InconsistencyResolver to eleminate some duplicates
 * 
 * @author jay
 * 
 */
public class InconsistencyResolvingStore<K, V> extends DelegatingStore<K, V> {

    private final InconsistencyResolver<Versioned<V>> resolver;

    public InconsistencyResolvingStore(Store<K, V> innerStore,
                                       InconsistencyResolver<Versioned<V>> resolver) {
        super(innerStore);
        this.resolver = resolver;
    }

    @Override
    public List<Versioned<V>> get(K key) throws VoldemortException {
        return resolver.resolveConflicts(super.get(key));
    }

}
