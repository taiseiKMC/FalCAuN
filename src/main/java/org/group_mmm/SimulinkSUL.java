package org.group_mmm;

import com.mathworks.engine.MatlabEngine;
import de.learnlib.api.SUL;
import de.learnlib.api.exception.SULException;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * The System Under Learning implemented by a Simulink. We use the fixed step execution of Simulink to make sampling easier.
 */
class SimulinkSUL implements SUL<List<Double>, List<Double>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulinkSUL.class);
    private final Double signalStep;
    /**
     * The simulation step of Simulink.
     * <p>
     * If this value is too large, Simulink can abort due to an computation error. In that case, you should make this value larger.
     */
    private double simulinkSimulationStep = 0.0025;
    private MatlabEngine matlab;
    private List<String> paramNames;
    private Double endTime = 0.0;
    private List<List<Double>> previousInput;
    private boolean isInitial = true;
    private boolean useFastRestart = true;

    /**
     * Setter of simulinkSimulationStep
     *
     * @param simulinkSimulationStep The fixed simulation step of Simulink. If this value is too large, Simulink can abort due to an computation error.
     */
    public void setSimulationStep(double simulinkSimulationStep) {
        this.simulinkSimulationStep = simulinkSimulationStep;
    }

    SimulinkSUL(String initScript, List<String> paramNames, Double signalStep) throws InterruptedException, ExecutionException {
        // Load System here
        this.paramNames = paramNames;
        this.signalStep = signalStep;
        String[] engines = MatlabEngine.findMatlab();
        if (engines.length == 0) {
            matlab = MatlabEngine.startMatlab();
        } else {
            matlab = MatlabEngine.connectMatlab();
        }

        matlab.eval("clear;");
        matlab.eval("warning('off', 'Simulink:LoadSave:EncodingMismatch')");
        matlab.putVariable("signalStep", signalStep);

        matlab.eval(initScript);
    }

    static private void appendSignalStep(List<List<Double>> previousInput, List<Double> signalStep) {
        for (int i = 0; i < signalStep.size(); i++) {
            if (previousInput.size() <= i) {
                previousInput.add(new ArrayList<>());
                assert previousInput.size() == i + 1;
                previousInput.get(i).add(signalStep.get(i));
                previousInput.get(i).add(signalStep.get(i));
            } else {
                previousInput.get(i).add(signalStep.get(i));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canFork() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pre() {
        endTime = 0.0;
        previousInput = new ArrayList<>();
        isInitial = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void post() {
        previousInput.clear();
        endTime = 0.0;
        isInitial = true;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public List<Double> step(@Nullable List<Double> inputSignal) throws SULException {
        assert (isInitial && endTime == 0) || (endTime > 0.0);
        if (inputSignal == null) {
            return null;
        }
        List<Double> result;
        LOGGER.trace("Input: " + inputSignal);

        appendSignalStep(previousInput, inputSignal);
        try {
            // Make the input signal
            int numberOfSamples = (int) (endTime * 1 / signalStep) + 1;
            StringBuilder builder = new StringBuilder();
            makeDataSet(numberOfSamples, inputSignal.size(), builder);

            configureSimulink(builder);
            preventHugeTempFile(builder);

            // Execute the simulation
            builder.append("set_param(mdl,'SaveFinalState','on','FinalStateName', 'myOperPoint','SaveCompleteFinalSimState','on');");
            if (isInitial) {
                builder.append("set_param(mdl, 'LoadInitialState', 'off');");
                isInitial = false;
            } else {
                builder.append("set_param(mdl, 'LoadInitialState', 'on');");
                builder.append("set_param(mdl, 'InitialState', 'myOperPoint');");
            }


            // Run the simulation
            runSimulation(builder, endTime + signalStep);

            matlab.eval(builder.toString());

            // get the simulation result and make the result
            double[][] y = matlab.getVariable("y");

            result = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(y[y.length - 1])));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            assert false;
            throw new SULException(e);
        }

        // Final internal process
        endTime += signalStep;
        assert !isInitial;
        assert endTime > 0.0;
        LOGGER.trace("Output: " + result);

        return result;
    }

    private void makeDataSet(int numberOfSamples, int signalDimension, StringBuilder builder) throws ExecutionException, InterruptedException {
        builder.append("numberOfSamples = ").append(numberOfSamples).append(";");
        //matlab.putVariable("numberOfSamples", (double) numberOfSamples);
        builder.append("timeVector = (0:numberOfSamples) * signalStep;");
        //matlab.eval("timeVector = (0:numberOfSamples) * signalStep;");
        builder.append("ds = Simulink.SimulationData.Dataset;");
        //matlab.eval("ds = Simulink.SimulationData.Dataset;");
        for (int i = 0; i < signalDimension; i++) {
            double[] tmp = previousInput.get(i).stream().mapToDouble(Double::doubleValue).toArray();
            matlab.putVariable("tmp" + i, tmp);
            builder.append("input").append(i).append(" = timeseries(tmp").append(i).append(", timeVector);");
            //matlab.eval("input = timeseries(tmp, timeVector);");
            builder.append("ds = ds.addElement(input").append(i).append(", '").append(paramNames.get(i)).append("');");
            //matlab.eval("ds = ds.addElement(input, '" + paramNames.get(i) + "');");
        }
    }

    private void configureSimulink(StringBuilder builder) {
        // We use the data in ds
        builder.append("set_param(mdl, 'LoadExternalInput', 'on');");
        builder.append("set_param(mdl, 'ExternalInput', 'ds');");

        // Enable fast restart
        if (this.useFastRestart) {
            builder.append("set_param(mdl,'FastRestart','on');");
        } else {
            builder.append("set_param(mdl,'FastRestart','off');");
        }

        /// Configuration on the accelerator
        // Use normal mode
        // builder.append("set_param(mdl,'SimulationMode','normal');");
        // Enable accelerator mode
        builder.append("set_param(mdl,'SimulationMode','accelerator');");
        // Enable classic accelerator mode
        builder.append("set_param(0, 'GlobalUseClassicAccelMode', 'on');");


        // The save format must be an array
        builder.append("set_param(mdl, 'SaveFormat', 'Array');");

        // Configuration on the decimation
        builder.append("set_param(mdl, 'SolverType', 'Fixed-step');");
        builder.append("set_param(mdl, 'FixedStep', '").append(simulinkSimulationStep).append("');");
        builder.append("set_param(mdl, 'Decimation', '").append(signalStep / simulinkSimulationStep).append("');");
    }

    private void preventHugeTempFile(StringBuilder builder) {
        builder.append("Simulink.sdi.setAutoArchiveMode(false);");
        builder.append("Simulink.sdi.setArchiveRunLimit(0);");
        builder.append("Simulink.sdi.clear;");
    }

    private void runSimulation(StringBuilder builder, double stopTime) {
        // append the input signal
        builder.append("in = Simulink.SimulationInput(mdl);");
        builder.append("in = in.setExternalInput(ds);");

        // Set the StopTime
        builder.append("in = in.setModelParameter('StopTime', '").append(stopTime).append("');");
        // Save the output to yout
        if (!this.useFastRestart) {
            builder.append("in = in.setModelParameter('SaveOutput', 'on');");
            builder.append("in = in.setModelParameter('OutputSaveName', 'yout');");
            builder.append("in = in.setModelParameter('SaveTime', 'on');");
            builder.append("in = in.setModelParameter('OutputTimeName', 'tout');");
        }
        builder.append("in = in.setModelParameter('LoadInitialState', 'off');");

        // Execute the simulation
        builder.append("simOut = sim(in);");
        builder.append("y = simOut.get('yout');");
    }

    /**
     * @param inputSignal The input signal
     * @return The output signal. The size is same as the input.
     */
    Word<List<Double>> execute(Word<List<Double>> inputSignal) throws InterruptedException, ExecutionException {
        assert (isInitial && endTime == 0) || (endTime > 0.0);
        if (inputSignal == null) {
            return null;
        }

        pre();
        final int numberOfSamples = inputSignal.length();
        final int signalDimension = paramNames.size();
        for (List<Double> signalStep : inputSignal) {
            appendSignalStep(previousInput, signalStep);
        }
        StringBuilder builder = new StringBuilder();

        makeDataSet(numberOfSamples, signalDimension, builder);

        configureSimulink(builder);

        preventHugeTempFile(builder);

        runSimulation(builder, signalStep * numberOfSamples);

        matlab.eval(builder.toString());

        // get the simulation result and make the result
        double[][] y = matlab.getVariable("y");
        if (Objects.isNull(y) || Objects.isNull(y[0])) {
            if (this.useFastRestart) {
                this.useFastRestart = false;
                LOGGER.info("disable fast restart");
                return this.execute(inputSignal);
            } else {
                LOGGER.error("I do not know how to obtain non-null result");
                return null;
            }
        }

        //convert double[][] to Word<ArrayList<Double>
        WordBuilder<List<Double>> result = new WordBuilder<>();

        for (double[] outputStep : ArrayUtils.subarray(y, 1, y.length)) {
            result.append(Arrays.asList(ArrayUtils.toObject(outputStep)));
        }

        post();
        Word<List<Double>> resultWord = result.toWord();
        assert inputSignal.size() == resultWord.size();
        return resultWord;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public SUL<List<Double>, List<Double>> fork() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            matlab.close();
        }
    }
}
