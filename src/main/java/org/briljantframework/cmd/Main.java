package org.briljantframework.cmd;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.cli.*;
import org.apache.commons.math3.util.Pair;
import org.briljantframework.data.series.Series;
import org.briljantframework.mimir.classification.ClassifierEvaluator;
import org.briljantframework.mimir.classification.ClassifierValidator;
import org.briljantframework.mimir.classification.EnsembleEvaluator;
import org.briljantframework.mimir.classification.ProbabilityEstimator;
import org.briljantframework.mimir.classification.tree.ClassSet;
import org.briljantframework.mimir.classification.tree.TreeBranch;
import org.briljantframework.mimir.classification.tree.TreeLeaf;
import org.briljantframework.mimir.classification.tree.TreeNode;
import org.briljantframework.mimir.classification.tree.pattern.PatternDistance;
import org.briljantframework.mimir.classification.tree.pattern.PatternFactory;
import org.briljantframework.mimir.classification.tree.pattern.PatternTree;
import org.briljantframework.mimir.classification.tree.pattern.RandomPatternForest;
import org.briljantframework.mimir.data.*;
import org.briljantframework.mimir.data.timeseries.MultivariateTimeSeries;
import org.briljantframework.mimir.data.timeseries.TimeSeries;
import org.briljantframework.mimir.distance.EarlyAbandonSlidingDistance;
import org.briljantframework.mimir.evaluation.Result;
import org.briljantframework.mimir.evaluation.partition.Partition;
import org.briljantframework.mimir.evaluation.partition.Partitioner;
import org.briljantframework.mimir.shapelet.IndexSortedNormalizedShapelet;
import org.briljantframework.mimir.shapelet.MultivariateShapelet;
import org.briljantframework.mimir.shapelet.Shapelet;
import org.briljantframework.util.sort.ElementSwapper;

/**
 * A simple command line utility for running the random shapelet forest.
 */
public class Main {

  private static long getTotalThreadCPUTime() {
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    threadMXBean.setThreadCpuTimeEnabled(true);
    long[] ids = threadMXBean.getAllThreadIds();
    long sum = 0;
    for (long id : ids) {
      long contrib = threadMXBean.getThreadCpuTime(id);
      if (contrib != -1)
        sum += contrib;
    }
    return sum;
  }

