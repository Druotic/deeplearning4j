package org.deeplearning4j.spark.impl.multilayer;

import java.util.Random;
import java.util.regex.Pattern;

import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;

import org.apache.spark.api.java.function.Function2;
import org.deeplearning4j.scaleout.api.ir.ParameterVectorUpdateable;
import org.deeplearning4j.spark.impl.multilayer.PrototypeSparkJob.DataPoint;
import org.deeplearning4j.spark.impl.multilayer.PrototypeSparkJob.ParsePoint;

import org.nd4j.linalg.api.ndarray.INDArray;



/**
 * This is the prototype DL4J Worker
 *
 * TODO
 * -	figure out how to get hdfs record readers rolling
 * -	separate out worker and master areas of code
 * -	replace ParsePoint with the SVMLight reader
 * -	replace DataPoint with the Pair<INDArray, INDArray>
 *
 *
 * @author josh
 *
 */
public class SparkWorker {

	private static ParameterVectorUpdateable masterParameterVector = null;

	private static final int D = 10;   // Number of dimensions
	private static final Random rand = new Random(42);

	/**
	 * This is the code run at the "Master" in IterativeReduce parlance
	 *
	 */
	static class MasterComputeParameterAverage implements Function2<ParameterVectorUpdateable[], ParameterVectorUpdateable,ParameterVectorUpdateable> {


		@Override
		public ParameterVectorUpdateable call(ParameterVectorUpdateable[] parameterVectorUpdateables, ParameterVectorUpdateable parameterVectorUpdateable) throws Exception {
			return null;
		}
	}


	/**
	 * TODO: figure out what this class's signature needs to look like
	 * - usage in code below:
	 *
	 * 	    JavaRDD<String> lines = sc.textFile(args[0]);

	 JavaRDD<DataPoint> points = lines.map(new ParsePoint()).cache();

	 *
	 * @author josh
	 *
	 */
	static class ParseSVMLightLine implements Function2<String, INDArray, INDArray> {
		private static final Pattern SPACE = Pattern.compile(" ");
		private static final RecordReader recordParser = null;


		@Override
		public INDArray call(String s, INDArray indArray) throws Exception {
			return null;
		}
	}


	/**
	 * This is considered the "Worker"
	 * This is the code that will run the .fit() method on the network
	 * @author josh
	 *
	 */
	static class Worker implements Function<DataPoint, double[]> {

		private final double[] weights;

		Worker(double[] weights) {
			this.weights = weights;
		}

		@Override
		public double[] call(DataPoint p) {
			double[] gradient = new double[D];
	      
	      /*
	      for (int i = 0; i < D; i++) {
	        double dot = dot(weights, p.x);
	        gradient[i] = (1 / (1 + Math.exp(-p.y * dot)) - 1) * p.y * p.x[i];
	      }
	      */



			return gradient;
		}
	}


	/**
	 * This is the main driver that kicks off the program
	 *
	 * @param args
	 */
	public static void main(String[] args) {

		if (args.length < 2) {
			System.err.println("Usage: JavaHdfsLR <file> <iters>");
			System.exit(1);
		}


		SparkConf sparkConf = new SparkConf().setAppName("DL4J");
		JavaSparkContext sc = new JavaSparkContext(sparkConf);
		JavaRDD<String> lines = sc.textFile(args[0]);
		JavaRDD<DataPoint> points = lines.map(new ParsePoint()).cache();
		int ITERATIONS = Integer.parseInt(args[1]);
		// Initialize w to a random value
		double[] w = new double[D];
/*
	    for (int i = 0; i < D; i++) {
	      w[i] = 2 * rand.nextDouble() - 1;
	    }

	    System.out.print("Initial w: ");
	    printWeights(w);
*/
		for (int i = 1; i <= ITERATIONS; i++) {

			System.out.println("On iteration " + i);

		/*	double[] gradient = points.map(
					new Worker(w)
			).reduce(new MasterComputeParameterAverage());

			for (int j = 0; j < D; j++) {
				w[j] -= gradient[j];
			}
*/
		}

		//System.out.print("Final w: ");
		//printWeights(w);
		sc.stop();
	}

}