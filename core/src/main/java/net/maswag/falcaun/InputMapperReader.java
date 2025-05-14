package net.maswag.falcaun;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.primitives.Chars;

import lombok.Getter;

public class InputMapperReader extends AbstractMapperReader {
    @Getter
    private List<Character> largest;
    @Getter
    private List<Map<Character, Double>> inputMapper;

    public InputMapperReader(String filename) throws IOException {
        List<List<Double>> parsedData = rawParse(filename);
        parse(parsedData);
    }

    /**
     * <p>Constructor for OutputMapperReader from data.</p>
     */
    public InputMapperReader(List<List<Double>> data) throws IOException {
        parse(data);
    }

    void parse(List<List<Double>> parsedData) throws IOException {
        char[] charList = new char[parsedData.size()];
        Arrays.fill(charList, 'a');

        inputMapper = assignCharacters(parsedData, charList);
        largest = new ArrayList<>(Chars.asList(charList));
    }
}
