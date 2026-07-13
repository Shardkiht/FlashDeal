package com.flashdeal.riskguard.model.impl;

import com.flashdeal.riskguard.model.RiskModel;
import smile.base.cart.SplitRule;
import smile.classification.DecisionTree;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.data.type.StructField;
import smile.data.type.DataTypes;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;

import java.io.*;

/**
 * 决策树模型实现，封装 Smile 的 DecisionTree
 */
public class DecisionTreeModel implements RiskModel {

    private DecisionTree tree;
    private StructType fullSchema;

    public static final String[] FEATURE_NAMES = {
            "qpsPerIp", "ipSimilarity", "accountAgeDays",
            "orderHistory", "clickIntervalStdMs", "isEmulator"
    };

    /**
     * 训练模型
     * @param x 特征矩阵
     * @param y 标签数组，0=正常，2=羊毛党
     * @param maxDepth 树最大深度
     * @param minSamplesLeaf 叶子节点最小样本数
     */
    public void train(double[][] x, int[] y, int maxDepth, int minSamplesLeaf) {
        // 构建混合类型 DataFrame：DoubleVector 特征列 + IntVector label 列
        BaseVector<?, ?, ?>[] vectors = new BaseVector[FEATURE_NAMES.length + 1];
        for (int col = 0; col < FEATURE_NAMES.length; col++) {
            double[] colData = new double[x.length];
            for (int row = 0; row < x.length; row++) {
                colData[row] = x[row][col];
            }
            vectors[col] = DoubleVector.of(FEATURE_NAMES[col], colData);
        }
        vectors[FEATURE_NAMES.length] = IntVector.of("label", y);
        DataFrame df = DataFrame.of(vectors);

        Formula formula = Formula.lhs("label");
        this.tree = DecisionTree.fit(formula, df, SplitRule.GINI, maxDepth, Integer.MAX_VALUE, minSamplesLeaf);
        this.fullSchema = buildFullSchema();
    }

    @Override
    public int predict(double[] features) {
        Tuple tuple = Tuple.of(appendDummy(features), fullSchema);
        return tree.predict(tuple);
    }

    @Override
    public double[] predictProba(double[] features) {
        double[] posteriori = new double[3];
        Tuple tuple = Tuple.of(appendDummy(features), fullSchema);
        tree.predict(tuple, posteriori);
        return posteriori;
    }

    /**
     * 构建完整 schema（含 label 占位列），Smile Formula.lhs("label") 要求 predict 时 schema 也包含 label 字段
     */
    private StructType buildFullSchema() {
        StructField[] fields = new StructField[FEATURE_NAMES.length + 1];
        for (int i = 0; i < FEATURE_NAMES.length; i++) {
            fields[i] = new StructField(FEATURE_NAMES[i], DataTypes.DoubleType);
        }
        fields[FEATURE_NAMES.length] = new StructField("label", DataTypes.IntegerType);
        return new StructType(fields);
    }

    /** 在特征数组末尾补一个 dummy int 值（对应 label 占位） */
    private double[] appendDummy(double[] features) {
        double[] full = new double[features.length + 1];
        System.arraycopy(features, 0, full, 0, features.length);
        full[features.length] = 0;
        return full;
    }

    /** 模型持久化 */
    public void save(String path) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(this.tree);
        }
    }

    public static DecisionTreeModel load(String path) throws IOException, ClassNotFoundException {
        DecisionTreeModel model = new DecisionTreeModel();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            model.tree = (DecisionTree) ois.readObject();
        }
        model.fullSchema = model.buildFullSchema();
        return model;
    }
}
