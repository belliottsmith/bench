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
package bes.bench;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.zip.Adler32;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
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
public class Compression
{

    @Param({"65536"})
    private int pageSize;

    @Param({"8192"})
    private int uniquePages;

    @Param({"0.1"})
    private double randomRatio;

    @Param({"4..16"})
    private String randomRunLength;

    @Param({"4..128"})
    private String duplicateLookback;

    private byte[][] lz4Bytes;
    private byte[][] rawBytes;

    private LZ4FastDecompressor lz4Decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    private LZ4Compressor lz4Compressor = LZ4Factory.fastestInstance().fastCompressor();

    @State(Scope.Thread)
    public static class ThreadState
    {
        byte[] bytes;
        Adler32 adler32 = new Adler32();
    }

    @Setup
    public void setup()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] randomRunLength = range(this.randomRunLength);
        int[] duplicateLookback = range(this.duplicateLookback);
        rawBytes = new byte[uniquePages][pageSize];
        lz4Bytes = new byte[uniquePages][];
        byte[][] runs = new byte[duplicateLookback[1] - duplicateLookback[0]][];
        for (int i = 0 ; i < rawBytes.length ; i++)
        {
            byte[] trg = rawBytes[0];
            int runCount = 0;
            int byteCount = 0;
            while (byteCount < trg.length)
            {
                byte[] nextRun;
                if (runCount == 0 || random.nextDouble() < this.randomRatio)
                {
                    nextRun = new byte[random.nextInt(randomRunLength[0], randomRunLength[1])];
                    random.nextBytes(nextRun );
                    runs[runCount % runs.length] = nextRun;
                    runCount++;
                }
                else
                {
                    int index = runCount < duplicateLookback[1]
                                ? random.nextInt(runCount)
                                : (runCount - random.nextInt(duplicateLookback[0], duplicateLookback[1]));
                    nextRun = runs[index % runs.length];
                }
                System.arraycopy(nextRun, 0, trg, byteCount, Math.min(nextRun.length, trg.length - byteCount));
                byteCount += nextRun.length;
            }
            lz4Bytes[i] = lz4Compressor.compress(trg);
        }
    }

    static int[] range(String spec)
    {
        String[] split = spec.split("\\.\\.");
        return new int[] { Integer.parseInt(split[0]), Integer.parseInt(split[1]) };
    }

    @Benchmark
    public void lz4(ThreadState state)
    {
        if (state.bytes == null)
            state.bytes = new byte[this.pageSize];
        byte[] in = lz4Bytes[ThreadLocalRandom.current().nextInt(lz4Bytes.length)];
        lz4Decompressor.decompress(in, state.bytes);
    }

    @Benchmark
    public void adler32(ThreadState state)
    {
        byte[] in = rawBytes[ThreadLocalRandom.current().nextInt(rawBytes.length)];
        state.adler32.reset();
        state.adler32.update(in);
        state.adler32.getValue();
    }

    public static void main(String[] args) throws RunnerException, InterruptedException
    {
        boolean addPerf = false;
        Map<String, Integer> jmhParams = new HashMap<String, Integer>();
        jmhParams.put("forks", 1);
        jmhParams.put("threads", 1);
        jmhParams.put("warmups", 5);
        jmhParams.put("warmupLength", 1);
        jmhParams.put("measurements", 5);
        jmhParams.put("measurementLength", 2);
        Map<String, String[]> benchParams = new LinkedHashMap<String, String[]>();
        benchParams.put("pageSize", new String[] { "65536" });
        benchParams.put("uniquePages", new String[] { "8192" });
        benchParams.put("randomRatio", new String[] { "0", "0.1", "1.0" });
        benchParams.put("randomRunLength", new String[] { "4..16", "128..512" });
        benchParams.put("duplicateLookback", new String[] { "4..128" });
        for (String arg : args)
        {
            if (arg.equals("-perf"))
            {
                addPerf = true;
                continue;
            }
            String[] split = arg.split("=");
            if (split.length != 2)
                throw new IllegalArgumentException(arg + " malformed");
            if (jmhParams.containsKey(split[0]))
                jmhParams.put(split[0], Integer.parseInt(split[1]));
            else if (benchParams.containsKey(split[0]))
                benchParams.put(split[0], split[1].split(","));
            else
                throw new IllegalArgumentException(arg + " unknown property");
        }

        ChainedOptionsBuilder builder = new OptionsBuilder()
            .include(".*Compression.*")
            .forks(jmhParams.get("forks"))
            .threads(jmhParams.get("threads"))
            .warmupIterations(jmhParams.get("warmups"))
            .warmupTime(TimeValue.seconds(jmhParams.get("warmupLength")))
            .measurementIterations(jmhParams.get("measurements"))
            .measurementTime(TimeValue.seconds(jmhParams.get("measurementLength")))
            .jvmArgs("-dsa", "-da");

        if (addPerf)
            builder.addProfiler(org.openjdk.jmh.profile.LinuxPerfProfiler.class);

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
