package org.group_mmm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import de.learnlib.api.oracle.MembershipOracle;
import net.automatalib.modelcheckers.ltsmin.AbstractLTSmin;
import net.automatalib.words.WordBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AutotransExampleTest {

    @Test
    void constructAT1() {
        AutotransExample exampleAT = new AutotransExample(10.0);
        System.out.println(exampleAT.constructAT1(4200));
    }

    @Test
    void memOracleBB() throws Exception {
        AutotransExample exampleAT = new AutotransExample(10.0);
        exampleAT.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT.constructAT1(4500.0))));
        exampleAT.constructVerifier();

        // Get BlackBoxVerifier
        Field field = exampleAT.getVerifier().getClass().getDeclaredField("verifier");
        field.setAccessible(true);
        BlackBoxVerifier verifier = (BlackBoxVerifier) field.get(exampleAT.getVerifier());

        // Get MemOracle
        field = verifier.getClass().getDeclaredField("memOracle");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        MembershipOracle.MealyMembershipOracle<String, String> memOracle =
                (MembershipOracle.MealyMembershipOracle<String, String>) field.get(verifier);

        WordBuilder<String> input = new WordBuilder<>();
        input.append("bb");
        WordBuilder<String> expected = new WordBuilder<>();
        expected.append("ba2");
        assertEquals(expected.toWord(), memOracle.answerQuery(input.toWord()));
    }

    /**
     * AT1 in [HAF14] for omega = 4500
     */
    @Test
    void runAT1() throws Exception {
        final Logger LOGGER = (Logger) LoggerFactory.getLogger(AbstractLTSmin.class);
        LOGGER.setLevel(Level.DEBUG);

        AutotransExample exampleAT1 = new AutotransExample(10.0);
        exampleAT1.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT1.constructAT1(4500.0))));

        exampleAT1.constructVerifier();

        assertFalse(exampleAT1.getVerifier().run());
        System.out.println("CexInput: " + exampleAT1.getVerifier().getCexAbstractInput());
        System.out.println("CexOutput: " + exampleAT1.getVerifier().getCexOutput());
    }

    /**
     * AT2 in [HAF14] for v = 120 and omega = 4500
     */
    @Test
    void runAT2() throws Exception {
        AutotransExample exampleAT2 = new AutotransExample(10.0);
        exampleAT2.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT2.constructAT2(120, 4500.0))));
        exampleAT2.constructVerifier();

        assertFalse(exampleAT2.getVerifier().run());
        System.out.println("CexInput: " + exampleAT2.getVerifier().getCexAbstractInput());
        System.out.println("CexOutput: " + exampleAT2.getVerifier().getCexOutput());
    }

    /**
     * AT2 in [HAF14] for v = 160 and omega = 5000
     */
    @Test
    void runAT2_1() throws Exception {
        AutotransExample exampleAT2 = new AutotransExample(10.0);
        ArrayList<Map<Character, Double>> outputMapper = exampleAT2.getOutputMapper();

        // Construct the input mapper
        {
            Map<Character, Double> throttleMapper = new HashMap<>();
            throttleMapper.put('a', 0.0);
            //throttleMapper.put('b', 20.0);
            //throttleMapper.put('c', 40.0);
            //throttleMapper.put('d', 60.0);
            //throttleMapper.put('e', 80.0);
            throttleMapper.put('f', 100.0);

            Map<Character, Double> brakeMapper = new HashMap<>();
            brakeMapper.put('a', 0.0);
            //brakeMapper.put('b', 50.0);
            //brakeMapper.put('c', 100.0);
            //brakeMapper.put('d', 150.0);
            //brakeMapper.put('e', 200.0);
            //brakeMapper.put('f', 250.0);
            //brakeMapper.put('g', 300.0);
            brakeMapper.put('h', 325.0);

            exampleAT2.setInputMapper(new ArrayList<>(Arrays.asList(throttleMapper, brakeMapper)));
        }

        //{120, 160, 170, 200}.
        Map<Character, Double> velocityMapper = new HashMap<>();
        velocityMapper.put('a', 80.0);
        velocityMapper.put('b', 120.0);
        velocityMapper.put('c', 160.0);
        //velocityMapper.put('d', 70.0);
        velocityMapper.put('e', 170.0);
        //velocityMapper.put('f', 110.0);
        velocityMapper.put('g', 180.0);
        outputMapper.set(0, velocityMapper);


        //{4500, 5000, 5200, 5500}.
        Map<Character, Double> rotationMapper = new HashMap<>();
        rotationMapper.put('a', 4000.0);
        rotationMapper.put('b', 4500.0);
        rotationMapper.put('c', 4750.0);
        rotationMapper.put('d', 5000.0);
        //rotationMapper.put('e', 5000.0);
        //rotationMapper.put('f', 5200.0);
        //rotationMapper.put('g', 5500.0);
        //rotationMapper.put('h', 5750.0);
        //rotationMapper.put('i', 6000.0);
        outputMapper.set(1, rotationMapper);

        exampleAT2.setOutputMapper(outputMapper);
        exampleAT2.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT2.constructAT2(160, 5000.0))));

        exampleAT2.constructVerifier();

        // exampleAT2.getVerifier().addRandomWalkEQOracle(0.3, 100, new Random());
        // exampleAT2.getVerifier().addBFOracle(100.0);
        exampleAT2.getVerifier().addRandomWordEQOracle(5, 30, 100, new Random(), 1);
        // exampleAT2.getVerifier().addCompleteExplorationEQOracle(1, 10, 1);

        // We cannot falsify this example
        assertTrue(exampleAT2.getVerifier().run());
        //System.out.println(exampleAT2.getVerifier().run());

        exampleAT2.getVerifier().visualizeLearnedMealy();
    }

    @Test
    void constructAT3() {
        AutotransExample exampleAT = new AutotransExample(10.0);

        //{120, 160, 170, 200}.
        Map<Character, Double> velocityMapper = new HashMap<>();
        velocityMapper.put('a', 80.0);


        //{4500, 5000, 5200, 5500}.
        Map<Character, Double> rotationMapper = new HashMap<>();
        rotationMapper.put('a', 4000.0);

        Map<Character, Double> gearMapper = new HashMap<>();
        gearMapper.put('1', 1.0);
        gearMapper.put('2', 2.0);
        gearMapper.put('3', 3.0);

        exampleAT.setOutputMapper(new ArrayList<>(
                Arrays.asList(velocityMapper, rotationMapper, gearMapper)));

        ArrayList<Character> largest = new ArrayList<>(Arrays.asList('b', 'b', '4'));
        exampleAT.setLargest(largest);


        String gear1 = "((output == \"aa1\") || (output == \"ab1\") || (output == \"ba1\") || (output == \"bb1\"))";
        String gear2 = "((output == \"aa2\") || (output == \"ab2\") || (output == \"ba2\") || (output == \"bb2\"))";

        String expected = "[] (( " + gear2 + " && X " + gear1 + " ) -> (" + "X(!" + gear2 + ") && " + "X(X(!" + gear2 + ")) " + "))";

        assertEquals(expected, exampleAT.constructAT3());
    }

    @Test
    void runAT3() throws Exception {
        AutotransExample exampleAT = new AutotransExample(1.0);

        //{120, 160, 170, 200}.
        Map<Character, Double> velocityMapper = new HashMap<>();
        velocityMapper.put('a', 100.0);


        //{4500, 5000, 5200, 5500}.
        Map<Character, Double> rotationMapper = new HashMap<>();
        rotationMapper.put('a', 4000.0);

        Map<Character, Double> gearMapper = new HashMap<>();
        gearMapper.put('1', 1.0);
        gearMapper.put('2', 2.0);
        gearMapper.put('3', 3.0);

        exampleAT.setOutputMapper(new ArrayList<>(
                Arrays.asList(velocityMapper, rotationMapper, gearMapper)));

        ArrayList<Character> largest = new ArrayList<>(Arrays.asList('b', 'b', '4'));
        exampleAT.setLargest(largest);

        exampleAT.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT.constructAT3())));

        exampleAT.constructVerifier();
        exampleAT.getVerifier().addRandomWordEQOracle(5, 30, 100, new Random(), 1);
        System.out.println(exampleAT.getVerifier().run());

        exampleAT.getVerifier().visualizeLearnedMealy();

        System.out.println("CexInput: " + exampleAT.getVerifier().getCexAbstractInput());
        System.out.println("CexOutput: " + exampleAT.getVerifier().getCexOutput());
    }

    @Test
    void runS1() throws Exception {
        final Logger LOGGER = (Logger) LoggerFactory.getLogger(AbstractLTSmin.class);
        LOGGER.setLevel(Level.INFO);

        AutotransExample exampleAT = new AutotransExample(10.0);

        // Construct the input mapper
        {
            Map<Character, Double> throttleMapper = new HashMap<>();
            throttleMapper.put('a', 0.0);
            //throttleMapper.put('b', 20.0);
            //throttleMapper.put('c', 40.0);
            //throttleMapper.put('d', 60.0);
            //throttleMapper.put('e', 80.0);
            throttleMapper.put('f', 100.0);

            Map<Character, Double> brakeMapper = new HashMap<>();
            brakeMapper.put('a', 0.0);
            //brakeMapper.put('b', 50.0);
            //brakeMapper.put('c', 100.0);
            //brakeMapper.put('d', 150.0);
            //brakeMapper.put('e', 200.0);
            //brakeMapper.put('f', 250.0);
            //brakeMapper.put('g', 300.0);
            brakeMapper.put('h', 325.0);

            exampleAT.setInputMapper(new ArrayList<>(Arrays.asList(throttleMapper, brakeMapper)));
        }

        //{120, 160, 170, 200}.
        Map<Character, Double> velocityMapper = new HashMap<>();
        velocityMapper.put('a', 80.0);
        velocityMapper.put('b', 100.0);
        velocityMapper.put('c', 120.0);


        //{4500, 5000, 5200, 5500}.
        Map<Character, Double> rotationMapper = new HashMap<>();

        Map<Character, Double> gearMapper = new HashMap<>();

        exampleAT.setOutputMapper(new ArrayList<>(
                Arrays.asList(velocityMapper, rotationMapper, gearMapper)));

        ArrayList<Character> largest = new ArrayList<>(Arrays.asList('d', 'X', 'X'));
        exampleAT.setLargest(largest);

        exampleAT.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT.constructS1(120))));

        exampleAT.constructVerifier();
        exampleAT.getVerifier().addRandomWordEQOracle(2, 3, 100, new Random(), 1);
        assertFalse(exampleAT.getVerifier().run());

        FileWriter writer = new FileWriter(new File("./runS1Learned.dot"));
        exampleAT.getVerifier().writeDOTLearnedMealy(writer);
        writer.close();

        System.out.println("CexInput: " + exampleAT.getVerifier().getCexAbstractInput());
        System.out.println("CexOutput: " + exampleAT.getVerifier().getCexOutput());
    }

    @Test
    void runS2() throws Exception {
        AutotransExample exampleAT = new AutotransExample(5.0);

        // Construct the input mapper
        {
            Map<Character, Double> throttleMapper = new HashMap<>();
            throttleMapper.put('a', 0.0);
            //throttleMapper.put('b', 20.0);
            //throttleMapper.put('c', 40.0);
            throttleMapper.put('c', 50.0);
            //throttleMapper.put('d', 60.0);
            //throttleMapper.put('e', 80.0);
            throttleMapper.put('f', 100.0);

            Map<Character, Double> brakeMapper = new HashMap<>();
            brakeMapper.put('a', 0.0);
            //brakeMapper.put('b', 50.0);
            //brakeMapper.put('c', 100.0);
            brakeMapper.put('d', 150.0);
            //brakeMapper.put('e', 200.0);
            //brakeMapper.put('f', 250.0);
            //brakeMapper.put('g', 300.0);
            brakeMapper.put('h', 325.0);

            exampleAT.setInputMapper(new ArrayList<>(Arrays.asList(throttleMapper, brakeMapper)));
        }

        //{120, 160, 170, 200}.
        Map<Character, Double> velocityMapper = new HashMap<>();
        velocityMapper.put('a', 10.0);
        velocityMapper.put('b', 20.0);

        //{4500, 5000, 5200, 5500}.
        Map<Character, Double> rotationMapper = new HashMap<>();

        Map<Character, Double> gearMapper = new HashMap<>();
        gearMapper.put('1', 1.0);
        gearMapper.put('2', 2.0);
        gearMapper.put('3', 3.0);


        exampleAT.setOutputMapper(new ArrayList<>(
                Arrays.asList(velocityMapper, rotationMapper, gearMapper)));

        ArrayList<Character> largest = new ArrayList<>(Arrays.asList('c', 'X', '4'));
        exampleAT.setLargest(largest);

        exampleAT.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT.constructS2(20))));

        exampleAT.constructVerifier();
        exampleAT.getVerifier().addRandomWordEQOracle(5, 7, 100, new Random(), 1);
        System.out.println(exampleAT.getVerifier().run());

        exampleAT.getVerifier().visualizeLearnedMealy();

        System.out.println("CexInput: " + exampleAT.getVerifier().getCexAbstractInput());
        System.out.println("CexOutput: " + exampleAT.getVerifier().getCexOutput());
    }

    @Test
    void runS4() throws Exception {
        AutotransExample exampleAT = new AutotransExample(10.0);

        Map<Character, Double> velocityMapper = new HashMap<>();
        velocityMapper.put('a', 50.0);
        velocityMapper.put('b', 65.0);
        velocityMapper.put('c', 80.0);
        velocityMapper.put('d', 100.0);
        velocityMapper.put('e', 120.0);

        //{4500, 5000, 5200, 5500}.
        Map<Character, Double> rotationMapper = new HashMap<>();

        Map<Character, Double> gearMapper = new HashMap<>();


        exampleAT.setOutputMapper(new ArrayList<>(
                Arrays.asList(velocityMapper, rotationMapper, gearMapper)));

        ArrayList<Character> largest = new ArrayList<>(Arrays.asList('f', 'X', 'X'));
        exampleAT.setLargest(largest);

        exampleAT.setProperties(new ArrayList<>(
                Collections.singletonList(
                        exampleAT.constructS4(100.0, 65.0))));

        exampleAT.constructVerifier();
        exampleAT.getVerifier().addRandomWordEQOracle(5, 10, 100, new Random(), 1);
        System.out.println(exampleAT.getVerifier().run());

        FileWriter writer = new FileWriter(new File("./runS4Learned.dot"));
        exampleAT.getVerifier().writeDOTLearnedMealy(writer);
        writer.close();

        System.out.println("CexInput: " + exampleAT.getVerifier().getCexAbstractInput());
        System.out.println("CexOutput: " + exampleAT.getVerifier().getCexOutput());
    }
}