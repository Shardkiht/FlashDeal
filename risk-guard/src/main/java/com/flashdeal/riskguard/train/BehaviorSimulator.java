package com.flashdeal.riskguard.train;

import java.util.Random;

/**
 * 模拟训练数据生成器（离线用，不参与线上运行）
 */
public class BehaviorSimulator {

    private final Random random;

    public BehaviorSimulator() {
        this.random = new Random(42);
    }

    public BehaviorSimulator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * 生成模拟数据
     * @param normalCount 正常用户数量
     * @param botCount 羊毛党数量
     * @return [0]=features double[][], [1]=labels int[]（通过 Result 封装）
     */
    public Result generate(int normalCount, int botCount) {
        int total = normalCount + botCount;
        double[][] features = new double[total][6];
        int[] labels = new int[total];

        int idx = 0;
        for (int i = 0; i < normalCount; i++, idx++) {
            features[idx] = generateNormal();
            labels[idx] = 0;
        }
        for (int i = 0; i < botCount; i++, idx++) {
            features[idx] = generateBot();
            labels[idx] = 2;
        }

        // 打乱顺序
        for (int i = total - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            double[] tmpF = features[i]; features[i] = features[j]; features[j] = tmpF;
            int tmpL = labels[i]; labels[i] = labels[j]; labels[j] = tmpL;
        }

        return new Result(features, labels);
    }

    private double[] generateNormal() {
        return new double[]{
                0.3 + random.nextDouble() * 2.7,       // qpsPerIp: 0.3~3
                random.nextDouble() * 0.3,              // ipSimilarity: 0~0.3
                30 + random.nextInt(970),               // accountAgeDays: 30~1000
                random.nextInt(51),                     // orderHistory: 0~50
                200 + random.nextDouble() * 1800,       // clickIntervalStdMs: 200~2000
                random.nextDouble() < 0.05 ? 1 : 0     // isEmulator: 5% 概率 1
        };
    }

    private double[] generateBot() {
        return new double[]{
                30 + random.nextDouble() * 170,         // qpsPerIp: 30~200
                0.7 + random.nextDouble() * 0.3,        // ipSimilarity: 0.7~1.0
                random.nextInt(8),                      // accountAgeDays: 0~7
                0,                                      // orderHistory: 0
                random.nextDouble() * 50,               // clickIntervalStdMs: 0~50
                random.nextDouble() < 0.7 ? 1 : 0      // isEmulator: 70% 概率 1
        };
    }

    public static class Result {
        public final double[][] features;
        public final int[] labels;

        public Result(double[][] features, int[] labels) {
            this.features = features;
            this.labels = labels;
        }
    }
}