  public static void main(String[] args) {
    //args = new String[] {"-n", "100", "-l", "0.025", "-u", "1", "-r", "10",
    //        "dataset/synthetic_control/synthetic_control_TRAIN",
    //        "dataset/synthetic_control/synthetic_control_TEST"};
    
    args = new String[] {"dataset/synthetic_control/synthetic_control_TRAIN",
            "dataset/synthetic_control/synthetic_control_TEST"};

    // String s = "-r 10 -s 0.3 -m -w /Users/isak/Downloads/dataSets/Cricket/xleft.txt
    // /Users/isak/Downloads/dataSets/Cricket/xright.txt
    // /Users/isak/Downloads/dataSets/Cricket/yleft.txt
    // /Users/isak/Downloads/dataSets/Cricket/yright.txt
    // /Users/isak/Downloads/dataSets/Cricket/zleft.txt
    // /Users/isak/Downloads/dataSets/Cricket/zright.txt";
    // args = s.split(" ");
    Options options = new Options();

    options.addOption("n", "no-trees", true, "Number of trees");
    options.addOption("l", "lower", true, "Lower shapelet size (fraction of length, e.g, 0.05)");
    options.addOption("u", "upper", true, "Upper shapelet size (fraction of length, e.g, 0.8)");
    options.addOption("r", "sample", true, "Number of shapelets");
    options.addOption("p", "print-shapelets", false, "Print the shapelets of the forest");
    options.addOption("m", "multivariate", false, "The given dataset is in a multivariate format");
    options.addOption("c", "cv", true, "Combine datasets and run cross validation");
    options.addOption("w", "weird", false, "Weird mts-format");
    options.addOption("s", "split", true, "Combine datasets and use split validation");
    options.addOption("o", "optimize", false, "optimize the parameters using oob");
    options.addOption("d", "csv-delim", false, "Present the results as a comma separated list");
    CommandLineParser parser = new DefaultParser();
    try {
      CommandLine cmd = parser.parse(options, args);
      int noTrees = Integer.parseInt(cmd.getOptionValue("n", "100"));
      double lower = Double.parseDouble(cmd.getOptionValue("l", "0.025"));
      double upper = Double.parseDouble(cmd.getOptionValue("u", "1"));
      int r = Integer.parseInt(cmd.getOptionValue("r", "100"));
      boolean print = cmd.hasOption("p");

      List<String> files = cmd.getArgList();
      if (files == null || files.isEmpty()) {
        throw new RuntimeException("Training/testing data missing");
      }

      // Compute the minimum distance between the shapelet and the time series
      PatternDistance<MultivariateTimeSeries, MultivariateShapelet> patternDistance =
          new PatternDistance<MultivariateTimeSeries, MultivariateShapelet>() {
            private EarlyAbandonSlidingDistance distance = new EarlyAbandonSlidingDistance();

            public double computeDistance(MultivariateTimeSeries a, MultivariateShapelet b) {
              return distance.compute(a.getDimension(b.getDimension()), b.getShapelet());
            }
          };

      Pair<Input<MultivariateTimeSeries>, Output<Object>> train;
      ClassifierValidator<MultivariateTimeSeries, Object> validator;
      if (cmd.hasOption("c") || cmd.hasOption("s")) {
        Input<MultivariateTimeSeries> t = new ArrayInput<>();
        Output<Object> o = new ArrayOutput<>();
        if (cmd.hasOption("m") && cmd.hasOption("w")) {
          List<Pair<Input<MultivariateTimeSeries>, Output<Object>>> list = new ArrayList<>();
          for (String file : files) {
            list.add(readData(file));
          }
          t.addAll(getMultivariateTimeSeries(list));
          o.addAll(list.get(0).getSecond());
        } else if (cmd.hasOption("m")) {
          for (String file : files) {
            Pair<Input<MultivariateTimeSeries>, Output<Object>> data = readMtsData(file);
            t.addAll(data.getFirst());
            o.addAll(data.getSecond());
          }
        } else {
          for (String file : files) {
            Pair<Input<MultivariateTimeSeries>, Output<Object>> data = readData(file);
            t.addAll(data.getFirst());
            o.addAll(data.getSecond());
          }
        }
        ((ElementSwapper) (a, b) -> {
          MultivariateTimeSeries tmp = t.get(a);
          t.set(a, t.get(b));
          t.set(b, tmp);

          Object tmp2 = o.get(a);
          o.set(a, o.get(b));
          o.set(b, tmp2);
        }).permute(t.size());
        train = new Pair<>(t, o);
        if (cmd.hasOption("c")) {
          validator =
              ClassifierValidator.crossValidator(Integer.parseInt(cmd.getOptionValue("c", "10")));
        } else {
          validator = ClassifierValidator
              .splitValidator(Double.parseDouble(cmd.getOptionValue("s", "0.3")));
        }
      } else {
        Pair<Input<MultivariateTimeSeries>, Output<Object>> test;
        if (cmd.hasOption("m")) {
          if (files.size() == 2) {
            train = readMtsData(files.get(0));
            test = readMtsData(files.get(1));
          } else {
            List<Pair<Input<MultivariateTimeSeries>, Output<Object>>> lTest = new ArrayList<>();
            List<Pair<Input<MultivariateTimeSeries>, Output<Object>>> lTrain = new ArrayList<>();
            for (int i = 0; i < files.size() - 1; i += 2) {
              String trainFile = files.get(i);
              String testFile = files.get(i + 1);
              Pair<Input<MultivariateTimeSeries>, Output<Object>> pTrain = readData(trainFile);
              Pair<Input<MultivariateTimeSeries>, Output<Object>> pTest = readData(testFile);
              lTrain.add(pTrain);
              lTest.add(pTest);
            }
            Input<MultivariateTimeSeries> testIn = getMultivariateTimeSeries(lTest);
            Input<MultivariateTimeSeries> trainIn = getMultivariateTimeSeries(lTrain);
            train = new Pair<>(trainIn, lTrain.get(0).getSecond());
            test = new Pair<>(testIn, lTest.get(0).getSecond());
          }
        } else {
          train = readData(files.get(0));
          test = readData(files.get(1));
        }
        // validator = ClassifierValidator.holdoutValidator(test.getFirst(), test.getSecond());
        validator = new ClassifierValidator<MultivariateTimeSeries, Object>(
            Collections.singleton(ClassifierEvaluator.getInstance()),
            new Partitioner<MultivariateTimeSeries, Object>() {
              @Override
              public Collection<Partition<MultivariateTimeSeries, Object>> partition(
                  Input<? extends MultivariateTimeSeries> x, Output<?> y) {
                return Collections.singleton(new Partition<>(Inputs.unmodifiableInput(x),
                    Inputs.unmodifiableInput(test.getFirst()), Outputs.unmodifiableOutput(y),
                    Outputs.unmodifiableOutput(test.getSecond())));
              }
            }) {

          @Override
          protected long preFit() {
            return getTotalThreadCPUTime();
          }

          @Override
          protected double postFit(long start) {
            return getTotalThreadCPUTime()/1e6;
          }

          @Override
          protected long prePredict() {
            return getTotalThreadCPUTime();
          }

          @Override
          protected double postPredict(long start) {
            return getTotalThreadCPUTime()/1e6;
          }
        };
      }
      validator.add(EnsembleEvaluator.getInstance());
      List<MultivariateShapelet> shapelets = new ArrayList<>();
      if (print) {
        validator.add(ctx -> {
          RandomPatternForest<MultivariateTimeSeries, Object> f =
              (RandomPatternForest<MultivariateTimeSeries, Object>) ctx.getPredictor();
          for (ProbabilityEstimator<MultivariateTimeSeries, Object> m : f.getEnsembleMembers()) {
            PatternTree<MultivariateTimeSeries, Object> t =
                (PatternTree<MultivariateTimeSeries, Object>) m;
            extractShapelets(shapelets, t.getRootNode());
          }
        });
      }
      Result<Object> result = null;
      double totalFitTime = 0;
      double totalPredictTime = 0;
      double[] minLu = null;
      int minR = -1;
      if (cmd.hasOption("o")) {
        //@formatter:off
        double[][] lowerUpper = {
            {0.025, 1},
            {0.025, 0.1},
            {0.025, 0.2},
            {0.025, 0.3},
            {0.025, 0.4},
            {0.2, 0.5},
            {0.3, 0.6},
            {0.6, 1},
            {0.7, 1},
            {0.8, 1},
            {0.9, 1}
        };
        //@formatter:on

        int[] ropt = {1, 10,50,100, 500, -1};
        double minOobError = Double.POSITIVE_INFINITY;
        for (double[] lu : lowerUpper) {
          for (int rv : ropt) {
            if (rv == -1) {
              int m = train.getFirst().get(0).getDimension(0).size();
              int d = train.getFirst().get(0).dimensions();
              rv = (int) Math.sqrt(m * d * (m * d + 1) / 2);
            }
            PatternFactory<MultivariateTimeSeries, MultivariateShapelet> patternFactory =
                getPatternFactory(lu[0], lu[1]);

            RandomPatternForest.Learner<MultivariateTimeSeries, Object> rsf =
                new RandomPatternForest.Learner<>(patternFactory, patternDistance, noTrees);
            rsf.set(PatternTree.PATTERN_COUNT, rv);
            Result<Object> res = validator.test(rsf, train.getFirst(), train.getSecond());
            totalFitTime += res.getFitTime();
            totalPredictTime += res.getPredictTime();
            Series measures = res.getMeasures().reduce(Series::mean);
            if (measures.getDouble("oobError") < minOobError) {
              result = res;
              minLu = lu;
              minR = rv;
              minOobError = measures.getDouble("oobError");
            }
          }
        }
      } else {
        PatternFactory<MultivariateTimeSeries, MultivariateShapelet> patternFactory =
            getPatternFactory(lower, upper);

        RandomPatternForest.Learner<MultivariateTimeSeries, Object> rsf =
            new RandomPatternForest.Learner<>(patternFactory, patternDistance, noTrees);
        rsf.set(PatternTree.PATTERN_COUNT, r);
        result = validator.test(rsf, train.getFirst(), train.getSecond());
      }

      if (print) {
        shapelets.sort((a, b) -> Integer.compare(a.getShapelet().size(), b.getShapelet().size()));
        for (int i = 0; i < shapelets.size(); i++) {
          System.out.print(i + "\t");
          Shapelet shapelet = shapelets.get(i).getShapelet();
          /*for (int j = 0; j < shapelet.size(); j++) {
            System.out.print(shapelet.getDouble(j) + " ");
          }*/
          System.out.println(shapelet.size());
        }
      }

      Series measures = result.getMeasures().reduce(Series::mean);
      if (cmd.hasOption("d")) {
        // Format as csv
        // accuracy, aucRoc, totalFitTime, totalPredictTime, lu, r
        System.out.println(measures.get("accuracy") + "," + measures.get("aucRoc") + "," + totalFitTime + "," + totalPredictTime + "," + Arrays.toString(minLu) + "," + minR);
      } else {
        System.out.println("Parameters");
        System.out.println("**********");
        for (Option o : cmd.getOptions()) {
          System.out.printf("%s:  %s\n", o.getLongOpt(), o.getValue("[default]"));
        }
        if (files.size() == 2) {
          System.out.printf("Training data '%s'\n", files.get(0));
          System.out.printf("Testing data  '%s'\n", files.get(1));
        }
        System.out.println(" ---- ---- ---- ---- ");

        System.out.println("\nResults");
        System.out.println("*******");
        for (Object key : measures.index()) {
          System.out.printf("%s:  %.4f\n", key, measures.getDouble(key));
        }
        System.out.println(" ---- ---- ---- ---- ");
        System.out.printf("Runtime (training)  %.2f ms (CPU TIME)\n", result.getFitTime());
        System.out.printf("Runtime (testing)   %.2f ms (CPU TIME)\n", result.getPredictTime());
      }
    } catch (Exception e) {
      HelpFormatter formatter = new HelpFormatter();
      e.printStackTrace();
      formatter.printHelp("rsfcmd.jar [OPTIONS] trainFile testFile", options);
    }
  }

