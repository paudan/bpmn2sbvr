package org.ktu.model2sbvr.extract;

import com.nomagic.magicdraw.uml.Finder;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OpaqueAction;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityEdge;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.DecisionNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import org.junit.Test;
import org.ktu.model2sbvr.BpmnExtractionTestCase;
import org.ktu.model2sbvr.models.ConceptExtractionEntry;
import org.ktu.model2sbvr.models.SBVRExpressionModel;
import org.ktu.model2sbvr.models.SBVRExpressionModel.Conditional;
import org.ktu.model2sbvr.models.SBVRExpressionModel.RuleType;
import org.ktu.model2sbvr.models.SourceEntry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


public class BpmnExperimentFileTest extends BpmnExtractionTestCase {

    @Override
    protected Path getFilename() {
        return Paths.get("tests", "resources", "bpmn", "experiment_refactored.mdzip");
    }

    @Test
    public void testResourceElement() {
        Package root = getRootPackage();
        assertNotNull(root);
        Element exampleResource = Finder.byNameRecursively().find(root, Class.class, "1st Level Support Agent");
        assert exampleResource != null;
        assertTrue(StereotypesHelper.hasStereotype(exampleResource, "Resource"));
        System.out.println(exampleResource.getHumanName());
    }

    @Test
    public void testModelExtraction() {
        BpmnSBVRExtractor extractor = getExtractor("9e");
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        printExtractorOutput(brObjects);
    }

    @Test
    public void testIdentity() {
        Package root = getRootPackage();
        assertNotNull(root);
        BpmnSBVRExtractor extractor = getExtractor("2a");
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        Element technician = getElementByName(root, "Seminar Coordinator", Class.class, "Resource");
        assertNotNull(technician);
        Element receiveNotification = getElementByName(elements, "Publish Seminar Description in Website", OpaqueAction.class, "Task");
        assertNotNull(receiveNotification);
        Element supportDept = getElementByName(root, "Marketing Manager", Class.class, "Resource");
        assertNotNull(supportDept);
        Element reviewTask = getElementByName(elements, "Prepare Seminar Description", OpaqueAction.class, "Task");
        assertNotNull(reviewTask);
        List<Object> items = new ArrayList<>(Arrays.asList(technician, receiveNotification, supportDept, reviewTask));
        assertEquals(4, items.size());
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        Map<SourceEntry, ConceptExtractionEntry> matchingEntries = new HashMap<>();
        for (Entry<SourceEntry, ConceptExtractionEntry> entry: brObjects.entrySet())
            if (entry.getKey().getSourceObjects().equals(items))
                matchingEntries.put(entry.getKey(), entry.getValue());
        assertFalse(matchingEntries.isEmpty());
        assertEquals(1, matchingEntries.size());
        Entry<SourceEntry, ConceptExtractionEntry> matchingEntry = matchingEntries.entrySet().iterator().next();
        assertEquals(matchingEntry.getKey().getSourceObjects(), items);
        assertEquals(matchingEntry.getKey().getSourceNames(),
                Arrays.asList("Seminar Coordinator", "Publish Seminar Description in Website", "Marketing Manager", "Prepare Seminar Description"));
        assertEquals(1, matchingEntry.getValue().getCandidates().size());

        SBVRExpressionModel model = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION)
                .addUnidentifiedText("Seminar Coordinator Publish Seminar Description in Website")
                .addRuleConditional(Conditional.AFTER)
                .addUnidentifiedText("Marketing Manager Prepare Seminar Description");
        assertEquals(matchingEntry.getValue().getCandidates().iterator().next(), model);
    }

    @Test
    public void testSequenceFlowCondition() {
        Package root = getRootPackage();
        assertNotNull(root);
        BpmnSBVRExtractor extractor = getExtractor("2a");
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        Element cancelSub = getElementByName(elements, "Cancel Seminar", StructuredActivityNode.class, "SubProcess");
        assertNotNull(cancelSub);
        StructuredActivityNode cancelNode = (StructuredActivityNode) cancelSub;
        Collection<ActivityEdge> incoming = cancelNode.getIncoming();
        assertEquals(1, incoming.size());
        for (ActivityEdge edge: incoming)
            if (edge.getSource().getClassType().equals(DecisionNode.class))
                assertEquals("Too Few Participants", extractor.getCondition(edge));
    }
}
