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

package voldemort.scheduled;

import static voldemort.TestUtils.bytesEqual;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;
import voldemort.TestUtils;
import voldemort.server.scheduler.SlopPusherJob;
import voldemort.store.StorageEngine;
import voldemort.store.Store;
import voldemort.store.memory.InMemoryStorageEngine;
import voldemort.store.slop.Slop;
import voldemort.store.slop.Slop.Operation;
import voldemort.utils.ByteArray;
import voldemort.versioning.Versioned;

public class SlopPusherTest extends TestCase {

    private StorageEngine<ByteArray, Slop> slopStore;
    private Map<Integer, Store<ByteArray, byte[]>> stores;
    private SlopPusherJob pusher;

    public SlopPusherTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        slopStore = new InMemoryStorageEngine<ByteArray, Slop>("slop");
        stores = new HashMap<Integer, Store<ByteArray, byte[]>>();
        stores.put(0, new InMemoryStorageEngine<ByteArray, byte[]>("0"));
        stores.put(1, new InMemoryStorageEngine<ByteArray, byte[]>("1"));
        stores.put(2, new InMemoryStorageEngine<ByteArray, byte[]>("2"));
        pusher = new SlopPusherJob(slopStore, stores);
    }

    private Slop randomSlop(String name, int nodeId) {
        return new Slop(name,
                        Operation.PUT,
                        TestUtils.randomBytes(10),
                        TestUtils.randomBytes(10),
                        nodeId,
                        new Date());
    }

    private void testPush(Versioned<Slop>... slops) {
        // put all the slop in the slop store
        for(Versioned<Slop> s: slops)
            slopStore.put(s.getValue().makeKey(), s);

        // run the pusher
        pusher.run();

        // now all the slop should be gone and the various stores should have
        // those items
        for(Versioned<Slop> vs: slops) {
            // check that all the slops are in the stores
            // and no new slops have appeared
            // and the SloppyStore is now empty
            Slop slop = vs.getValue();
            assertEquals("Slop remains.", 0, slopStore.get(slop.makeKey()).size());
            assertTrue(bytesEqual(slop.getValue(), stores.get(slop.getNodeId())
                                                         .get(slop.makeKey())
                                                         .get(0)
                                                         .getValue()));
        }
    }

    @SuppressWarnings("unchecked")
    public void testPushSingleSlop() {
        testPush(new Versioned<Slop>(randomSlop("0", 0)));
    }
}
