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

package voldemort.store;

import java.util.HashMap;
import java.util.Map;

import voldemort.VoldemortException;
import voldemort.utils.ReflectUtils;
import voldemort.versioning.InconsistentDataException;
import voldemort.versioning.ObsoleteVersionException;

/**
 * Map error codes to exceptions and vice versa
 * 
 * @author jay
 * 
 */
public class ErrorCodeMapper {

    // These two maps act as a bijection from error codes to exceptions.
    private Map<Short, Class<? extends VoldemortException>> codeToException;
    private Map<Class<? extends VoldemortException>, Short> exceptionToCode;

    public ErrorCodeMapper() {
        codeToException = new HashMap<Short, Class<? extends VoldemortException>>();
        codeToException.put((short) 1, VoldemortException.class);
        codeToException.put((short) 2, InsufficientOperationalNodesException.class);
        codeToException.put((short) 3, StoreOperationFailureException.class);
        codeToException.put((short) 4, ObsoleteVersionException.class);
        codeToException.put((short) 6, UnknownFailure.class);
        codeToException.put((short) 7, UnreachableStoreException.class);
        codeToException.put((short) 8, InconsistentDataException.class);

        exceptionToCode = new HashMap<Class<? extends VoldemortException>, Short>();
        for(Map.Entry<Short, Class<? extends VoldemortException>> entry: codeToException.entrySet())
            exceptionToCode.put(entry.getValue(), entry.getKey());
    }

    public VoldemortException getError(short code, String message) {
        Class<? extends VoldemortException> klass = codeToException.get(code);
        if(klass == null)
            return new UnknownFailure(Integer.toString(code));
        else
            return ReflectUtils.construct(klass, new Object[] { message });
    }

    public short getCode(VoldemortException e) {
        Short code = exceptionToCode.get(e.getClass());
        if(code == null)
            throw new IllegalArgumentException("No mapping code for " + e.getClass());
        else
            return code;
    }

}
