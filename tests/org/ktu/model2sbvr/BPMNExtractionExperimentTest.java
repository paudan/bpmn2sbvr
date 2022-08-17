package org.ktu.model2sbvr;

import org.junit.Test;
import org.ktu.model2sbvr.extract.BpmnSBVRExtractor;
import org.ktu.model2sbvr.models.ConceptExtractionEntry;
import org.ktu.model2sbvr.models.SBVRExpressionModel;
import org.ktu.model2sbvr.models.SourceEntry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BPMNExtractionExperimentTest extends BpmnExtractionTestCase {

    private final Map<String, Map<String, Integer>> ruleStats = new TreeMap<>();
    private final Set<String> ruleNames;

    public BPMNExtractionExperimentTest() {
        ruleNames = new TreeSet<>();
        for (int i = 1; i <=9; i++)
            ruleNames.add("T" + i);
        ruleNames.add("Complex");
    }

    @Override
    protected Path getFilename() {
        return Paths.get("tests", "resources", "bpmn", "experiment_refactored.mdzip");
    }

    private void saveExtractorOutput(Map<SourceEntry, ConceptExtractionEntry> objects, Writer writer, String modelName) throws IOException {
        int i = 1;
        Map<String, Integer> modelStats = ruleStats.get(modelName);
        for(Entry<SourceEntry, ConceptExtractionEntry> item: objects.entrySet()) {
            List<String> outputs = new ArrayList<>();
            for (SBVRExpressionModel sbvr: item.getValue().getCandidates())
                outputs.add(sbvr.toString());
            String rule = item.getKey().getRule();
            modelStats.put(rule, modelStats.get(rule) + 1);
            writer.write(String.format("%d. [%s] Source: ", i, rule) + String.join(",", item.getKey().getSourceNames()) +
                    " -> output: " + String.join(",", outputs) + "\r\n");
            i += 1;
        }
    }

    private void outputStats(String filename) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(filename)), StandardCharsets.UTF_8))) {
            writer.write("Model;");
            writer.write(String.join(";", ruleNames));
            writer.newLine();
            for (Entry<String, Map<String, Integer>> modelEntry: ruleStats.entrySet()) {
                writer.write(modelEntry.getKey() + ";");
                for (Entry<String, Integer> ruleEntry: modelEntry.getValue().entrySet())
                    writer.write(ruleEntry.getValue() + ";");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testExtractModels() {
        String dirName = "results";
        try {
            Files.createDirectories(Paths.get(dirName));
        } catch (IOException e) {
            Logger.getLogger(getName()).log(Level.SEVERE, null, e);
        }
        String[] modelNames = {"1", "2a", "2b", "2c", "2d", "2e", "3a", "3b", "4", "5",
                "6a", "6b", "6c", "6d", "6e", "6f", "6g", "7", "8", "9a", "9b", "9c", "9e",
                "10a", "10b", "11a", "11b", "12a", "12b", "12c", "12d", "12e"};
        for (String modelName: modelNames) {
            BpmnSBVRExtractor extractor = getExtractor(modelName);
            Map<String, Integer> modelStats = new TreeMap<>();
            for (String ruleName: ruleNames)
                modelStats.put(ruleName, 0);
            ruleStats.put(modelName, modelStats);
            String filePath = Paths.get(dirName, modelName + ".txt").toString();
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(filePath)), StandardCharsets.UTF_8))) {
                Map<String, Map<SourceEntry, ConceptExtractionEntry>> mapByRule = objectsByRuleName(extractor.getBRCandidateModel().getDataset());
                for (Map<SourceEntry, ConceptExtractionEntry> ruleSet: mapByRule.values())
                    saveExtractorOutput(ruleSet, writer, modelName);
            } catch (IOException e) {
                Logger.getLogger(getName()).log(Level.SEVERE, null, e);
            }
        }
        outputStats(Paths.get(dirName,"general_stats.csv").toString());
    }
}
