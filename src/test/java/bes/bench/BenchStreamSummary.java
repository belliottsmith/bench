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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Adler32;

import com.clearspring.analytics.stream.StreamSummary;
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
public class BenchStreamSummary
{

    @Param({"1024"})
    private int summarySize;

    @Param({"1..100000"})
    private String valueRange;

    @Param({"64{8}..64{8}..4096{16}"})
    private String keySpec;

    private StreamSummary summary;
    private String[] keys;
    private int[] values;

    @State(Scope.Thread)
    public static class ThreadState
    {
        int index = 0;
    }

    public static class KeyTier
    {
        final int count;
        final int length;
        final String[] options;
        static Pattern specPattern = Pattern.compile("([0-9]+)\\{([0-9]+)\\}");

        public KeyTier(String spec)
        {
            Matcher m = specPattern.matcher(spec);
            if (!m.matches())
                throw new IllegalArgumentException();
            count = Integer.parseInt(m.group(1));
            length = Integer.parseInt(m.group(2));
            options = new String[count];
            byte[] bytes = new byte[length];
            for (int i = 0 ; i < count ; i++)
            {
                ThreadLocalRandom.current().nextBytes(bytes);
                options[i] = new String(bytes);
            }
        }
    }

    @Setup
    public void setup()
    {
        summary = new StreamSummary(summarySize);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String[] valueRange = this.valueRange.split("\\.\\.");
        int lb = Integer.parseInt(valueRange[0]);
        int ub = Integer.parseInt(valueRange[1]);
        List<KeyTier> keyTiers = new ArrayList<>();
        for (String spec : keySpec.split("\\.\\."))
            keyTiers.add(new KeyTier(spec));

        int count = 1;
        for (KeyTier tier : keyTiers)
            count *= tier.count;

        keys = new String[count];
        values = new int[count];
        StringBuilder builder = new StringBuilder();
        for (int i = 0 ; i < keys.length ; i++)
        {
            values[i] = (int) Math.sqrt(random.nextInt(lb, ub * ub));
            builder.setLength(0);
            for (KeyTier tier : keyTiers)
                builder.append(tier.options[random.nextInt(tier.count)]);
            keys[i] = builder.toString();
        }
    }

    @Benchmark
    public void stream(ThreadState state)
    {
        synchronized (summary)
        {
            summary.offer(new String(keys[state.index % keys.length]), values[state.index % keys.length]);
            state.index++;
        }
    }

    public static void main(String[] args) throws RunnerException, InterruptedException
    {
        boolean addPerf = false;
        Map<String, Integer> jmhParams = new HashMap<String, Integer>();
        jmhParams.put("forks", 1);
        jmhParams.put("threads", 1);
        jmhParams.put("warmups", 5);
        jmhParams.put("warmupLength", 1);
        jmhParams.put("measurements", 15);
        jmhParams.put("measurementLength", 2);
        Map<String, String[]> benchParams = new LinkedHashMap<String, String[]>();
        benchParams.put("summarySize", new String[] { "1024" });
        benchParams.put("valueRange", new String[] { "1..100000" });
        benchParams.put("keySpec", new String[] { "64{8}..64{8}..2048{16}" });
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
            .include(".*StreamSummary.*")
            .forks(jmhParams.get("forks"))
            .threads(jmhParams.get("threads"))
            .warmupIterations(jmhParams.get("warmups"))
            .warmupTime(TimeValue.seconds(jmhParams.get("warmupLength")))
            .measurementIterations(jmhParams.get("measurements"))
            .measurementTime(TimeValue.seconds(jmhParams.get("measurementLength")))
            .jvmArgs("-dsa", "-da", "-Xmx2G");

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
