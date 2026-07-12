package com.flashdeal.riskguard.train;

import com.flashdeal.riskguard.model.DecisionTreeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 训练入口：切分数据集、调用 fit、评估、落盘 model 文件
 */
public class ModelTrainer {

    private static final Logger log = LoggerFactory.getLogger(ModelTrainer.class);

    public static void main(String[] args) throws Exception {
        // 1. 生成模拟数据
        BehaviorSimulator simulator = new BehaviorSimulator(42);
        BehaviorSimulator.Result data = simulator.generate(3800, 200);

        // 2. 8:2 分层切分
        int totalNormal = 3800, totalBot = 200;
        int trainNormal = (int) (totalNormal * 0.8);
        int trainBot = (int) (totalBot * 0.8);
        int trainSize = trainNormal + trainBot;
        int testSize = data.features.length - trainSize;

        double[][] trainX = new double[trainSize][6];
        int[] trainY = new int[trainSize];
        double[][] testX = new double[testSize][6];
        int[] testY = new int[testSize];

        // 按标签分组索引
        int normalIdx = 0, botIdx = 0;
        int trainIdx = 0, testIdx = 0;
        int normalCount = 0, botCount = 0;

        for (int i = 0; i < data.features.length; i++) {
            boolean isNormal = data.labels[i] == 0;
            if (isNormal) {
                normalCount++;
                if (normalCount <= trainNormal) {
                    trainX[trainIdx] = data.features[i];
                    trainY[trainIdx] = data.labels[i];
                    trainIdx++;
                } else {
                    testX[testIdx] = data.features[i];
                    testY[testIdx] = data.labels[i];
                    testIdx++;
                }
            } else {
                botCount++;
                if (botCount <= trainBot) {
                    trainX[trainIdx] = data.features[i];
                    trainY[trainIdx] = data.labels[i];
                    trainIdx++;
                } else {
                    testX[testIdx] = data.features[i];
                    testY[testIdx] = data.labels[i];
                    testIdx++;
                }
            }
        }

        log.info("训练集: {} 条, 验证集: {} 条", trainSize, testSize);

        // 3. 训练
        DecisionTreeModel model = new DecisionTreeModel();
        model.train(trainX, trainY, 5, 10);

        // 4. 评估
        int truePositive = 0, falsePositive = 0, trueNegative = 0, falseNegative = 0;
        for (int i = 0; i < testSize; i++) {
            int predicted = model.predict(testX[i]);
            boolean actualBot = testY[i] == 2;
            boolean predictedBot = predicted == 2;

            if (actualBot && predictedBot) truePositive++;
            else if (!actualBot && predictedBot) falsePositive++;
            else if (!actualBot && !predictedBot) trueNegative++;
            else falseNegative++;
        }

        double accuracy = (double) (truePositive + trueNegative) / testSize;
        double recall = truePositive + falseNegative > 0
                ? (double) truePositive / (truePositive + falseNegative) : 0;
        double fpr = trueNegative + falsePositive > 0
                ? (double) falsePositive / (trueNegative + falsePositive) : 0;

        log.info("准确率: {}", String.format("%.4f", accuracy));
        log.info("召回率(拦截率): {}", String.format("%.4f", recall));
        log.info("误杀率: {}", String.format("%.4f", fpr));

        // 5. 落盘
        String outputPath = "risk-guard/src/main/resources/model.bin";
        File outFile = new File(outputPath);
        outFile.getParentFile().mkdirs();
        model.save(outputPath);
        log.info("模型已保存到 {}", outputPath);
    }
}
