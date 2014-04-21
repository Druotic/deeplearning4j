package org.deeplearning4j.rbm;

import static org.deeplearning4j.util.MatrixUtil.*;


import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.BaseNeuralNetwork;
import org.deeplearning4j.nn.Tensor;
import org.deeplearning4j.nn.gradient.NeuralNetworkGradient;
import org.deeplearning4j.util.Convolution;
import org.deeplearning4j.util.MatrixUtil;
import org.jblas.DoubleMatrix;
import org.jblas.ranges.RangeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ConvolutionalRBM extends RBM  {

    /**
     *
     */
    private static final long serialVersionUID = 6868729665328916878L;
    private int numFilters = 4;
    //top down signal from hidden feature maps to visibles
    private Tensor visI;
    //bottom up signal from visibles to hiddens
    private Tensor hidI;
    private Tensor W;
    private int[] stride = {2,2};
    private static Logger log = LoggerFactory.getLogger(ConvolutionalRBM.class);
    protected boolean convolutionInitCalled = false;

    protected ConvolutionalRBM() {
        convolutionInit();
    }




    protected ConvolutionalRBM(DoubleMatrix input, int nVisible, int n_hidden, DoubleMatrix W,
                               DoubleMatrix hbias, DoubleMatrix vBias, RandomGenerator rng,double fanIn,RealDistribution dist) {
        super(input, nVisible, n_hidden, W, hbias, vBias, rng,fanIn,dist);
        convolutionInit();
    }




    private void convolutionInit() {
       if(convolutionInitCalled)
           return;
        W = new Tensor(getnVisible(),getnHidden(),numFilters);
        visI = Tensor.zeros(getnVisible(),getnHidden(),numFilters);
        int visibleNumFilters = getnVisible() - numFilters + 1;
        int hiddenNumFilters = getnHidden() - numFilters + 1;

        if(visibleNumFilters < 1 || hiddenNumFilters < 1)
            throw new IllegalArgumentException("Invalid hidden filter size shape: (" + visibleNumFilters + "," + hiddenNumFilters + ")");

        hidI = Tensor.zeros(visibleNumFilters,hiddenNumFilters,numFilters);
        convolutionInitCalled = true;


    }



    /**
     * Calculates the activation of the visible :
     * sigmoid(v * W + hbias)
     *
     * @param v the visible layer
     * @return the approximated activations of the visible layer
     */
    @Override
    public DoubleMatrix propUp(DoubleMatrix v) {
        for(int i = 0; i < numFilters; i++) {
            DoubleMatrix reversedSlice =  MatrixUtil.reverse(W.getSlice(i));
            DoubleMatrix slice =  MatrixUtil.padWithZeros(Convolution.conv2d(v, reversedSlice, Convolution.Type.VALID).add(hBias.get(i)), hidI.rows(), hidI.columns());
            hidI.setSlice(i,slice);

        }

        Tensor expHidI = hidI.exp();

        DoubleMatrix eHid = expHidI.div(pool(expHidI));
        return eHid;
    }

    /**
     * Calculates the activation of the hidden:
     * sigmoid(h * W + vbias)
     *
     * @param h the hidden layer
     * @return the approximated output of the hidden layer
     */
    @Override
    public DoubleMatrix propDown(DoubleMatrix h) {
        Tensor h1 = (Tensor) h;
        for(int i = 0; i < numFilters; i++) {
            visI.setSlice(i,
                    Convolution.conv2d(h1.getSlice(i), W.getSlice(i), Convolution.Type.FULL));
        }

        DoubleMatrix I = visI.sliceElementSums().addRowVector(vBias);

        return sigmoid(I);
    }





    public Tensor pool(Tensor input) {
        int nCols = input.columns();
        int rows = input.rows();
        Tensor ret = Tensor.zeros(rows,nCols,input.slices());
        for(int i = 0;i < Math.ceil(nCols / stride[0]); i++) {
            int rowsMin = (i - 1) * stride[0] + 1;
            int rowsMax = i * stride[0];
            for(int j = 0; j < Math.ceil(nCols / stride[1]); j++) {
                int cols = (j - 1) * stride[1] + 1;
                int colsMax = j * stride[1];
                Tensor blockVal =  null;
                int rowLength = rowsMax - rowsMin;
                int colLength = colsMax - cols;

                Tensor set = blockVal.permute(new int[]{2,3,1}).repmat(rowLength,colLength);
                ret.put(RangeUtils.interval(rowsMin, rowsMax), RangeUtils.interval(cols, colsMax), set);



            }

        }
        return ret;
    }



    @Override
    public NeuralNetworkGradient getGradient(Object[] params) {
        int k = (int) params[0];
        double learningRate = (double) params[1];


        if(wAdaGrad != null)
            wAdaGrad.setMasterStepSize(learningRate);
        if(hBiasAdaGrad != null )
            hBiasAdaGrad.setMasterStepSize(learningRate);
        if(vBiasAdaGrad != null)
            vBiasAdaGrad.setMasterStepSize(learningRate);

		/*
		 * Cost and updates dictionary.
		 * This is the update rules for weights and biases
		 */
        Pair<DoubleMatrix,DoubleMatrix> probHidden = sampleHiddenGivenVisible(input);
		/*
		 * Start the gibbs sampling.
		 */
        DoubleMatrix chainStart = probHidden.getSecond();

		/*
		 * Note that at a later date, we can explore alternative methods of
		 * storing the chain transitions for different kinds of sampling
		 * and exploring the search space.
		 */
        Pair<Pair<DoubleMatrix,DoubleMatrix>,Pair<DoubleMatrix,DoubleMatrix>> matrices = null;
        //negative visible means or expected values
        DoubleMatrix nvMeans = null;
        //negative value samples
        DoubleMatrix nvSamples = null;
        //negative hidden means or expected values
        DoubleMatrix nhMeans = null;
        //negative hidden samples
        DoubleMatrix nhSamples = null;

		/*
		 * K steps of gibbs sampling. This is the positive phase of contrastive divergence.
		 *
		 * There are 4 matrices being computed for each gibbs sampling.
		 * The samples from both the positive and negative phases and their expected values
		 * or averages.
		 *
		 */

        for(int i = 0; i < k; i++) {


            if(i == 0)
                matrices = gibbhVh(chainStart);
            else
                matrices = gibbhVh(nhSamples);

            //get the cost updates for sampling in the chain after k iterations
            nvMeans = matrices.getFirst().getFirst();
            nvSamples = matrices.getFirst().getSecond();
            nhMeans = matrices.getSecond().getFirst();
            nhSamples = matrices.getSecond().getSecond();
        }

		/*
		 * Update gradient parameters
		 */

        Tensor eHiddenInitial = (Tensor) probHidden.getSecond();
        Tensor hiddenMeans = (Tensor) nhMeans;
        Tensor wGradient = new Tensor(W.rows(),W.columns(),W.slices());
        for(int i = 0; i < numFilters; i++) {
            wGradient.setSlice(i,Convolution.conv2d(input,
                    eHiddenInitial.getSlice(i), Convolution.Type.VALID)
                    .sub(Convolution.conv2d(nvSamples,
                            MatrixUtil.reverse(hiddenMeans.getSlice(i)),
                            Convolution.Type.VALID)));
        }




        DoubleMatrix hBiasGradient = null;

        //update rule: the expected values of the hidden input - the negative hidden  means adjusted by the learning rate
        hBiasGradient = DoubleMatrix.scalar(probHidden.getSecond().sub(nhMeans).columnSums().sum());




        //update rule: the expected values of the input - the negative samples adjusted by the learning rate
        DoubleMatrix  vBiasGradient = DoubleMatrix.scalar((input.sub(nvSamples)).columnSums().sum());
        NeuralNetworkGradient ret = new NeuralNetworkGradient(wGradient, vBiasGradient, hBiasGradient);

        updateGradientAccordingToParams(ret, learningRate);
        triggerGradientEvents(ret);

        return ret;
    }


    public static class Builder extends BaseNeuralNetwork.Builder<ConvolutionalRBM> {

        protected int numFilters;
        protected int[] stride;




        public Builder() {
            this.clazz = ConvolutionalRBM.class;

        }


        public Builder withStride(int[] stride) {
            this.stride = stride;
            return this;
        }

        public Builder withNumFilters(int numFilters) {
            this.numFilters = numFilters;
            return this;
        }



        public ConvolutionalRBM build() {
            ConvolutionalRBM ret = (ConvolutionalRBM) super.build();
            ret.numFilters = numFilters;
            ret.stride = stride;
            ret.convolutionInit();
            return ret;

        }



    }


}