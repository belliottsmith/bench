/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bes.bench.collections;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class AtomicReferenceArrayUpdater<V>
{

    final long offset;
    final int shift;

    public AtomicReferenceArrayUpdater(Class<V[]> arrayType)
    {
        if (!arrayType.isArray())
            throw new IllegalArgumentException();
        offset = unsafe.arrayBaseOffset(arrayType);
        shift = Integer.bitCount(unsafe.arrayIndexScale(arrayType) - 1);
    }

    public boolean compareAndSet(V[] array, int index, V exp, V upd)
    {
        assert index >= 0 && index < array.length;
        return unsafe.compareAndSwapObject(array, offset + (index << shift), exp, upd);
    }

    static final Unsafe unsafe;
    static
    {
        try
        {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }
}
