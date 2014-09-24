/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package bes.bench.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Benchmark)
public class HashOrderedCollections
{

    static enum Type
    {
        NBHOM, CSLM
    }

    private static Long[] KEYS = new Long[Integer.parseInt(System.getProperty("keyCount"))];
    static
    {
        for (int i = 0 ; i < KEYS.length ; i++)
            KEYS[i] = ThreadLocalRandom.current().nextLong();
    }

    @Param({"0.1"})
    private double readWriteRatio;

    @Param({"NBHOM", "CSLM"})
    private String type;

    @Param("1000000")
    private int warmup;

    @Param("100")
    private int batchSize;

    private InsertOnlyOrderedMap<Long, Long> map;
    private final AtomicInteger nextInsert = new AtomicInteger();

    @State(Scope.Thread)
    public static class ThreadState
    {
        ThreadLocalRandom random;
        int insertOffset;
        int insertsRemaining;
        int readOffset;
        int readsRemaining;
    }

    @Setup(Level.Iteration)
    public void setup() throws InterruptedException
    {
        nextInsert.set(this.warmup);
        map = newMap();
        final int processors = Runtime.getRuntime().availableProcessors();
        final int warmUp = this.warmup / processors;
        ExecutorService exec = Executors.newFixedThreadPool(processors);
        for (int i = 0 ; i < processors ; i++)
        {
            final int offset = warmUp * i;
            exec.execute(new Runnable()
            {
                public void run()
                {
                    for (int i = 0 ; i < warmUp ; i++)
                    {
                        Long key = KEYS[offset + i];
                        map.putIfAbsent(key, key);
                    }
                }
            });
        }
        exec.shutdown();
        exec.awaitTermination(1L, TimeUnit.DAYS);
        System.gc();
    }

    @TearDown(Level.Iteration)
    public void teardown() throws InterruptedException
    {
        nextInsert.set(this.warmup);
        Thread.sleep(10);
        map.clear();
        map = null;
    }

    private InsertOnlyOrderedMap<Long, Long> newMap()
    {
        switch (Type.valueOf(type))
        {
            case CSLM:
                return new InsertOnlyOrderedMap.Adapter<>(new ConcurrentSkipListMap<Long, Long>());
            case NBHOM:
                return new NonBlockingHashOrderedMap<>();
        }
        throw new IllegalStateException();
    }

    @Benchmark
    public void test(ThreadState state)
    {
        if (state.random == null)
            state.random = ThreadLocalRandom.current();
        if (state.random.nextFloat() <= readWriteRatio)
        {
            if (state.readsRemaining == 0)
            {
                int modulus = Math.max(0, nextInsert.get() - batchSize);
                state.readOffset = state.random.nextInt(KEYS.length) % modulus;
                state.readsRemaining = batchSize;
            }
            int index = state.readOffset++;
            state.readsRemaining--;
            map.get(KEYS[index]);
        }
        else
        {
            if (state.insertsRemaining == 0)
            {
                state.insertOffset = nextInsert.addAndGet(batchSize);
                state.insertsRemaining = batchSize;
            }
            int index = state.insertOffset++;
            state.insertsRemaining--;
            Long key = KEYS[index];
            map.putIfAbsent(key, key);
        }
    }

    public static void main(String[] args) throws RunnerException, InterruptedException
    {
        boolean addPerf = false, printGc = false;
        int keyCount = 1 << 24;
        List<String> vmArgs = new ArrayList<>();
        Map<String, Integer> jmhParams = new HashMap<String, Integer>();
        jmhParams.put("forks", 1);
        jmhParams.put("threads", 4);
        jmhParams.put("warmups", 5);
        jmhParams.put("warmupLength", 1);
        jmhParams.put("measurements", 10);
        jmhParams.put("measurementLength", 1);
        Map<String, String[]> benchParams = new LinkedHashMap<String, String[]>();
        benchParams.put("type", new String[] { "CSLM", "NBHOM" });
        benchParams.put("readWriteRatio", new String[] { "0.9", "0.5", "0.1", "0" });
        benchParams.put("warmup", new String[] { "1000000" });
        benchParams.put("batchSize", new String[] { "100" });
        for (String arg : args)
        {
            if (arg.equals("-perf"))
            {
                addPerf = true;
                continue;
            }
            if (arg.equals("-gc"))
            {
                printGc = true;
                continue;
            }
            if (arg.startsWith("-"))
            {
                vmArgs.add(arg);
                continue;
            }
            String[] split = arg.split("=");
            if (split.length != 2)
                throw new IllegalArgumentException(arg + " malformed");
            if (jmhParams.containsKey(split[0]))
                jmhParams.put(split[0], Integer.parseInt(split[1]));
            else if (benchParams.containsKey(split[0]))
                benchParams.put(split[0], split[1].split(","));
            else if (split[0].equals("keyCount"))
                keyCount = Integer.parseInt(split[1]);
            else
                throw new IllegalArgumentException(arg + " unknown property");
        }

        if (vmArgs.isEmpty())
            vmArgs.add("-Xmx2G");

        ChainedOptionsBuilder builder = new OptionsBuilder()
            .include(".*HashOrderedCollections.*")
            .forks(jmhParams.get("forks"))
            .threads(jmhParams.get("threads"))
            .warmupIterations(jmhParams.get("warmups"))
            .warmupTime(TimeValue.seconds(jmhParams.get("warmupLength")))
            .measurementIterations(jmhParams.get("measurements"))
            .measurementTime(TimeValue.seconds(jmhParams.get("measurementLength")))
            .jvmArgs("-dsa", "-da", "-server", "-DkeyCount=" + keyCount);

        if (printGc)
        {
            vmArgs.add("-XX:+PrintGC");
            vmArgs.add("-XX:+PrintGCTimeStamps");
        }

        builder.jvmArgsAppend(vmArgs.toArray(new String[0]));

        if (addPerf)
            builder.addProfiler(org.openjdk.jmh.profile.LinuxPerfAsmProfiler.class);

        System.out.println("Running with:");
        System.out.println(jmhParams);
        for (Map.Entry<String, String[]> e : benchParams.entrySet())
        {
            System.out.println(e.getKey() + ": " + Arrays.toString(e.getValue()));
            builder.param(e.getKey(), e.getValue());
        }
        new Runner(builder.build()).run();
    }
}
