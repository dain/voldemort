/**
 * See the NOTICE.txt file distributed with this work for information regarding
 * copyright ownership.
 * 
 * The authors license this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package voldemort.store.mongodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.mongodb.driver.MongoDBException;
import org.mongodb.driver.MongoDBIOException;
import org.mongodb.driver.impl.DirectBufferTLS;
import org.mongodb.driver.ts.DB;
import org.mongodb.driver.ts.DBCollection;
import org.mongodb.driver.ts.DBCursor;
import org.mongodb.driver.ts.Doc;
import org.mongodb.driver.ts.Mongo;
import org.mongodb.driver.ts.MongoSelector;
import org.mongodb.driver.util.BSONObject;

import voldemort.VoldemortException;
import voldemort.store.StorageEngine;
import voldemort.store.StoreUtils;
import voldemort.utils.ByteArray;
import voldemort.utils.ClosableIterator;
import voldemort.utils.Pair;
import voldemort.versioning.ObsoleteVersionException;
import voldemort.versioning.Occured;
import voldemort.versioning.VectorClock;
import voldemort.versioning.Version;
import voldemort.versioning.Versioned;

/**
 * <p>
 * Storage engine for MongoDB (<a
 * href="http://www.mongodb.org/">http://www.mongodb.org/</a>). Stores the key,
 * value and clock in a BSON document that in JS notation is :
 * </p>
 * 
 * <pre>
 *  {
 *     k : user key string,
 *     c : vectorclock as a binary
 *     v : user BSON doc
 *  }
 * </pre>
 * 
 * <p>
 * Currently, database is "voldemort" and collection is name of the store.
 * </p>
 * 
 * <p>
 * Note : you *must* use the MongoDocSerializer to use this store, as it expects
 * the byte[] value to be BSON, and the key to be the UTF-8 encoded byte array
 * of a string.
 * </p>
 * 
 * <p>
 * Cofiguration : use 'string' for the type of key serializer, and 'mongodoc'
 * for value serializer
 * </p>
 * 
 * <p>
 * This store depends on the XJDM driver for mongodb
 * (http://github.com/geir/mongo-java-driver, and currently is a tad hacky wrt
 * buffer management. Both that and performance are next to be fixed.
 * </p>
 * 
 * @author geir
 */
public class MongoDBStorageEngine implements StorageEngine<ByteArray, byte[]> {

    private static final Logger logger = Logger.getLogger(MongoDBStorageEngine.class.getName());

    public static final String KEY = "k";
    public static final String CLOCK = "c";
    public static final String VALUE = "v";
    public static final String DB_NAME = "voldemort";

    protected Mongo _mongoDB;
    protected DB _db;
    protected DBCollection _coll;
    protected final String _collectionName;

    public MongoDBStorageEngine(String name) throws MongoDBException {
        logger.info("MongoDB Storage Engine : v0.1");
        _collectionName = name;
        init();
    }

    protected final void  init() throws MongoDBException {
        _mongoDB = new Mongo("127.0.0.1", 27017);
        _db = _mongoDB.getDB(DB_NAME);
        _coll = _db.getCollection(_collectionName);
    }
    
    public ClosableIterator<Pair<ByteArray, Versioned<byte[]>>> entries() {
        try {
            return new MongoDBClosableIterator();
        } catch(MongoDBException e) {
            throw new VoldemortException(e);
        }
    }

    public List<Versioned<byte[]>> get(ByteArray key) throws VoldemortException {
        StoreUtils.assertValidKey(key);
        setTls();
        List<Versioned<byte[]>> list = new ArrayList<Versioned<byte[]>>();

        /*
         * we want to deal w/ keys as strings to ensure max utility when
         * directly querying mongodb
         */
        String strKey = new String(key.get());
        DBCursor cur = null;
        try {
            cur = _coll.find(new MongoSelector(KEY, strKey));
            for(Doc d: cur) {

                BSONObject bo = new BSONObject(); // TODO : use the TLS buffer
                // to save the alloc
                bo.serialize(d.getDoc(VALUE));
                Versioned<byte[]> val = new Versioned<byte[]>(bo.toArray(),
                                                              new VectorClock(d.getBytes(CLOCK)));
                list.add(val);
            }
        } catch (MongoDBIOException mioe) {
            try {
                init();
            }
            catch(MongoDBException ee) {
                ee.printStackTrace();
            }
            throw new VoldemortException(mioe);
        } catch(MongoDBException e) {
            throw new VoldemortException(e);
        } finally {
            closeCursor(cur);
        }

        return list;
    }

    public Map<ByteArray, List<Versioned<byte[]>>> getAll(Iterable<ByteArray> keys)
            throws VoldemortException {
        StoreUtils.assertValidKeys(keys);

        Map<ByteArray, List<Versioned<byte[]>>> map = new HashMap<ByteArray, List<Versioned<byte[]>>>();
        for(ByteArray b: keys) {
            List<Versioned<byte[]>> list = get(b);
            // the test suite expects this - I'd prefer to return an empty list
            // per key to parallel get()
            if(list.size() > 0) {
                map.put(b, list);
            }
        }

        return map;
    }

