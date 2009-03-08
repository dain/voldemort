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

package voldemort.store.routed;

import static voldemort.TestUtils.getClock;
import static voldemort.VoldemortTestConstants.getNineNodeCluster;

import java.util.List;
import java.util.Map;

import voldemort.VoldemortException;
import voldemort.cluster.Cluster;
import voldemort.cluster.Node;
import voldemort.routing.RouteToAllStrategy;
import voldemort.routing.RoutingStrategy;
import voldemort.store.AbstractByteArrayStoreTest;
import voldemort.store.FailingStore;
import voldemort.store.InsufficientOperationalNodesException;
import voldemort.store.SleepyStore;
import voldemort.store.Store;
import voldemort.store.UnreachableStoreException;
import voldemort.store.memory.InMemoryStorageEngine;
import voldemort.store.versioned.InconsistencyResolvingStore;
import voldemort.utils.ByteArray;
import voldemort.utils.Utils;
import voldemort.versioning.Occured;
import voldemort.versioning.VectorClock;
import voldemort.versioning.VectorClockInconsistencyResolver;
import voldemort.versioning.Versioned;

import com.google.common.collect.Maps;

/**
 * Basic tests for RoutedStore
 * 
 * @author jay
 * 
 */
public class RoutedStoreTest extends AbstractByteArrayStoreTest {