  private static PatternFactory<MultivariateTimeSeries, MultivariateShapelet> getPatternFactory(
      final double lowFrac, final double uppFrac) {
    return new PatternFactory<MultivariateTimeSeries, MultivariateShapelet>() {

      /**
       * @param inputs the input dataset
       * @param classSet the inputs included in the current bootstrap.
       * @return a shapelet
       */
      public MultivariateShapelet createPattern(Input<? extends MultivariateTimeSeries> inputs,
          ClassSet classSet) {
        MultivariateTimeSeries mts =
            inputs.get(classSet.getRandomSample().getRandomExample().getIndex());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int randomDim = random.nextInt(mts.dimensions());
        TimeSeries uts = mts.getDimension(randomDim);
        int timeSeriesLength = uts.size();
        int upper = (int) Math.round(timeSeriesLength * uppFrac);
        int lower = (int) Math.round(timeSeriesLength * lowFrac);
        if (lower < 2) {
          lower = 2;
        }

        if (Math.addExact(upper, lower) > timeSeriesLength) {
          upper = timeSeriesLength - lower;
        }
        if (lower == upper) {
          upper -= 2;
        }
        if (upper < 1) {
          return null;
        }

        int length = ThreadLocalRandom.current().nextInt(upper) + lower;
        int start = ThreadLocalRandom.current().nextInt(timeSeriesLength - length);
        return new MultivariateShapelet(randomDim,
            new IndexSortedNormalizedShapelet(start, length, uts));
      }
    };
  }

