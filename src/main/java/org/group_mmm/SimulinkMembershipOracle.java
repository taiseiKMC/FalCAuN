package org.group_mmm;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.Query;
import net.automatalib.words.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * The membership oracle for a Simulink model
 */
public class SimulinkMembershipOracle implements MembershipOracle.MealyMembershipOracle<String, String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulinkMembershipOracle.class);

    private final SimulinkSUL simulink;
    private final SimulinkSULMapper mapper;
    private final TreeCache<String, String> cache;

    SimulinkMembershipOracle(SimulinkSUL simulink, SimulinkSULMapper mapper) {
        this.simulink = simulink;
        this.mapper = mapper;
        this.cache = new TreeCache<>(mapper.constructAbstractAlphabet());
    }

    @Override
    public void processQueries(Collection<? extends Query<String, Word<String>>> queries) {
        for (Query<String, Word<String>> q : queries) {
            final Word<String> abstractInput = q.getInput();
            Word<String> abstractOutput = cache.get(abstractInput);

            if (abstractOutput == null) {
                final Word<ArrayList<Double>> concreteInput = Word.fromList(
                        abstractInput.stream().map(mapper::mapInput).collect(Collectors.toList()));
                assert concreteInput.size() == q.getInput().size();

                final Word<ArrayList<Double>> concreteOutput;
                try {
                    concreteOutput = simulink.execute(concreteInput);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                    return;
                }
                assert concreteOutput.size() == concreteInput.size();
                abstractOutput = Word.fromList(
                        concreteOutput.stream().map(mapper::mapOutput).collect(Collectors.toList()));
                assert concreteOutput.size() == abstractOutput.size();
                cache.put(abstractInput, abstractOutput);
            }

            final Word<String> output = abstractOutput.suffix(q.getSuffix().length());
            q.answer(output);
        }
    }
}

