///*
//* Licensed to the Apache Software Foundation (ASF) under one
//* or more contributor license agreements.  See the NOTICE file
//* distributed with this work for additional information
//* regarding copyright ownership.  The ASF licenses this file
//* to you under the Apache License, Version 2.0 (the
//* "License"); you may not use this file except in compliance
//* with the License.  You may obtain a copy of the License at
//*
//*    http://www.apache.org/licenses/LICENSE-2.0
//*
//* Unless required by applicable law or agreed to in writing,
//* software distributed under the License is distributed on an
//* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//* KIND, either express or implied.  See the License for the
//* specific language governing permissions and limitations
//* under the License.
//*/
//package bes.graphs;
//
//import java.io.BufferedReader;
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.FileReader;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.Writer;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.LinkedHashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//public class PivotAndPlotResults
//{
//
//    public static void main(String[] args) throws IOException, InterruptedException
//    {
//        ExecutorType numerator = args.length > 2 ? ExecutorType.valueOf(args[1]) : ExecutorType.INJECTOR;
//        ExecutorType denominator = args.length > 1 ? ExecutorType.valueOf(args[args.length - 1]) : ExecutorType.JDK;
//        Results results = filter(read(args[0]), .75f, numerator, denominator);
//        for (String label : results.uniqueLabels.keySet())
//            plot(results, label, numerator, denominator, new File(args[0]));
//    }
//
//    private static Results read(String path) throws IOException
//    {
//        List<String> labels = Arrays.asList("(tasks)", "(opSleep)", "(opWork)", "(threads)");
//        List<String> renameLabels = Arrays.asList("requests (in:queue)", "sleep time per operation (incidence/micros)", "cputime per operation (micros)", "threads");
//        int[] lpos = new int[labels.size()];
//        List<String> measurementLabels = Arrays.asList("Score", "Score error", "(type)");
//        int[] mpos = new int[measurementLabels.size()];
//        Arrays.fill(lpos, -1);
//        Arrays.fill(mpos, -1);
//
//        Map<Comparison, Comparison> results = new LinkedHashMap<Comparison, Comparison>();
//        Map<String, Set<String>> uniqueLabels = new HashMap<String, Set<String>>();
//        for (String label : renameLabels)
//            uniqueLabels.put(label, new LinkedHashSet<String>());
//
//        BufferedReader reader = new BufferedReader(new FileReader(path));
//        try
//        {
//            String line;
//            boolean first = true;
//            while ( null != (line = reader.readLine()) )
//            {
//                if (first)
//                {
//                    String[] names = line.split("  +");
//                    for (int i = 0 ; i < names.length ; i++)
//                    {
//                        String name = names[i];
//                        int label = labels.indexOf(name);
//                        if (label >= 0)
//                            lpos[label] = i;
//                        label = measurementLabels.indexOf(name);
//                        if (label >= 0)
//                            mpos[label] = i;
//                    }
//                    first = false;
//                    continue;
//                }
//
//                String[] values = line.split(" +");
//                if (values.length <= 1)
//                    continue;
//                if (values[0].endsWith(":@cpi"))
//                    continue;
//                Comparison comparison = new Comparison();
//                for (int i = 0 ; i < lpos.length ; i++)
//                {
//                    comparison.labels.put(renameLabels.get(i), values[lpos[i]]);
//                    uniqueLabels.get(renameLabels.get(i)).add(values[lpos[i]]);
//                }
//                Measurement measurement = new Measurement(values[mpos[0]], values[mpos[1]]);
//                comparison.measurements.put(ExecutorType.valueOf(values[mpos[2]]), measurement);
//
//                Comparison prev = results.get(comparison);
//                if (prev != null)
//                    comparison.measurements.putAll(prev.measurements);
//                results.put(comparison, comparison);
//            }
//
//            return new Results(uniqueLabels, Arrays.asList(results.values().toArray(new Comparison[0])));
//        }
//        finally
//        {
//            reader.close();
//        }
//    }
//
//    static void plot(Results results, String label, ExecutorType numerator, ExecutorType denominator, File src) throws IOException, InterruptedException
//    {
//        File dir = src.getParentFile();
//        String prefix = src.getName();
//        List<Stats> stats = stats(results.comparisons, label, numerator, denominator, results.uniqueLabels.get(label));
//        String filelabel = label;
//        if (label.indexOf(' ') > 0)
//            filelabel = label.substring(0, label.indexOf(' '));
//        filelabel = prefix + "." + filelabel;
//        Writer out = new BufferedWriter(new FileWriter(new File(dir, filelabel + ".dat")));
//        double maxratio = 0d;
//        try
//        {
//            out.write("min d1 q1 med q3 d9 max\n");
//            int i = 1;
//            for (Stats stat : stats)
//            {
//                out.write(Integer.toString(i++));
//                out.write(" ");
//                out.write(stat.toString());
//                out.write("\n");
//                maxratio = Math.max(maxratio, stat.max);
//            }
//        }
//        finally
//        {
//            out.close();
//        }
//        File plotFile = new File(dir, filelabel + ".plot");
//        out = new BufferedWriter(new FileWriter(plotFile));
//        try
//        {
//            out.write(GNUPLOT_TEMPLATE
//                      .replaceAll("\\$labelcount", Integer.toString(stats.size() + 1))
//                      .replaceAll("\\$filelabel", filelabel)
//                      .replaceAll("\\$label", label)
//                      .replaceAll("\\$denominator", denominator.toString())
//                      .replaceAll("\\$numerator", numerator.toString())
//                      .replaceAll("\\$maxratio", String.format("%.0f", Math.ceil(maxratio)))
//            );
//        }
//        finally
//        {
//            out.close();
//        }
//        new ProcessBuilder().directory(dir)
//                            .redirectError(new File(dir, "err"))
//                            .redirectOutput(new File(dir, "err"))
//                            .command("gnuplot", plotFile.getPath())
//                            .start().waitFor();
//    }
//
//    static List<Stats> stats(List<Comparison> comparisons, String label, ExecutorType numerator, ExecutorType denominator, Collection<String> values)
//    {
//        List<Stats> results = new ArrayList<Stats>();
//        for (String value : values)
//        {
//            List<Comparison> filtered = filter(comparisons, label, value);
//            double[] ratios = ratios(filtered, numerator, denominator);
//            Stats stats = new Stats(value, ratios);
//            results.add(stats);
//        }
//        return results;
//    }
//
//    static List<Comparison> filter(List<Comparison> comparisons, String label, String value)
//    {
//        List<Comparison> results = new ArrayList<Comparison>();
//        for (Comparison comparison : comparisons)
//            if (comparison.labels.get(label).equals(value))
//                results.add(comparison);
//        return results;
//    }
//
//    static Results filter(Results in, float percError, ExecutorType ... types)
//    {
//        List<Comparison> results = new ArrayList<Comparison>();
//        for (Comparison comparison : in.comparisons)
//        {
//            boolean accept = true;
//            for (ExecutorType type : types)
//            {
//                Measurement measurement = comparison.measurements.get(type);
//                accept &= measurement.err / measurement.score < percError;
//            }
//            if (accept)
//                results.add(comparison);
//            else
//                System.err.printf("Filtering %s due to measurement error\n", comparison);
//        }
//        return new Results(in.uniqueLabels, results);
//    }
//
//    static double[] ratios(List<Comparison> comparisons, ExecutorType numerator, ExecutorType denominator)
//    {
//        double[] results = new double[comparisons.size()];
//        int i = 0;
//        for (Comparison comparison : comparisons)
//            results[i++] = comparison.measurements.get(numerator).score
//                           / comparison.measurements.get(denominator).score;
//        return results;
//    }
//
//    private static class Results
//    {
//        final Map<String, Set<String>> uniqueLabels;
//        final List<Comparison> comparisons;
//
//        private Results(Map<String, Set<String>> uniqueLabels, List<Comparison> comparisons)
//        {
//            this.uniqueLabels = uniqueLabels;
//            this.comparisons = comparisons;
//        }
//    }
//
//    private static class Stats
//    {
//        final String label;
//        final double min, d1, q1, median, mean, q3, d9, max;
//        private Stats(String label, double[] values)
//        {
//            this.label = label;
//            Arrays.sort(values);
//            min = values[0];
//            max = values[values.length - 1];
//            if (values.length % 2 == 0)
//                median = values[values.length / 2];
//            else
//                median = (values[values.length / 2] + values[1 + (values.length / 2)]) / 2;
//            q1 = values[(int)Math.round(values.length * (1.0/4))];
//            q3 = values[(int)Math.round(values.length * (3.0/4))];
//            d1 = values[(int)Math.round(values.length * (1.0/10))];
//            d9 = values[(int)Math.round(values.length * (9.0/10))];
//            double sum = 0;
//            for (double v : values)
//                sum += v;
//            mean = sum / values.length;
//        }
//        public String toString()
//        {
//            return String.format("%.2f %.2f %.2f %.2f %.2f %.2f %.2f %s", min, d1, q1, median, q3, d9, max, label);
//        }
//    }
//
//    private static class Comparison
//    {
//        final Map<String, String> labels = new HashMap<String, String>();
//        final Map<ExecutorType, Measurement> measurements = new HashMap<ExecutorType, Measurement>();
//        public int hashCode()
//        {
//            return labels.hashCode();
//        }
//        public boolean equals(Object that)
//        {
//            return that instanceof Comparison && ((Comparison) that).labels.equals(labels);
//        }
//
//        public String toString()
//        {
//            return "(" + labels.toString() + "," + measurements.toString() + ")";
//        }
//    }
//
//    private static class Measurement
//    {
//        final double score;
//        final double err;
//        private Measurement(String score, String err)
//        {
//            this.score = Double.parseDouble(score);
//            this.err = Double.parseDouble(err);
//        }
//        public String toString()
//        {
//            return String.format("%.0f +/-%.0f%%", score, 100*err/score);
//        }
//    }
//
//
//    private static final String GNUPLOT_TEMPLATE =
//        "set terminal png\n" +
//        "set output \"$filelabel.png\"\n" +
//        "set bars 2.0\n" +
//        "set style fill empty\n" +
//        "set title \"$numerator vs $denominator\"\n" +
//        "set xtics rotate by -45\n" +
//        "\n" +
//        "set boxwidth .5 relative\n" +
//        "set xrange[0:$labelcount]\n" +
//        "set xlabel \"$label\"\n" +
//        "set ylabel \"Performance Ratio\"\n" +
//        "set yrange[0:$maxratio]\n" +
//        "set linetype 1 lw 2 lc rgb \"blue\" pointtype 6\n" +
//        "set linetype 1 lw 2 lc rgb \"red\" pointtype 6\n" +
//        "plot '$filelabel.dat' using 1:3:2:8:7:xticlabels(9) with candlesticks title 'Deciles' whiskerbars 0.25, \\\n" +
//        "     '$filelabel.dat' using 1:4:3:7:6:xticlabels(9) with candlesticks title 'Quartiles'";
//
//}