  private static Input<MultivariateTimeSeries> getMultivariateTimeSeries(
      List<Pair<Input<MultivariateTimeSeries>, Output<Object>>> data) {
    Input<MultivariateTimeSeries> input = new ArrayInput<>();
    int n = data.get(0).getFirst().size();
    for (int i = 0; i < n; i++) {
      TimeSeries[] trainSeries = new TimeSeries[data.size()];
      for (int j = 0; j < data.size(); j++) {
        trainSeries[j] = data.get(j).getFirst().get(i).getDimension(0);
      }
      input.add(new MultivariateTimeSeries(trainSeries));
    }
    return input;
  }

  private static void extractShapelets(List<MultivariateShapelet> shapelets,
      TreeNode<MultivariateTimeSeries, ?> node) {
    if (node instanceof TreeLeaf) {
      return;
    }

    @SuppressWarnings("unchecked")
    TreeBranch<MultivariateTimeSeries, PatternTree.Threshold<MultivariateShapelet>> b =
        (TreeBranch<MultivariateTimeSeries, PatternTree.Threshold<MultivariateShapelet>>) node;
    shapelets.add(b.getThreshold().getPattern());
    extractShapelets(shapelets, b.getLeft());
    extractShapelets(shapelets, b.getRight());
  }

  private static Pair<Input<MultivariateTimeSeries>, Output<Object>> readData(String filePath)
      throws IOException {
    // Construct the input and output variables
    Input<MultivariateTimeSeries> input = new ArrayInput<>();
    Output<Object> output = new ArrayOutput<>();

    // Read the file
    List<String> data = Files.readAllLines(Paths.get(filePath));
    // Collections.shuffle(data, ThreadLocalRandom.current());
    for (String line : data) {
      String[] split = line.trim().split("\\s+");
      output.add((int) Double.parseDouble(split[0]));

      TimeSeries timeSeries = getTimeSeries(1, split);
      input.add(new MultivariateTimeSeries(timeSeries));
    }
    return new Pair<>(input, output);
  }

