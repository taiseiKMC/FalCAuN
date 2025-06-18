package net.maswag.falcaun;

import de.learnlib.exception.SULException;
import de.learnlib.sul.SUL;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;
import jep.JepException;
import jep.NDArray;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
public class PythonContinuousNumericSUL implements ContinuousNumericSUL, Closeable {
    /**
     * The signal step of the input signal.
     */
    protected final Double signalStep;

    /**
     * Use rawtypes because classobject does not support generic type
     */
    @SuppressWarnings("rawtypes")
    protected final PythonModel<List<Double>, NDArray> model;
    protected Signal inputSignal = null;
    protected final TimeMeasure simulationTime = new TimeMeasure();

    @Getter
    private int counter = 0;

    /**
     * @param initScript The Python script to initialize the model. It defines a
     *                   class SUL with methods pre(), post(), step(I inputSignal)
     *                   -> O, and close().
     * @throws JepException If there is an error initializing the Python interpreter
     *                      or running the script.
     */
    @SuppressWarnings("rawtypes")
    public PythonContinuousNumericSUL(String initScript, Double signalStep)
            throws InterruptedException, ExecutionException {
        this.model = new PythonModel<List<Double>, NDArray>(initScript, NDArray.class);
        this.signalStep = signalStep;
    }

    /**
     * The current time of the simulation
     */
    public double getCurrentTime() {
        return inputSignal.duration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canFork() {
        return false;
    }

    /**
     * Clear the counter and the time measure.
     */
    @Override
    public void clear() {
        simulationTime.reset();
        counter = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pre() {
        inputSignal = new Signal(signalStep);
        this.model.pre();
        counter++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void post() {
        this.model.post();
    }

    public ValueWithTime<List<Double>> constructValueWithTime(@SuppressWarnings("rawtypes") NDArray ndArray) {
        var dimension = ndArray.getDimensions();
        assert dimension[1] == this.inputSignal.get(0).size() + 1; // +1 for the timestamp
        var length = dimension[0];
        var length2 = dimension[1];
        var obj = ndArray.getData();

        if (obj instanceof double[] data) {
            var timestamps = new ArrayList<Double>();
            var result = new ArrayList<List<Double>>();
            for (int i = 0; i < length; i++) {
                timestamps.add(data[i * length2]);
                var output = new ArrayList<Double>();
                for (int j = 1; j < length2; j++) {
                    output.add(data[i * length2 + j]);
                }
                result.add(output);
            }
            return new ValueWithTime<List<Double>>(timestamps, result);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + obj.getClass().getName());
        }
    }

    /**
     * Make one step on the SUL in python.
     * step function in python must return a 2D numpy array
     * which first column is the timestamp and the rest columns are the output values.
     *
     * @param inputSignal The input signal to the SUL
     * @return output of SUL
     *
     * @throws SULException
     *         if the input symbol cannot be executed on the SUL
     */
    @Nullable
    @Override
    public ExtendedIOSignalPiece<List<Double>> step(@Nullable List<Double> inputSignal) {
        if (inputSignal == null) {
            return null;
        }
        this.inputSignal.add(inputSignal);

        simulationTime.start();
        var ret = this.model.step(inputSignal);
        simulationTime.stop();

        var values = constructValueWithTime(ret);
        double endTime = getCurrentTime();
        return new ExtendedIOSignalPiece<>(inputSignal, values, endTime - this.signalStep, endTime);
    }

    /**
     * Run all steps of the python model by feeding inputSignal
     *
     * @param inputSignal The input signal
     * @return The output signal. The size is same as the input.
     */
    @Override
    public IOContinuousSignal<List<Double>> execute(Word<List<Double>> inputSignal)
            throws InterruptedException, ExecutionException {
        pre();

        @SuppressWarnings("rawtypes")
        NDArray ret = null;

        for (var e : inputSignal) {
            this.inputSignal.add(e);

            simulationTime.start();
            ret = this.model.step(e);
            simulationTime.stop();
        }

        var values = constructValueWithTime(ret);

        WordBuilder<List<Double>> builder = new WordBuilder<>();
        for (int i = 0; i < inputSignal.size(); i++) {
            builder.add(values.at(i * this.signalStep));
        }
        return new IOContinuousSignal<>(inputSignal, builder.toWord(), values, this.signalStep);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public SUL<List<Double>, IOSignalPiece<List<Double>>> fork() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        this.model.close();
    }

    /**
     * {@inheritDoc}
     */
    public double getSimulationTimeSecond() {
        return this.simulationTime.getSecond();
    }

}
