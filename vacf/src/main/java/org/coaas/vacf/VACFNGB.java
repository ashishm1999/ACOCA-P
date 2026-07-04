package org.coaas.vacf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * VACF-NGB decision engine.
 *
 * Natural Gradient Boosting for the retention decision. This is a
 * lightweight from-scratch implementation of NGBoost's core idea:
 *
 *   - Boost a probabilistic prediction (Bernoulli) rather than a point
 *     estimate. Each round fits a small regression tree to the natural
 *     gradient of the negative log-likelihood.
 *   - The natural gradient rescales the ordinary gradient by the
 *     inverse Fisher information matrix, which for Bernoulli reduces
 *     to a scalar multiplier — so NGBoost's implementation collapses
 *     to a well-conditioned gradient boost under this parameterisation.
 *
 * Feature vector (thesis §8):
 *
 *   x = (CVI, CF, PoA, hop_cost, provider_reputation, time_of_day_sin, time_of_day_cos)
 *
 * Chapter 8 reports NGBoost inference at sub-millisecond per item on the
 * i7 controller with 100 trees.
 */
public final class VACFNGB {

    private static final int NUM_FEATURES = 7;
    private static final int DEFAULT_TREES = 100;
    private static final int MAX_DEPTH = 4;
    private static final double LEARNING_RATE = 0.1;

    private final List<RegressionTree> trees = new ArrayList<>();
    private double bias;

    public VACFNGB() {
        this.bias = 0.0;   // logit(0.5)
    }

    /** Predict the retention probability for a single feature vector. */
    public double predict(double[] features) {
        checkShape(features);
        double logit = bias;
        for (RegressionTree t : trees) {
            logit += LEARNING_RATE * t.predict(features);
        }
        return sigmoid(logit);
    }

    /**
     * Fit the ensemble against (x_i, y_i) training pairs.
     *
     * y_i ∈ {0, 1} — 1 means the item was worth retaining.
     */
    public void fit(double[][] x, int[] y) {
        fit(x, y, DEFAULT_TREES, 0xC0FFEEL);
    }

    public void fit(double[][] x, int[] y, int nTrees, long seed) {
        if (x.length != y.length) throw new IllegalArgumentException("x/y length mismatch");
        if (x.length == 0) return;
        // Initialise bias to logit of the mean.
        double mean = 0.0;
        for (int yi : y) mean += yi;
        mean /= y.length;
        this.bias = Math.log((mean + 1e-6) / (1.0 - mean + 1e-6));

        Random rng = new Random(seed);
        double[] logits = new double[x.length];
        Arrays.fill(logits, this.bias);

        for (int t = 0; t < nTrees; t++) {
            double[] grad = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                double p = sigmoid(logits[i]);
                grad[i] = p - y[i];   // ∂ NLL / ∂ logit = p - y
            }
            RegressionTree tree = RegressionTree.fit(x, grad, MAX_DEPTH, rng);
            trees.add(tree);
            for (int i = 0; i < x.length; i++) {
                logits[i] -= LEARNING_RATE * tree.predict(x[i]);
            }
        }
    }

    private void checkShape(double[] features) {
        if (features.length != NUM_FEATURES) {
            throw new IllegalArgumentException(
                "expected " + NUM_FEATURES + " features, got " + features.length);
        }
    }

    private static double sigmoid(double z) {
        if (z >= 0) {
            double e = Math.exp(-z);
            return 1.0 / (1.0 + e);
        } else {
            double e = Math.exp(z);
            return e / (1.0 + e);
        }
    }

    // ---------------------------------------------------------------------
    // Small binary regression tree — fitted to residuals, splits on best
    // feature × threshold by variance reduction.
    // ---------------------------------------------------------------------
    static final class RegressionTree {
        int featureIdx = -1;
        double threshold = Double.NaN;
        double value = 0.0;
        RegressionTree left, right;

        double predict(double[] x) {
            if (featureIdx < 0) return value;
            return x[featureIdx] <= threshold ? left.predict(x) : right.predict(x);
        }

        static RegressionTree fit(double[][] x, double[] target, int maxDepth, Random rng) {
            int[] rows = new int[x.length];
            for (int i = 0; i < rows.length; i++) rows[i] = i;
            RegressionTree root = new RegressionTree();
            fitRecursive(root, x, target, rows, maxDepth, rng);
            return root;
        }

        static void fitRecursive(RegressionTree node, double[][] x, double[] target,
                                 int[] rows, int depth, Random rng) {
            double mean = mean(target, rows);
            if (depth == 0 || rows.length < 4) {
                node.value = mean;
                return;
            }
            double bestGain = 0.0;
            int bestFeature = -1;
            double bestThreshold = Double.NaN;
            int[] bestLeftRows = null;
            int[] bestRightRows = null;
            double parentSS = ss(target, rows, mean);

            // Try a random subset of thresholds per feature to keep training fast.
            for (int f = 0; f < x[0].length; f++) {
                double[] samples = sampleValues(x, rows, f, 8, rng);
                for (double thr : samples) {
                    int[] leftRows = filter(x, rows, f, thr, true);
                    int[] rightRows = filter(x, rows, f, thr, false);
                    if (leftRows.length == 0 || rightRows.length == 0) continue;
                    double leftMean = mean(target, leftRows);
                    double rightMean = mean(target, rightRows);
                    double gain = parentSS - ss(target, leftRows, leftMean) - ss(target, rightRows, rightMean);
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestFeature = f;
                        bestThreshold = thr;
                        bestLeftRows = leftRows;
                        bestRightRows = rightRows;
                    }
                }
            }
            if (bestFeature < 0) {
                node.value = mean;
                return;
            }
            node.featureIdx = bestFeature;
            node.threshold = bestThreshold;
            node.left = new RegressionTree();
            node.right = new RegressionTree();
            fitRecursive(node.left, x, target, bestLeftRows, depth - 1, rng);
            fitRecursive(node.right, x, target, bestRightRows, depth - 1, rng);
        }

        static double mean(double[] arr, int[] rows) {
            double s = 0.0;
            for (int i : rows) s += arr[i];
            return rows.length == 0 ? 0.0 : s / rows.length;
        }

        static double ss(double[] arr, int[] rows, double mean) {
            double s = 0.0;
            for (int i : rows) {
                double d = arr[i] - mean;
                s += d * d;
            }
            return s;
        }

        static int[] filter(double[][] x, int[] rows, int feature, double threshold, boolean left) {
            int count = 0;
            for (int i : rows) if ((x[i][feature] <= threshold) == left) count++;
            int[] out = new int[count];
            int j = 0;
            for (int i : rows) if ((x[i][feature] <= threshold) == left) out[j++] = i;
            return out;
        }

        static double[] sampleValues(double[][] x, int[] rows, int feature, int n, Random rng) {
            double[] out = new double[Math.min(n, rows.length)];
            for (int i = 0; i < out.length; i++) {
                out[i] = x[rows[rng.nextInt(rows.length)]][feature];
            }
            return out;
        }
    }
}
