package net.maswag.falcaun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericSULMapperSoundnessTest {

    private List<Map<Character, Double>> inputMapper;
    private NumericSULMapper mapper;
    private List<Map<Character, Double>> outputMapper;
    private List<Character> largest;

    @BeforeEach
    void setUp() {
        inputMapper = List.of(Map.of('a', 1.0, 'b', 2.0));
        outputMapper = List.of(Map.of('a', 1.0, 'b', 2.0));
        largest = List.of('c');
        mapper = new NumericSULMapper(inputMapper, largest, outputMapper, new SimpleSignalMapper());
    }

    Boolean verify(Double e0, STLOutputAtomic.Operation op, Double e1) {
        String result = mapper.mapOutput(new IOSignalPiece<>(Collections.emptyList(), List.of(e0)));
        var formula = new STLOutputAtomic(0, op, e1);
        formula.setAtomic(outputMapper, largest);
        return formula.getSatisfyingAtomicPropositions().contains(result);
    }

    @Test
    void mapOutput() {
        List<Double> values = List.of(0.5, 1.0, 1.5, 2.0, 2.5);

        for (var e0 : values) {
            // for(var e1 : values) {
            for (var e1 : outputMapper.get(0).values()) {
                if (e0 <= e1) {
                    assertTrue(verify(e0, STLOutputAtomic.Operation.lt, e1));
                }
                if (e0 > e1) {
                    assertTrue(verify(e0, STLOutputAtomic.Operation.gt, e1));
                }
                if (e0 == e1) {
                    assertTrue(verify(e0, STLOutputAtomic.Operation.eq, e1));
                }
                if (e0 != e1) {
                    // assertTrue(verify(e0, STLOutputAtomic.Operation.ne, e1));
                }
            }
        }
    }
}
