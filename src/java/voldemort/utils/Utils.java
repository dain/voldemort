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

package voldemort.utils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Nullable;

/**
 * Helper functions FTW!
 * 
 * @author jay
 * 
 */
public class Utils {

    public static final String NEWLINE = System.getProperty("line.separator");

    /**
     * Print an error and exit with error code 1
     * 
     * @param message The error to print
     */
    public static void croak(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * Print an error and exit with the given error code
     * 
     * @param message The error to print
     * @param errorCode The error code to exit with
     */
    public static void croak(String message, int errorCode) {
        System.err.println(message);
        System.exit(errorCode);
    }

    /**
     * Combine the given items as a list
     * 
     * @param <T> The type of the items
     * @param args The items to combine
     * @return A list of the items
     */
    public static <T> List<T> asList(T... args) {
        List<T> items = new ArrayList<T>();
        Collections.addAll(items, args);
        return items;
    }

    /**
     * Delete the given file
     * 
     * @param file The file to delete
     */
    public static void rm(File file) {
        if(file != null)
            rm(Collections.singletonList(file));
    }

    /**
     * Delete the given file
     * 
     * @param file The file to delete
     */
    public static void rm(String file) {
        if(file != null)
            rm(Collections.singletonList(new File(file)));
    }

    /**
     * Delete all the given files
     * 
     * @param files A collection of files to delete
     */
    public static void rm(Collection<File> files) {
        if(files != null) {
            for(File f: files) {
                if(f.isDirectory()) {
                    rm(Arrays.asList(f.listFiles()));
                    f.delete();
                } else {
                    f.delete();
                }
            }
        }
    }

    /**
     * @return true iff the argument is the name of a readable file
     */
    public static boolean isReadableFile(String fileName) {
        return isReadableFile(new File(fileName));
    }

    /**
     * @return true iff the argument is a readable file
     */
    public static boolean isReadableFile(File f) {
        return f.exists() && f.isFile() && f.canRead();
    }

    /**
     * @return true iff the argument is the name of a readable directory
     */
    public static boolean isReadableDir(String dirName) {
        return isReadableDir(new File(dirName));
    }

    /**
     * @return true iff the argument is a readable directory
     */
    public static boolean isReadableDir(File d) {
        return d.exists() && d.isDirectory() && d.canRead();
    }

    /**
     * Check if the two objects are equal to one another if t1 == t2 they are
     * equal if t1 != t2 and one is null, they are not equal to each other if
     * t1.equals(t2) they are equal to each other
     * 
     * @param o1 The first object
     * @param o2 The second object
     * @return True iff they are equal
     */
    public static boolean areNEquals(Object o1, Object o2) {
        if(o1 == o2)
            return true;
        // t1 != t2
        else if(o1 == null || o2 == null)
            // only null equals null
            return false;
        // are they of the same class?
        else if(!o1.getClass().equals(o2.getClass()))
            return false;
        // t1 != null
        else
            return o1.equals(o2);
    }

    /**
     * Throw an IllegalArgumentException if any of the given objects are null
     * 
     * @param objects The objects to test
     */
    public static void assertNotNull(Object... objects) {
        assertNotNull("Null argument not allowed", objects);
    }

    /**
     * Throw an IllegalArgumentException if any of the given objects are null
     * 
     * @param s The error message to give
     * @param objects The objects to test
     */
    public static void assertNotNull(String s, Object... objects) {
        for(Object o: objects)
            if(o == null)
                throw new IllegalArgumentException(s);
    }

    /**
     * Throw an IllegalArgumentException if the argument is null, otherwise just
     * return the argument.
     * 
     * Useful for assignment as in this.thing = Utils.notNull(thing);
     * 
     * @param <T> The type of the thing
     * @param t The thing to check for nullness.
     * @param message The message to put in the exception if it is null
     */
    public static <T> T notNull(T t, String message) {
        if(t == null)
            throw new IllegalArgumentException(message);
        return t;
    }

    /**
     * Throw an IllegalArgumentException if the argument is null, otherwise just
     * return the argument.
     * 
     * Useful for assignment as in this.thing = Utils.notNull(thing);
     * 
     * @param <T> The type of the thing
     * @param t The thing to check for nullness.
     */
    public static <T> T notNull(T t) {
        if(t == null)
            throw new IllegalArgumentException("This object MUST be non-null.");
        return t;
    }

    /**
     * Gets hash code of an object, optionally returns hash code based on the
     * "deep contents" of array if the object is an array.
     * <p>
     * If {@code o} is null, 0 is returned; if {@code o} is an array, the
     * corresponding {@link Arrays#deepHashCode(Object[])}, or
     * {@link Arrays#hashCode(int[])} or the like is used to calculate the hash
     * code.
     */
    public static int deepHashCode(@Nullable Object o) {
        if(o == null) {
            return 0;
        }
        if(!o.getClass().isArray()) {
            return o.hashCode();
        }
        if(o instanceof Object[]) {
            return Arrays.deepHashCode((Object[]) o);
        }
        if(o instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) o);
        }
        if(o instanceof char[]) {
            return Arrays.hashCode((char[]) o);
        }
        if(o instanceof byte[]) {
            return Arrays.hashCode((byte[]) o);
        }
        if(o instanceof short[]) {
            return Arrays.hashCode((short[]) o);
        }
        if(o instanceof int[]) {
            return Arrays.hashCode((int[]) o);
        }
        if(o instanceof long[]) {
            return Arrays.hashCode((long[]) o);
        }
        if(o instanceof float[]) {
            return Arrays.hashCode((float[]) o);
        }
        if(o instanceof double[]) {
            return Arrays.hashCode((double[]) o);
        }
        throw new AssertionError();
    }

    /**
     * Determines if two objects are equal as determined by
     * {@link Object#equals(Object)}, or "deeply equal" if both are arrays.
     * <p>
     * If both objects are null, true is returned; if both objects are array,
     * the corresponding {@link Arrays#deepEquals(Object[], Object[])}, or
     * {@link Arrays#equals(int[], int[])} or the like are called to determine
     * equality.
     * <p>
     * Note that this method does not "deeply" compare the fields of the
     * objects.
     */
    public static boolean deepEquals(@Nullable Object o1, @Nullable Object o2) {
        if(o1 == o2) {
            return true;
        }
        if(o1 == null || o2 == null) {
            return false;
        }

        Class<?> type1 = o1.getClass();
        Class<?> type2 = o2.getClass();
        if(!(type1.isArray() && type2.isArray())) {
            return o1.equals(o2);
        }
        if(o1 instanceof Object[] && o2 instanceof Object[]) {
            return Arrays.deepEquals((Object[]) o1, (Object[]) o2);
        }
        if(type1 != type2) {
            return false;
        }
        if(o1 instanceof boolean[]) {
            return Arrays.equals((boolean[]) o1, (boolean[]) o2);
        }
        if(o1 instanceof char[]) {
            return Arrays.equals((char[]) o1, (char[]) o2);
        }
        if(o1 instanceof byte[]) {
            return Arrays.equals((byte[]) o1, (byte[]) o2);
        }
        if(o1 instanceof short[]) {
            return Arrays.equals((short[]) o1, (short[]) o2);
        }
        if(o1 instanceof int[]) {
            return Arrays.equals((int[]) o1, (int[]) o2);
        }
        if(o1 instanceof long[]) {
            return Arrays.equals((long[]) o1, (long[]) o2);
        }
        if(o1 instanceof float[]) {
            return Arrays.equals((float[]) o1, (float[]) o2);
        }
        if(o1 instanceof double[]) {
            return Arrays.equals((double[]) o1, (double[]) o2);
        }
        throw new AssertionError();
    }

    /**
     * Load the given class using the default constructor
     * 
     * @param className The name of the class
     * @return The class object
     */
    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch(ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Call the class constructor with the given arguments
     * 
     * @param c The class
     * @param args The arguments
     * @return The constructed object
     */
    public static Object callConstructor(Class<?> c, Class<?>[] argTypes, Object[] args) {
        try {
            Constructor<?> cons = c.getConstructor(argTypes);
            return cons.newInstance(args);
        } catch(InvocationTargetException e) {
            throw getCause(e);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch(NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        } catch(InstantiationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Call the named method
     * 
     * @param obj The object to call the method on
     * @param c The class of the object
     * @param name The name of the method
     * @param args The method arguments
     * @return The result of the method
     */
    public static Object callMethod(Object obj,
                                    Class<?> c,
                                    String name,
                                    Class<?>[] classes,
                                    Object[] args) {
        try {
            Method m = getMethod(c, name, classes);
            return m.invoke(obj, args);
        } catch(InvocationTargetException e) {
            throw getCause(e);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get the named method from the class
     * 
     * @param c The class to get the method from
     * @param name The method name
     * @param argTypes The argument types
     * @return The method
     */
    public static Method getMethod(Class<?> c, String name, Class<?>... argTypes) {
        try {
            return c.getMethod(name, argTypes);
        } catch(NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Get the root cause of the Exception
     * 
     * @param e The Exception
     * @return The root cause of the Exception
     */
    private static RuntimeException getCause(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if(cause instanceof RuntimeException)
            throw (RuntimeException) cause;
        else
            throw new IllegalArgumentException(e.getCause());
    }
}
