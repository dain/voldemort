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

package voldemort.store.readonly;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import voldemort.VoldemortException;
import voldemort.cluster.Cluster;
import voldemort.cluster.Node;
import voldemort.routing.ConsistentRoutingStrategy;
import voldemort.routing.RoutingStrategy;
import voldemort.serialization.DefaultSerializerFactory;
import voldemort.serialization.Serializer;
import voldemort.serialization.SerializerFactory;
import voldemort.serialization.json.EndOfFileException;
import voldemort.serialization.json.JsonReader;
import voldemort.store.StoreDefinition;
import voldemort.utils.ByteUtils;
import voldemort.utils.Utils;
import voldemort.xml.ClusterMapper;
import voldemort.xml.StoreDefinitionsMapper;

import com.google.common.collect.AbstractIterator;

/**
 * Build a read-only store from given input.
 * 
 * @author jay
 * 
 */
public class JsonStoreBuilder {

    private static final Logger logger = Logger.getLogger(JsonStoreBuilder.class);

    private final JsonReader reader;
    private final Cluster cluster;
    private final StoreDefinition storeDefinition;
    private final RoutingStrategy routingStrategy;
    private final File outputDir;
    private final int internalSortSize;
    private final int numThreads;

    public JsonStoreBuilder(JsonReader reader,
                            Cluster cluster,
                            StoreDefinition storeDefinition,
                            RoutingStrategy routingStrategy,
                            File outputDir,
                            int internalSortSize,
                            int numThreads) {
        this.reader = reader;
        this.cluster = cluster;
        this.storeDefinition = storeDefinition;
        this.outputDir = outputDir;
        this.routingStrategy = routingStrategy;
        this.internalSortSize = internalSortSize;
        this.numThreads = numThreads;
    }