    public void put(ByteArray key, Versioned<byte[]> value) throws VoldemortException {
        StoreUtils.assertValidKey(key);
        setTls();

        String strKey = new String(key.get());
        DBCursor cur = null;

        try {

            /*
             * we need to go through our current entries for this key and see if
             * 
             * a) any are newer, in which case we need to complain b) we have
             * more than one older, in which case someone screwed up
             * 
             * and also delete them
             */

            cur = _coll.find(new Doc(KEY, strKey));

            for(Doc d: cur) {

                VectorClock existingClock = new VectorClock(d.getBytes(CLOCK));
                Occured occured = value.getVersion().compare(existingClock);

                // if my new one occured before the one from the db....

                if(occured == Occured.BEFORE)
                    throw new ObsoleteVersionException("Key '" + strKey + " is obsolete.");
                else if(occured == Occured.AFTER)
                    _coll.remove(new MongoSelector(d));

                // TODO - why not concurrent? need to understand better...
            }

            /*
             * since we're clean and safe time-wise, just insert it
             */

            BSONObject bo = new BSONObject();

            Doc newData = new Doc(KEY, strKey);
            newData.put(VALUE, bo.deserialize(value.getValue()));
            newData.put(CLOCK, ((VectorClock) value.getVersion()).toBytes());

            _coll.insert(newData);
        } catch (MongoDBIOException mioe) {
            try {
                init();
            }
            catch(MongoDBException ee) {
                ee.printStackTrace();
            }
            throw new VoldemortException(mioe);
        } catch(MongoDBException e) {
            throw new VoldemortException(e);
        } finally {
            closeCursor(cur);
        }
    }

    public boolean delete(ByteArray key, Version version) throws VoldemortException {
        StoreUtils.assertValidKey(key);
        setTls();
        String strKey = new String(key.get());
        boolean deleted = false;
        DBCursor cur = null;
        try {
            cur = _coll.find(new Doc(KEY, strKey));
            for(Doc d: cur) {
                VectorClock existingClock = new VectorClock(d.getBytes(CLOCK));
                Occured occured = version.compare(existingClock);

                // TODO - Q : why not concurrently?
                if(occured == Occured.BEFORE) {
                    _coll.remove(new MongoSelector(d));
                    deleted = true;
                }
            }

            return deleted;
        } catch (MongoDBIOException mioe) {
            try {
                init();
            }
            catch(MongoDBException ee) {
                ee.printStackTrace();
            }
            throw new VoldemortException(mioe);
        } catch(MongoDBException e) {
            throw new VoldemortException(e);
        } finally {
            closeCursor(cur);
        }
    }

    public String getName() {
        return _coll.getName();
    }

    public void close() throws VoldemortException {
        try {
            if(_db != null)
                _db.close();
        } catch(Exception e) {
            throw new VoldemortException(e);
        }
    }

    /**
     * Delete everything in the store irrespective of version et al. This is
     * really to aide w/ testing.
     */
    protected void clearStore() {
        try {
            _coll.clear();
        } catch(MongoDBException e) {
            logger.error("Error while clearing store.", e);
        }
    }

    /**
     * Closes a cursor quietly
     * 
     * @param cur cursor to close.
     */
    private void closeCursor(DBCursor cur) {
        if(cur == null)
            return;

        try {
            cur.close();
        } catch(MongoDBException e) {
            logger.error("Error while closing cursor.", e);
        }
    }

    /**
     * Embarassing hackery until I work out something different in the driver.
     * Driver was designed w/ a set of expectations for threading and therefore
     * buffer management (for perf reasons) that don't exactly align w/ the use
     * case here
     */
    private void setTls() {
        DirectBufferTLS tls = DirectBufferTLS.getThreadLocal();
        if(tls == null) {
            tls = new DirectBufferTLS();
            tls.set();
        }
    }

    public class MongoDBClosableIterator implements
            ClosableIterator<Pair<ByteArray, Versioned<byte[]>>> {

        BSONObject _bo = new BSONObject();
        protected DBCursor _cursor;

        public MongoDBClosableIterator() throws MongoDBException {
            setTls(); // TODO - will be a problem if someone hands this iterator
            // across threads
            _cursor = _coll.find();
        }

        public void close() {
            closeCursor(_cursor);
            _cursor = null;
        }

        public boolean hasNext() {
            return _cursor.hasMoreElements();
        }

        public Pair<ByteArray, Versioned<byte[]>> next() {
            try {
                Doc d = _cursor.getNextObject();
                _bo.serialize(d.getDoc(VALUE));

                Versioned<byte[]> val = new Versioned<byte[]>(_bo.toArray(),
                                                              new VectorClock(d.getBytes(CLOCK)));

                return new Pair<ByteArray, Versioned<byte[]>>(new ByteArray(d.getString(KEY)
                                                                             .getBytes()), val);
            } catch(MongoDBException e) {
                throw new VoldemortException(e);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
