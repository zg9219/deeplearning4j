package org.deeplearning4j.nn.conf.constraint;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Broadcast;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.BooleanIndexing;
import org.nd4j.linalg.indexing.conditions.Conditions;

import java.util.Collections;
import java.util.Set;

/**
 * Constrain the minimum AND maximum L2 norm of the incoming weights for each unit to be between the specified values.
 * If the L2 norm exceeds the specified max value, the weights will be scaled down to satisfy the constraint; if the
 * L2 norm is less than the specified min value, the weights will be scaled up<br>
 * Note that this constraint supports a rate parameter (default: 1.0, which is equivalent to a strict constraint).
 * If rate < 1.0, the applied norm2 constraint will be (1-rate)*norm2 + rate*clippedNorm2, where clippedNorm2 is the
 * norm2 value after applying clipping to min/max values.
 *
 * @author Alex Black
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MinMaxNormConstraint extends BaseConstraint {
    public static final double DEFAULT_RATE = 1.0;

    private double min;
    private double max;
    private double rate;

    private MinMaxNormConstraint(){
        //No arg for json ser/de
    }

    /**
     * Apply to weights but not biases by default
     *
     * @param max            Maximum L2 value
     * @param min            Minimum L2 value
     * @param dimensions     Dimensions to apply to. For DenseLayer, OutputLayer, RnnOutputLayer, LSTM, etc: this should
     *                       be dimension 1. For CNNs, this should be dimensions [1,2,3] corresponding to last 3 of
     *                       parameters which have order [depthOut, depthIn, kH, kW]
     */
    public MinMaxNormConstraint(double min, double max, int... dimensions){
        this(min, max, DEFAULT_RATE, null, dimensions);
    }

    /**
     * Apply to weights but not biases by default
     *
     * @param max            Maximum L2 value
     * @param min            Minimum L2 value
     * @param rate           Constraint rate
     * @param dimensions     Dimensions to apply to. For DenseLayer, OutputLayer, RnnOutputLayer, LSTM, etc: this should
     *                       be dimension 1. For CNNs, this should be dimensions [1,2,3] corresponding to last 3 of
     *                       parameters which have order [depthOut, depthIn, kH, kW]
     */
    public MinMaxNormConstraint(double min, double max, double rate, int... dimensions){
        this(min, max, rate, Collections.<String>emptySet(), dimensions);
    }

    /**
     *
     * @param max            Maximum L2 value
     * @param min            Minimum L2 value
     * @param rate           Constraint rate
     * @param paramNames     Which parameter names to apply constraint to
     * @param dimensions     Dimensions to apply to. For DenseLayer, OutputLayer, RnnOutputLayer, LSTM, etc: this should
     *                       be dimension 1. For CNNs, this should be dimensions [1,2,3] corresponding to last 3 of
     *                       parameters which have order [depthOut, depthIn, kH, kW]
     */
    public MinMaxNormConstraint(double min, double max, double rate, Set<String> paramNames, int... dimensions){
        super(paramNames, dimensions);
        if(rate <= 0 || rate > 1.0){
            throw new IllegalStateException("Invalid rate: must be in interval (0,1]: got " + rate);
        }
        this.min = min;
        this.max = max;
        this.rate = rate;
    }

    @Override
    public void apply(INDArray param) {
        INDArray norm = param.norm2(dimensions);
        INDArray clipped = norm.unsafeDuplication();
        CustomOp op = DynamicCustomOp.builder("clipbyvalue")
                .setInputs(clipped)
                .callInplace(true)
                .setFloatingPointArguments(min, max)
                .build();
        Nd4j.getExecutioner().exec(op);

        norm.addi(epsilon);
        clipped.divi(norm);

        if(rate != 1.0){
            clipped.muli(rate).addi(norm.muli(1.0-rate));
        }

        Broadcast.mul(param, clipped, param, getBroadcastDims(dimensions, param.rank()) );
    }

    @Override
    public MinMaxNormConstraint clone() {
        return new MinMaxNormConstraint(min, max, rate, params, dimensions);
    }
}