    /**
     * Main method to run on a input text file
     * 
     * @param args see USAGE for details
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if(args.length != 7)
            Utils.croak("USAGE: java "
                        + JsonStoreBuilder.class.getName()
                        + " cluster.xml store_definitions.xml store_name sort_obj_buffer_size input_data output_dir num_threads");
        String clusterFile = args[0];
        String storeDefFile = args[1];
        String storeName = args[2];
        int sortBufferSize = Integer.parseInt(args[3]);
        String inputFile = args[4];
        File outputDir = new File(args[5]);
        int numThreads = Integer.parseInt(args[6]);

        try {
            JsonReader reader = new JsonReader(new BufferedReader(new FileReader(inputFile),
                                                                  1000000));
            Cluster cluster = new ClusterMapper().readCluster(new BufferedReader(new FileReader(clusterFile)));
            StoreDefinition storeDef = null;
            List<StoreDefinition> stores = new StoreDefinitionsMapper().readStoreList(new BufferedReader(new FileReader(storeDefFile)));
            for(StoreDefinition def: stores) {
                if(def.getName().equals(storeName))
                    storeDef = def;
            }

            if(storeDef == null)
                Utils.croak("No store found with name \"" + storeName + "\"");

            if(!outputDir.exists())
                Utils.croak("Directory \"" + outputDir.getAbsolutePath() + " does not exist.");

            ConsistentRoutingStrategy routingStrategy = new ConsistentRoutingStrategy(cluster.getNodes(),
                                                                                      storeDef.getReplicationFactor());

            new JsonStoreBuilder(reader,
                                 cluster,
                                 storeDef,
                                 routingStrategy,
                                 outputDir,
                                 sortBufferSize,
                                 numThreads).build();
        } catch(FileNotFoundException e) {
            Utils.croak(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void build() throws IOException {
        // initialize nodes
        int numNodes = cluster.getNumberOfNodes();
        DataOutputStream[] indexes = new DataOutputStream[numNodes];
        DataOutputStream[] datas = new DataOutputStream[numNodes];
        int current = 0;
        for(Node node: cluster.getNodes()) {
            File indexFile = new File(outputDir, node.getId() + ".index");
            File dataFile = new File(outputDir, node.getId() + ".data");
            indexes[current] = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile),
                                                                             1000000));
            datas[current] = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile),
                                                                           1000000));
            current++;
        }

        SerializerFactory factory = new DefaultSerializerFactory();
        Serializer keySerializer = factory.getSerializer(storeDefinition.getKeySerializer());
        Serializer valueSerializer = factory.getSerializer(storeDefinition.getValueSerializer());

        logger.info("Reading items...");
        int count = 0;
        ExternalSorter<KeyValuePair> sorter = new ExternalSorter<KeyValuePair>(new KeyValuePairSerializer(),
                                                                               new KeyMd5Comparator(),
                                                                               internalSortSize,
                                                                               numThreads);
        JsonObjectIterator iter = new JsonObjectIterator(reader, keySerializer, valueSerializer);
        long position = 0;
        MessageDigest digest = ByteUtils.getDigest("MD5");
        for(KeyValuePair pair: sorter.sorted(iter)) {
            List<Node> nodes = this.routingStrategy.routeRequest(pair.getKey());
            byte[] keyMd5 = pair.getKeyMd5();
            digest.reset();
            for(int i = 0; i < this.storeDefinition.getReplicationFactor(); i++) {
                int nodeId = nodes.get(i).getId();
                int numBytes = pair.getValue().length;
                datas[nodeId].writeInt(numBytes);
                datas[nodeId].write(pair.getValue());
                indexes[nodeId].write(keyMd5);
                indexes[nodeId].writeLong(position);
                position += numBytes + 4;
            }
            count++;
        }

        logger.info(count + " items read.");

        // sort and write out
        logger.info("Closing all store files");
        for(int i = 0; i < numNodes; i++) {
            indexes[i].close();
            datas[i].close();
        }
    }

    private static class KeyValuePairSerializer implements Serializer<KeyValuePair> {

        private final MessageDigest digest = ByteUtils.getDigest("MD5");

        public byte[] toBytes(KeyValuePair pair) {
            byte[] key = pair.getKey();
            byte[] value = pair.getValue();
            byte[] bytes = new byte[key.length + value.length + 8];
            ByteUtils.writeInt(bytes, key.length, 0);
            ByteUtils.writeInt(bytes, value.length, 4);
            System.arraycopy(key, 0, bytes, 8, key.length);
            System.arraycopy(value, 0, bytes, 8 + key.length, value.length);
            return bytes;
        }

        public KeyValuePair toObject(byte[] bytes) {
            int keySize = ByteUtils.readInt(bytes, 0);
            int valueSize = ByteUtils.readInt(bytes, 4);
            byte[] key = new byte[keySize];
            byte[] value = new byte[valueSize];
            System.arraycopy(bytes, 8, key, 0, keySize);
            System.arraycopy(bytes, 8 + keySize, value, 0, valueSize);
            byte[] md5 = digest.digest(key);
            digest.reset();

            return new KeyValuePair(key, md5, value);
        }

    }

    private static class JsonObjectIterator extends AbstractIterator<KeyValuePair> {

        private final JsonReader reader;
        private final Serializer<Object> keySerializer;
        private final Serializer<Object> valueSerializer;
        private final MessageDigest digest;

        public JsonObjectIterator(JsonReader reader,
                                  Serializer<Object> keySerializer,
                                  Serializer<Object> valueSerializer) {
            this.reader = reader;
            this.keySerializer = keySerializer;
            this.valueSerializer = valueSerializer;
            this.digest = ByteUtils.getDigest("MD5");
        }

        @Override
        protected KeyValuePair computeNext() {
            try {
                Object key = reader.read();
                Object value = null;
                try {
                    value = reader.read();
                } catch(EndOfFileException e) {
                    throw new VoldemortException("Invalid file: reached end of file with key but no matching value.");
                }
                byte[] keyBytes = keySerializer.toBytes(key);
                byte[] keyMd5 = digest.digest(keyBytes);
                digest.reset();
                byte[] valueBytes = valueSerializer.toBytes(value);

                return new KeyValuePair(keyBytes, keyMd5, valueBytes);
            } catch(EndOfFileException e) {
                return endOfData();
            }
        }

    }

    public static class KeyMd5Comparator implements Comparator<KeyValuePair> {

        public int compare(KeyValuePair kv1, KeyValuePair kv2) {
            return ByteUtils.compare(kv1.getKeyMd5(), kv2.getKeyMd5());
        }

    }

    private static class KeyValuePair {

        private final byte[] key;
        private final byte[] keyMd5;
        private final byte[] value;

        public KeyValuePair(byte[] key, byte[] keyMd5, byte[] value) {
            this.key = key;
            this.keyMd5 = keyMd5;
            this.value = value;
        }

        public byte[] getKey() {
            return key;
        }

        public byte[] getKeyMd5() {
            return this.keyMd5;
        }

        public byte[] getValue() {
            return value;
        }
    }

}