    private Cluster cluster;
    private final ByteArray aKey = ByteArray.valueOf("jay");
    private final byte[] aValue = "kreps".getBytes();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        cluster = getNineNodeCluster();
    }

    @Override
    public Store<ByteArray, byte[]> getStore() {
        return new InconsistencyResolvingStore<ByteArray, byte[]>(getStore(cluster,
                                                                           cluster.getNumberOfNodes(),
                                                                           cluster.getNumberOfNodes(),
                                                                           4,
                                                                           0),
                                                                  new VectorClockInconsistencyResolver<byte[]>());
    }

    private RoutedStore getStore(Cluster cluster, int reads, int writes, int threads, int failing) {
        return getStore(cluster,
                        reads,
                        writes,
                        cluster.getNumberOfNodes(),
                        threads,
                        failing,
                        0,
                        new RouteToAllStrategy(cluster.getNodes()),
                        new VoldemortException());
    }

    private RoutedStore getStore(Cluster cluster,
                                 int reads,
                                 int writes,
                                 int replicationFactor,
                                 int threads,
                                 int failing,
                                 int sleepy,
                                 RoutingStrategy strategy,
                                 VoldemortException e) {
        Map<Integer, Store<ByteArray, byte[]>> subStores = Maps.newHashMap();
        int count = 0;
        for(Node n: cluster.getNodes()) {
            if(count >= cluster.getNumberOfNodes())
                throw new IllegalArgumentException(failing + " failing nodes, " + sleepy
                                                   + " sleepy nodes, but only "
                                                   + cluster.getNumberOfNodes()
                                                   + " nodes in the cluster.");
            else if(count < failing)
                subStores.put(n.getId(), new FailingStore<ByteArray, byte[]>("test", e));
            else if(count < failing + sleepy)
                subStores.put(n.getId(),
                              new SleepyStore<ByteArray, byte[]>(Long.MAX_VALUE,
                                                                 new InMemoryStorageEngine<ByteArray, byte[]>("test")));
            else
                subStores.put(n.getId(), new InMemoryStorageEngine<ByteArray, byte[]>("test"));

            count += 1;
        }
        return new RoutedStore("test", subStores, strategy, reads, writes, threads, true, 1000L);
    }

    private int countOccurances(RoutedStore routedStore, ByteArray key, Versioned<byte[]> value) {
        int count = 0;
        for(Store<ByteArray, byte[]> store: routedStore.getInnerStores().values())
            try {
                if(store.get(key).size() > 0 && Utils.deepEquals(store.get(key).get(0), value))
                    count += 1;
            } catch(VoldemortException e) {
                // This is normal for the failing store...
            }
        return count;
    }

    private void assertNEqual(RoutedStore routedStore,
                              int expected,
                              ByteArray key,
                              Versioned<byte[]> value) {
        int count = countOccurances(routedStore, key, value);
        assertEquals("Expected " + expected + " occurances of '" + key + "' with value '" + value
                     + "', but found " + count + ".", expected, count);
    }

    private void assertNOrMoreEqual(RoutedStore routedStore,
                                    int expected,
                                    ByteArray key,
                                    Versioned<byte[]> value) {
        int count = countOccurances(routedStore, key, value);
        assertTrue("Expected " + expected + " or more occurances of '" + key + "' with value '"
                   + value + "', but found " + count + ".", expected <= count);
    }

    public void testBasicOperations(int reads, int writes, int failures, int threads) {
        RoutedStore routedStore = getStore(cluster, reads, writes, threads, failures);
        Store<ByteArray, byte[]> store = new InconsistencyResolvingStore<ByteArray, byte[]>(routedStore,
                                                                                            new VectorClockInconsistencyResolver<byte[]>());
        VectorClock clock = getClock(1);
        Versioned<byte[]> versioned = new Versioned<byte[]>(aValue, clock);
        routedStore.put(aKey, versioned);
        assertNOrMoreEqual(routedStore, cluster.getNumberOfNodes() - failures, aKey, versioned);
        List<Versioned<byte[]>> found = store.get(aKey);
        assertEquals(1, found.size());
        assertEquals(versioned, found.get(0));
        assertNOrMoreEqual(routedStore, cluster.getNumberOfNodes() - failures, aKey, versioned);
        assertTrue(routedStore.delete(aKey, versioned.getVersion()));
        assertNEqual(routedStore, 0, aKey, versioned);
        assertTrue(!routedStore.delete(aKey, versioned.getVersion()));
    }

    public void testBasicOperationsSingleThreaded() {
        testBasicOperations(cluster.getNumberOfNodes(), cluster.getNumberOfNodes(), 0, 1);
    }

    public void testBasicOperationsMultiThreaded() {
        testBasicOperations(cluster.getNumberOfNodes(), cluster.getNumberOfNodes(), 0, 4);
    }

    public void testBasicOperationsMultiThreadedWithFailures() {
        testBasicOperations(cluster.getNumberOfNodes() - 2, cluster.getNumberOfNodes() - 2, 2, 4);
    }

    public void testBasicOperationFailure(int reads, int writes, int failures, int threads) {
        VectorClock clock = getClock(1);
        Versioned<byte[]> versioned = new Versioned<byte[]>(aValue, clock);
        RoutedStore routedStore = getStore(cluster,
                                           reads,
                                           cluster.getNumberOfNodes(),
                                           writes,
                                           threads,
                                           failures,
                                           0,
                                           new RouteToAllStrategy(cluster.getNodes()),
                                           new UnreachableStoreException("no go"));
        try {
            routedStore.put(aKey, versioned);
            fail("Put succeeded with too few operational nodes.");
        } catch(InsufficientOperationalNodesException e) {
            // expected
        }
        try {
            routedStore.get(aKey);
            fail("Get succeeded with too few operational nodes.");
        } catch(InsufficientOperationalNodesException e) {
            // expected
        }
        try {
            routedStore.delete(aKey, versioned.getVersion());
            fail("Get succeeded with too few operational nodes.");
        } catch(InsufficientOperationalNodesException e) {
            // expected
        }
    }

    public void testBasicOperationFailureMultiThreaded() {
        testBasicOperationFailure(cluster.getNumberOfNodes() - 2,
                                  cluster.getNumberOfNodes() - 2,
                                  4,
                                  4);
    }

    public void testPutIncrementsVersion() {
        Store<ByteArray, byte[]> store = getStore();
        VectorClock clock = new VectorClock();
        VectorClock copy = clock.clone();
        store.put(aKey, new Versioned<byte[]>(getValue(), clock));
        List<Versioned<byte[]>> found = store.get(aKey);
        assertEquals("Invalid number of items found.", 1, found.size());
        assertEquals("Version not incremented properly",
                     Occured.BEFORE,
                     copy.compare(found.get(0).getVersion()));
    }

    public void testObsoleteMasterFails() {
    // write me
    }

    public void testOnlyNodeFailuresDisableNode() {
        RoutingStrategy strategy;
        Cluster cluster;

        // test put
        cluster = getNineNodeCluster();
        strategy = new RouteToAllStrategy(cluster.getNodes());
        Store<ByteArray, byte[]> s1 = getStore(cluster,
                                               1,
                                               9,
                                               cluster.getNumberOfNodes(),
                                               9,
                                               9,
                                               0,
                                               strategy,
                                               new VoldemortException());
        try {
            s1.put(aKey, new Versioned<byte[]>(aValue));
            fail("Failure is expected");
        } catch(InsufficientOperationalNodesException e) { /* expected */}
        assertOperationalNodes(cluster, 9);

        cluster = getNineNodeCluster();
        strategy = new RouteToAllStrategy(cluster.getNodes());
        Store<ByteArray, byte[]> s2 = getStore(cluster,
                                               1,
                                               9,
                                               cluster.getNumberOfNodes(),
                                               9,
                                               9,
                                               0,
                                               strategy,
                                               new UnreachableStoreException("no go"));
        try {
            s2.put(aKey, new Versioned<byte[]>(aValue));
            fail("Failure is expected");
        } catch(InsufficientOperationalNodesException e) { /* expected */}
        assertOperationalNodes(cluster, 0);

        // test get
        cluster = getNineNodeCluster();
        strategy = new RouteToAllStrategy(cluster.getNodes());
        s1 = getStore(cluster,
                      1,
                      9,
                      cluster.getNumberOfNodes(),
                      9,
                      9,
                      0,
                      strategy,
                      new VoldemortException());
        try {
            s1.get(aKey);
            fail("Failure is expected");
        } catch(InsufficientOperationalNodesException e) { /* expected */}
        assertOperationalNodes(cluster, 9);

        cluster = getNineNodeCluster();
        strategy = new RouteToAllStrategy(cluster.getNodes());
        s2 = getStore(cluster,
                      1,
                      9,
                      cluster.getNumberOfNodes(),
                      9,
                      9,
                      0,
                      strategy,
                      new UnreachableStoreException("no go"));
        try {
            s2.get(aKey);
            fail("Failure is expected");
        } catch(InsufficientOperationalNodesException e) { /* expected */}
        assertOperationalNodes(cluster, 0);

        // test delete
        cluster = getNineNodeCluster();
        strategy = new RouteToAllStrategy(cluster.getNodes());
        s1 = getStore(cluster,
                      1,
                      9,
                      cluster.getNumberOfNodes(),
                      9,
                      9,
                      0,
                      strategy,
                      new VoldemortException());
        try {
            s1.delete(aKey, new VectorClock());
            fail("Failure is expected");
        } catch(InsufficientOperationalNodesException e) { /* expected */}
        assertOperationalNodes(cluster, 9);

        cluster = getNineNodeCluster();
        strategy = new RouteToAllStrategy(cluster.getNodes());
        s2 = getStore(cluster,
                      1,
                      9,
                      cluster.getNumberOfNodes(),
                      9,
                      9,
                      0,
                      strategy,
                      new UnreachableStoreException("no go"));
        try {
            s2.delete(aKey, new VectorClock());
            fail("Failure is expected");
        } catch(InsufficientOperationalNodesException e) { /* expected */}
        assertOperationalNodes(cluster, 0);
    }

    public void testStoreTimeouts() throws InterruptedException {
    /*
     * Cluster cluster = getThreeNodeThreePartitionCluster(); RoutingStrategy
     * strategy = new RouteToAllStrategy(cluster); final Store<byte[],byte[]>
     * store = getStore(cluster, 1, 9, 9, 0, 9, strategy, new
     * VoldemortException()); store.put(aKey, new Versioned<byte[]>(new
     * byte[0]));
     */
    }

    public void assertOperationalNodes(Cluster cluster, int expected) {
        int found = 0;
        for(Node n: cluster.getNodes())
            if(n.getStatus().isAvailable())
                found++;
        assertEquals("Number of operational nodes not what was expected.", expected, found);
    }

    @Override
    protected void assertGetAllValues(byte[] expectedValue, List<Versioned<byte[]>> versioneds) {
        assertEquals(cluster.getNodes().size(), versioneds.size());
        valuesEqual(expectedValue, versioneds.get(0).getValue());
    }
}