  private static TimeSeries getTimeSeries(int start, String[] split) {
    double[] ts = new double[split.length - start];
    for (int i = start; i < split.length; i++) {
      ts[i - start] = Double.parseDouble(split[i]);
    }
    return TimeSeries.of(ts);
  }

  private static Pair<Input<MultivariateTimeSeries>, Output<Object>> readMtsData(String folder)
      throws IOException {
    Input<MultivariateTimeSeries> input = new ArrayInput<>();
    Output<Object> output = new ArrayOutput<>();

    String data = new String(Files.readAllBytes(Paths.get(folder, "classes.dat")));
    Collections.addAll(output, data.trim().split(","));

    List<Path> files = new ArrayList<>();
    Files.newDirectoryStream(Paths.get(folder, "data")).forEach(files::add);
    files.sort((a, b) -> getNameWithoutExt(a).compareTo(getNameWithoutExt(b)));

    for (Path exampleFile : files) {
      List<String> lines = Files.readAllLines(exampleFile);
      TimeSeries[] mts = new TimeSeries[lines.size()];
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        String[] split = line.trim().split(",");
        mts[i] = getTimeSeries(0, split);
      }
      input.add(new MultivariateTimeSeries(mts));
    }
    return new Pair<>(input, output);
  }

  private static Integer getNameWithoutExt(Path a) {
    String name = a.getFileName().toString();
    return Integer.parseInt(name.split("\\.")[0]);
  }

}
