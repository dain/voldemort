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

package voldemort;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import voldemort.client.RoutingTier;
import voldemort.cluster.Cluster;
import voldemort.routing.ConsistentRoutingStrategy;
import voldemort.routing.RoutingStrategy;
import voldemort.serialization.SerializerDefinition;
import voldemort.serialization.json.JsonReader;
import voldemort.store.StorageEngineType;
import voldemort.store.StoreDefinition;
import voldemort.store.readonly.JsonStoreBuilder;
import voldemort.versioning.VectorClock;

/**
 * Helper utilities for tests
 * 
 * @author jay
 * 
 */
public class TestUtils {

    public static final String DIGITS = "0123456789";
    public static final String LETTERS = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";
    public static final String CHARACTERS = LETTERS + DIGITS + "~!@#$%^&*()____+-=[];',,,./>?:{}";
    public static final Random RAND = new Random(19873482374L);

    /**
     * Get a vector clock with events on the sequence of nodes given So
     * getClock(1,1,2,2,2) means a clock that has two writes on node 1 and 3
     * writes on node 2.
     * 
     * @param nodes The sequence of nodes
     * @return A VectorClock initialized with the given sequence of events
     */
    public static VectorClock getClock(int... nodes) {
        VectorClock clock = new VectorClock();
        increment(clock, nodes);
        return clock;
    }

    /**
     * Record events for the given sequence of nodes
     * 
     * @param clock The VectorClock to record the events on
     * @param nodes The sequences of node events
     */
    public static void increment(VectorClock clock, int... nodes) {
        for(int n: nodes)
            clock.incrementVersion((short) n, System.currentTimeMillis());
    }

    /**
     * Test two byte arrays for (deep) equality. I think this exists in java 6
     * but not java 5
     * 
     * @param a1 Array 1
     * @param a2 Array 2
     * @return True iff a1.length == a2.length and a1[i] == a2[i] for 0 <= i <
     *         a1.length
     */
    public static boolean bytesEqual(byte[] a1, byte[] a2) {
        if(a1 == a2) {
            return true;
        } else if(a1 == null || a2 == null) {
            return false;
        } else if(a1.length != a2.length) {
            return false;
        } else {
            for(int i = 0; i < a1.length; i++)
                if(a1[i] != a2[i])
                    return false;
        }

        return true;
    }

    /**
     * Create a string with some random letters
     * 
     * @param RAND The Random number generator to use
     * @param length The length of the string to create
     * @return The string
     */
    public static String randomLetters(int length) {
        return randomString(LETTERS, length);
    }

    /**
     * Create a string that is a random sample (with replacement) from the given
     * string
     * 
     * @param sampler The string to sample from
     * @param length The length of the string to create
     * @return The created string
     */
    public static String randomString(String sampler, int length) {
        StringBuilder builder = new StringBuilder(length);
        for(int i = 0; i < length; i++)
            builder.append(sampler.charAt(RAND.nextInt(sampler.length())));
        return builder.toString();
    }

    /**
     * Generate an array of random bytes
     * 
     * @param length
     * @return
     */
    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RAND.nextBytes(bytes);
        return bytes;
    }

    /**
     * Return an array of length count containing random integers in the range
     * (0, max) generated off the test rng.
     * 
     * @param max The bound on the random number size
     * @param count The number of integers to generate
     * @return The array of integers
     */
    public static int[] randomInts(int max, int count) {
        int[] vals = new int[count];
        for(int i = 0; i < count; i++)
            vals[i] = RAND.nextInt(max);
        return vals;
    }

    /**
     * Weirdly java doesn't seem to have Arrays.shuffle(), this terrible hack
     * does that.
     * 
     * @return A shuffled copy of the input
     */
    public static int[] shuffle(int[] input) {
        List<Integer> vals = new ArrayList<Integer>(input.length);
        for(int i = 0; i < input.length; i++)
            vals.add(input[i]);
        Collections.shuffle(vals, RAND);
        int[] copy = new int[input.length];
        for(int i = 0; i < input.length; i++)
            copy[i] = vals.get(i);
        return copy;
    }

    /**
     * Compute the requested quantile of the given array
     * 
     * @param values The array of values
     * @param quantile The quantile requested (must be between 0.0 and 1.0
     *        inclusive)
     * @return The quantile
     */
    public static long quantile(long[] values, double quantile) {
        if(values == null)
            throw new IllegalArgumentException("Values cannot be null.");
        if(quantile < 0.0 || quantile > 1.0)
            throw new IllegalArgumentException("Quantile must be between 0.0 and 1.0");

        long[] copy = new long[values.length];
        System.arraycopy(values, 0, copy, 0, copy.length);
        Arrays.sort(copy);
        int index = (int) (copy.length * quantile);
        return copy[index];
    }

    /**
     * Create a temporary directory in the directory given by java.io.tmpdir
     * 
     * @return The directory created.
     */
    public static File createTempDir() {
        return createTempDir(new File(System.getProperty("java.io.tmpdir")));
    }

    /**
     * Create a temporary directory that is a child of the given directory
     * 
     * @param parent The parent directory
     * @return The temporary directory
     */
    public static File createTempDir(File parent) {
        File temp = new File(parent, Integer.toString(Math.abs(RAND.nextInt()) % 1000000));
        temp.delete();
        temp.mkdir();
        temp.deleteOnExit();
        return temp;
    }

    /**
     * Wrap the given string in quotation marks. This is slightly more readable
     * then the java inline quotes that require escaping.
     * 
     * @param s The string to wrap in quotes
     * @return The string
     */
    public static String quote(String s) {
        return "\"" + s + "\"";
    }

    /**
     * 
     * @param cluster
     * @param data
     * @param baseDir
     * @param TEST_SIZE
     * @return the directory where the index is created
     * @throws Exception
     */
    public static String createReadOnlyIndex(Cluster cluster,
                                             Map<String, String> data,
                                             String baseDir) throws Exception {
        // write data to file
        File dataFile = File.createTempFile("test", ".txt");
        dataFile.deleteOnExit();
        BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile));
        for(Map.Entry<String, String> entry: data.entrySet())
            writer.write("\"" + entry.getKey() + "\"\t\"" + entry.getValue() + "\"\n");
        writer.close();
        BufferedReader reader = new BufferedReader(new FileReader(dataFile));
        JsonReader jsonReader = new JsonReader(reader);

        SerializerDefinition serDef = new SerializerDefinition("json", "'string'");
        StoreDefinition storeDef = new StoreDefinition("test",
                                                       StorageEngineType.READONLY,
                                                       serDef,
                                                       serDef,
                                                       RoutingTier.CLIENT,
                                                       1,
                                                       1,
                                                       1,
                                                       1,
                                                       1,
                                                       1);
        RoutingStrategy router = new ConsistentRoutingStrategy(cluster.getNodes(), 1);

        // make a temp dir
        File dataDir = new File(baseDir + File.separatorChar + "read-only-temp-index-"
                                + new Integer((int) (Math.random() * 1000)));
        // build and open store
        JsonStoreBuilder storeBuilder = new JsonStoreBuilder(jsonReader,
                                                             cluster,
                                                             storeDef,
                                                             router,
                                                             dataDir,
                                                             100,
                                                             1);
        storeBuilder.build();

        return dataDir.getAbsolutePath();
    }

}
