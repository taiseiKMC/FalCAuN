package org.group_mmm;

import de.learnlib.api.oracle.PropertyOracle;
import org.apache.commons.cli.MissingOptionException;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
    private static int generationSize = 5;
    private static int childrenSize = 15 * 4;
    private static boolean resetWord = false;
    private static ArrayList<Function<ArrayList<Double>, Double>> sigMap = new ArrayList<>(Collections.emptyList());
    private static int maxTest = 50000;

    public static void main(String[] args) throws Exception {
        ArgParser argParser = new ArgParser(args);
        if (argParser.isQuit()) {
            return;
        }

        // Parse Simulink mapper
        ArrayList<Map<Character, Double>> inputMapper = InputMapperReader.parse(argParser.getInputMapperFile());
        OutputMapperReader outputMapperReader = new OutputMapperReader(argParser.getOutputMapperFile());
        outputMapperReader.parse();
        ArrayList<Character> largest = outputMapperReader.getLargest();
        ArrayList<Map<Character, Double>> outputMapper = outputMapperReader.getOutputMapper();
        if (argParser.isVerbose()) {
            System.out.println("InputMapper: " + inputMapper);
            System.out.println("OutputMapper: " + outputMapper);
            System.out.println("Largest: " + largest);
        }
        SimulinkSULMapper sulMapper = new SimulinkSULMapper(inputMapper, largest, outputMapper, sigMap);

        // Parse STL formulas
        List<STLCost> stl;
        if (argParser.getStlFormula() != null) {
            stl = Collections.singletonList(STLCost.parseSTL(argParser.getStlFormula(), outputMapper, largest));
        } else if (argParser.getStlFile() != null) {
            stl = Files.lines(FileSystems.getDefault().getPath(argParser.getStlFile())).map(line ->
                    STLCost.parseSTL(line, outputMapper, largest)).collect(Collectors.toList());
        } else {
            throw new MissingOptionException("STL formula is not given");
        }

        if (argParser.isVerbose()) {
            System.out.println("STL formulas: " + stl);
            List<String> ltlString = new ArrayList<>(stl.size());
            for (STLCost fml : stl) {
                ltlString.add(fml.toAbstractString());
            }
            System.out.println("LTL formulas: " + ltlString);
        }

        SimulinkVerifier verifier = new SimulinkVerifier(
                argParser.getInitScript(),
                argParser.getParamNames(),
                argParser.getStepTime(),
                stl.stream().map(STLCost::toAbstractString).collect(Collectors.toList()),
                sulMapper);

/*
            if (useHillClimbing) {
                exampleAT.getVerifier().addHillClimbingEQOracle(costFunc,
                        15,
                        new Random(),
                        50000, 5, 15 * 4, resetWord,
                        exampleAT.getVerifier().getLtlFormulas().get(0));
            } else if (useGA) {
                exampleAT.getVerifier().addGAEQOracle(costFunc,
                        15,
                        new Random(),
                        10000, 3, 3, 2, 0.01, 0.8, resetWord);
            } else {
                exampleAT.getVerifier().addRandomWordEQOracle(15, 15, 100, new Random(), 1);
            }
*/
        switch (argParser.getEquiv()) {
            case HC:
                for (int i = 0; i < stl.size(); i++) {
                    PropertyOracle.MealyPropertyOracle<String, String, String> ltlOracle = verifier.getLtlFormulas().get(i);
                    verifier.addHillClimbingEQOracle(stl.get(i), argParser.getLength(), new Random(), maxTest, generationSize, childrenSize, resetWord, ltlOracle);
                }
                if (argParser.isVerbose()) {
                    System.out.println("Hill Climing is used");
                }
                break;
            case WP:
                throw new UnsupportedOperationException("Wp is not implemented yet!!");
            case RANDOM:
                throw new UnsupportedOperationException("random is not implemented yet!!");
        }

        System.out.println("BBC started");
        long startTime = System.nanoTime();
        boolean result = verifier.run();
        long endTime = System.nanoTime();
        System.out.println("BBC finished");
        System.out.println("BBC Elapsed Time: " + ((endTime - startTime) / 1000000000.0) + " [sec]");
        if (result) {
            System.out.println("All the given properties are verified");
        } else {
            System.out.println("The following properties are falsified");
            for (int i = 0; i < verifier.getCexAbstractInput().size(); i++) {
                if (verifier.getCexAbstractInput().get(i) != null) {
                    System.out.println("Property: " + verifier.getCexProperty().get(i));
                    System.out.println("Concrete Input: " + verifier.getCexConcreteInput().get(i));
                    System.out.println("Abstract Input: " + verifier.getCexAbstractInput().get(i));
                    System.out.println("Output: " + verifier.getCexOutput().get(i));
                }
            }
        }

        if (argParser.getDotFile() != null) {
            FileWriter writer = new FileWriter(new File(argParser.getDotFile()));
            verifier.writeDOTLearnedMealy(writer);
            writer.close();
        }
    }
}
