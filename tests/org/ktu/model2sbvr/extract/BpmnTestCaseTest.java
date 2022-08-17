package org.ktu.model2sbvr.extract;

import com.nomagic.magicdraw.cbm.BPMNHelper;
import com.nomagic.magicdraw.cbm.profiles.BPMN2Profile;
import com.nomagic.magicdraw.uml.Finder;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OpaqueAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdcompleteactions.AcceptEventAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdintermediateactions.SendObjectAction;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityEdge;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityFinalNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ControlFlow;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.ActivityPartition;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.DecisionNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.EnumerationLiteral;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.ktu.model2sbvr.BpmnExtractionTestCase;
import org.ktu.model2sbvr.PluginUtilities;
import org.ktu.model2sbvr.models.ConceptExtractionEntry;
import org.ktu.model2sbvr.models.SourceEntry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class BpmnTestCaseTest extends BpmnExtractionTestCase {

    @Override
    protected Path getFilename() {
        return Paths.get("tests", "resources", "bpmn", "test_cases.mdzip");
    }

    private void runExtractionTest(String model, int actualGC, int actualVC, int actualBR) {
        BpmnSBVRExtractor extractor = getExtractor(model);
        Map<SourceEntry, ConceptExtractionEntry> gcObjects = extractor.getGCCandidateModel().getDataset();
        assertEquals(actualGC, gcObjects.size());
        Map<SourceEntry, ConceptExtractionEntry> vcObjects = extractor.getVCCandidateModel().getDataset();
        assertEquals(actualVC, vcObjects.size());
        Map<SourceEntry, ConceptExtractionEntry> brObjects = extractor.getBRCandidateModel().getDataset();
        printExtractorOutput(brObjects);
        assertEquals(actualBR, brObjects.size());
    }

    @Test
    public void testRuleT1Extraction() {
        runExtractionTest("Model1", 3, 2, 1);
        runExtractionTest("Model1a", 3, 2, 1);
    }

    @Test
    public void testRuleT2Extraction_PaperExample() {
        runExtractionTest("PaperExampleT2", 5, 4, 4);
    }

    @Test
    public void testRuleT2Extraction() {
        System.out.println("Running test on model without default conditions");
        runExtractionTest("Model2", 6, 5, 4);
        System.out.println("Running test on model with default conditions");
        runExtractionTest("Model2a", 6, 5, 4);

        System.out.println("Running test on model without default conditions and start/end events");
        runExtractionTest("Model2b", 6, 5, 4);
        System.out.println("Running test on model with default conditions and start/end events");
        runExtractionTest("Model2c", 6, 5, 4);
    }

    @Test
    public void testRuleT3Extraction() {
        runExtractionTest("Model3", 6, 5, 4);
    }

    @Test
    public void testRuleT4Extraction() {
        runExtractionTest("Model4", 2, 1, 3);
    }

    @Test
    public void testRuleT5Extraction() {
        runExtractionTest("Model5a", 6, 5, 4);
    }

    @Test
    public void testRuleT6Extraction() {
        runExtractionTest("Model5", 3, 2, 8);
    }

    @Test
    public void testRuleT7Extraction() {
        System.out.println("Running test on model with data associations");
        runExtractionTest("Model6a", 6, 2, 2);
        System.out.println("Running test on model with simple associations on sequence flows");
        runExtractionTest("Model6b", 6, 2, 3);

        System.out.println("Running test on model with data associations and data types");
        runExtractionTest("Model6c", 6, 2, 2);
        System.out.println("Running test on model with simple associations on sequence flows and data types");
        runExtractionTest("Model6d", 6, 2, 3);
    }

    @Test
    public void testRuleT8Extraction() {
        runExtractionTest("Model7a", 6, 2, 2);
    }

    @Test
    public void testRuleT9Extraction() {
        runExtractionTest("Model8", 10, 4, 8);
    }

    @Test
    public void testGetBoundaryEvents_Model5() {
        Profile profile = PluginUtilities.getBPMNProfile(project);
        Stereotype taskStereotype = StereotypesHelper.getStereotype(project, "Task", profile);
        Package root = getRootPackage();
        Element pkg = Finder.byName().find(root, Activity.class, "Model5");
        assertNotNull(pkg);
        Element task = Finder.byNameRecursively().find(root, OpaqueAction.class, "perform task");
        assertNotNull(task);
        List<Element> boundaryElements = BPMNHelper.getBoundaryEventRefs(task, taskStereotype);
        assertEquals(3, boundaryElements.size());
    }

    @Test
    public void testGetStereotypeProperties() {
        Profile profile = PluginUtilities.getBPMNProfile(project);
        Package root = getRootPackage();
        Element boundary = Finder.byNameRecursively().find(root, AcceptEventAction.class, "e-mail is sent");
        Stereotype stereotype = StereotypesHelper.getStereotype(project, "MessageBoundaryEvent", profile);
        assertNotNull(stereotype);
        List<Property> stereotypeProperties = StereotypesHelper.getPropertiesWithDerivedOrdered(stereotype);
        assertNotNull(stereotypeProperties);
        for (Property prop: stereotypeProperties) {
            List<Object> result = StereotypesHelper.getStereotypePropertyValue(boundary, stereotype, prop);
            List<String> repr = result.stream().map(Object::toString).collect(Collectors.toList());
            System.out.println(prop.getName() + ", value: " + String.join(",", repr));
        }
        List<Object> cancelActivity = StereotypesHelper.getStereotypePropertyValue(boundary, stereotype, "cancelActivity");
        assertEquals(1, cancelActivity.size());
        Object value = cancelActivity.get(0);
        assertTrue(value instanceof EnumerationLiteral);
        EnumerationLiteral valueCasted = (EnumerationLiteral) value;
        System.out.println(valueCasted.getName());
        System.out.println(valueCasted.getOwnedElement());
    }

    @Test
    public void testGetTopResource1() {
        DiagramPresentationElement diagram1 = getDiagramElements("TestResource");
        assertNotNull(diagram1);
        Collection<Element> elements = diagram1.getUsedModelElements();
        Element taskElement1 = Finder.byName().find(elements, OpaqueAction.class, "Hire developer");
        assertNotNull(taskElement1);
        OpaqueAction task1 = (OpaqueAction) taskElement1;
        Collection<ActivityPartition> partitions = task1.getInPartition();
        assertEquals(3, partitions.size());
        Set<ActivityPartition> topPartitions = partitions.stream().filter(n -> !n.hasSubpartition()).collect(Collectors.toSet());
        assertEquals(1, topPartitions.size());
        ActivityPartition part = topPartitions.iterator().next();
        Element subject = part.getRepresents() != null ? part.getRepresents() : part;
        String name = AbstractSBVRExtractor.extractElementText(subject);
        assertEquals("Manager", name);
    }

    @Test
    public void testGetTopResource2() {
        DiagramPresentationElement diagram1 = getDiagramElements("TestResource1");
        assertNotNull(diagram1);
        Collection<Element> elements = diagram1.getUsedModelElements();
        Element taskElement1 = Finder.byName().find(elements, OpaqueAction.class, "Hire developer");
        assertNotNull(taskElement1);
        OpaqueAction task1 = (OpaqueAction) taskElement1;
        Collection<ActivityPartition> partitions = task1.getInPartition();
        assertEquals(5, partitions.size());
        Set<ActivityPartition> topPartitions = partitions.stream().filter(n -> !n.hasSubpartition()).collect(Collectors.toSet());
        assertEquals(2, topPartitions.size());
        Set<String> names = topPartitions.stream().map(part -> {
            Element subject = part.getRepresents() != null ? part.getRepresents() : part;
            return AbstractSBVRExtractor.extractElementText(subject);
        }).collect(Collectors.toSet());
        String [] outputs = {"Manager", "Developer"};
        MatcherAssert.assertThat(names, Matchers.containsInAnyOrder(outputs));
    }

    @Test
    public void testGetDefaultCondition() {
        DiagramPresentationElement diagram1 = getDiagramElements("TestDefault1");
        assertNotNull(diagram1);
        Collection<Element> elements = diagram1.getUsedModelElements();
        Element taskElement1 = Finder.byName().find(elements, StructuredActivityNode.class, "Confirm Seminar");
        assertNotNull(taskElement1);
        Collection<DecisionNode> gatewayElements = Finder.byType().find(elements, DecisionNode.class);
        assertEquals(1, gatewayElements.size());
        DecisionNode node = gatewayElements.iterator().next();
        assertTrue(BPMN2Profile.isExclusiveGateway(node));
        Collection<ControlFlow> flows = Finder.byType().find(elements, ControlFlow.class);
        assertEquals(3, flows.size());
        Set<ControlFlow> default_ = flows.stream().filter(n -> BPMNHelper.isDefaultSequenceFlow(node, n)).collect(Collectors.toSet());
        assertEquals(1, default_.size());
        ControlFlow defaultFlow = default_.iterator().next();
        assertEquals(taskElement1, defaultFlow.getTarget());
    }

    public void performTestStartEvent(String modelName) {
        DiagramPresentationElement diagram1 = getDiagramElements(modelName);
        assertNotNull(diagram1);
        Collection<Element> elements = diagram1.getUsedModelElements();
        Collection<InitialNode> startEvents = Finder.byType().find(elements, InitialNode.class);
        assertEquals(1, startEvents.size());
        InitialNode startNode = startEvents.iterator().next();
        assertTrue(BPMN2Profile.isStartEvent(startNode));
        String text = AbstractSBVRExtractor.extractElementText(startNode);
        assertNull(text);
        Activity owner = startNode.getActivity();
        assertNotNull(owner);
        String name = owner.getName();
        assertEquals(name, modelName);
    }

    @Test
    public void testStartEvent() {
        performTestStartEvent("Model2b");
        performTestStartEvent("Model2c");
    }

    private void performTestEndEvent(String modelName, String actualName) {
        DiagramPresentationElement diagram1 = getDiagramElements(modelName);
        assertNotNull(diagram1);
        Collection<Element> elements = diagram1.getUsedModelElements();
        Collection<ActivityFinalNode> endEvents = Finder.byType().find(elements, ActivityFinalNode.class);
        assertEquals(1, endEvents.size());
        ActivityFinalNode endNode = endEvents.iterator().next();
        String name = AbstractSBVRExtractor.extractElementText(endNode);
        if (name == null) {
            Activity owner = endNode.getActivity();
            assertNotNull(owner);
            name = owner.getName() + " ends";
        }
        assertEquals(actualName, name);
    }


    @Test
    public void testEndEvent() {
        performTestEndEvent("Model2b", "Model2b ends");
        performTestEndEvent("Model2c", "activity is finished");
    }

    @Test
    public void testSpecializations() {
        DiagramPresentationElement diagram1 = getDiagramElements("TestSpecializations");
        assertNotNull(diagram1);
        Collection<Element> elements = diagram1.getUsedModelElements();
        Collection<InitialNode> startEvents = Finder.byType().find(elements, InitialNode.class);
        assertEquals(10, startEvents.size());
        for (InitialNode node: startEvents)
            assertTrue(BPMN2Profile.isStartEvent(node));
        Collection<AcceptEventAction> intermediaryEvents = Finder.byType().find(elements, AcceptEventAction.class);
        assertEquals(8, intermediaryEvents.size());
        for (AcceptEventAction node: intermediaryEvents)
            assertTrue(BPMN2Profile.isIntermediateCatchEvent(node));
        Collection<SendObjectAction> throwEvents = Finder.byType().find(elements, SendObjectAction.class);
        assertEquals(6, throwEvents.size());
        for (SendObjectAction node: throwEvents)
            assertTrue(BPMN2Profile.isIntermediateThrowEvent(node));
        Collection<ActivityFinalNode> endEvents = Finder.byType().find(elements, ActivityFinalNode.class);
        assertEquals(9, endEvents.size());
        for (ActivityFinalNode node: endEvents)
            assertTrue(BPMN2Profile.isEndEvent(node));
    }

    @Test
    public void testStereotypes() {
        DiagramPresentationElement diagram1 = getDiagramElements("Model1");
        assertNotNull(diagram1);
        Collection<Element> elements = diagram1.getUsedModelElements();
        Collection<OpaqueAction> taskList = Finder.byType().find(elements, OpaqueAction.class);
        assertEquals(2, taskList.size());
        OpaqueAction task = taskList.iterator().next();
        Stereotype st = BPMNHelper.getTaskStereotype(task);
        assertNotNull(st);
        assertEquals("Task", st.getName());

        diagram1 = getDiagramElements("Model1a");
        assertNotNull(diagram1);
        elements = diagram1.getUsedModelElements();
        Collection<StructuredActivityNode> subprocess = Finder.byType().find(elements, StructuredActivityNode.class);
        assertEquals(1, subprocess.size());
        StructuredActivityNode subprocessNode = subprocess.iterator().next();
        st = BPMNHelper.getTaskStereotype(subprocessNode);
        assertNull(st);
    }


    private Collection<Element> getDiagramElements(Collection<Element> colelem, DiagramPresentationElement diagram) {
        Collection<Element> newelem = new HashSet<>();
        for (Element element : colelem)
            if (element instanceof Package && diagram.findPresentationElement(element, null) != null)
                addPackageElements(diagram, (Package) element, newelem);
            else if (element instanceof StructuredActivityNode && diagram.findPresentationElement(element, null) != null)
                addSubProcessElements(diagram, (StructuredActivityNode) element, newelem);
        return newelem;
    }


    private void addPackageElements(DiagramPresentationElement diagram, Package pack, Collection<Element> elements) {
        for (Element el : pack.getOwnedElement())
            if (diagram.findPresentationElement(el, null) != null)
                elements.add(el);
        for (Package innerPack : pack.getNestedPackage())
            if (diagram.findPresentationElement(innerPack, null) != null)
                addPackageElements(diagram, innerPack, elements);
    }

    private void addSubProcessElements(DiagramPresentationElement diagram, StructuredActivityNode node, Collection<Element> elements) {
        for (Element el : node.getOwnedElement())
            if (diagram.findPresentationElement(el, null) != null)
                elements.add(el);
        for (ActivityNode innerNode : node.getNode())
            if (innerNode instanceof StructuredActivityNode && diagram.findPresentationElement(innerNode, null) != null)
                addSubProcessElements(diagram, (StructuredActivityNode) innerNode, elements);
    }

    private DiagramPresentationElement getDiagramElement(String modelName) {
        Package model = getRootPackage();
        assertNotNull(model);
        Collection<DiagramPresentationElement> diagrams = BpmnSBVRExtractor.getBPMNDiagrams(model);
        Optional<DiagramPresentationElement> diagramOpt = diagrams.stream()
                .filter(n -> n.getName() != null && n.getName().compareToIgnoreCase(modelName) == 0).findFirst();
        assertTrue(diagramOpt.isPresent());
        return diagramOpt.get();
    }

    @Test
    public void testElementCollection() {
        DiagramPresentationElement diagram = getDiagramElement("SubprocessDiagram");
        Collection<Element> extracted = diagram.getUsedModelElements();
        extracted.addAll(getDiagramElements(extracted, diagram));
        System.out.println(extracted.size());
        OpaqueAction element = Finder.byName().find(extracted, OpaqueAction.class, "Initiate sending process");
        assertNotNull(element);
        Map<Element, String> subjects = getSubjectNames(element);
        assertEquals(1, subjects.size());
        Entry<Element, String> subject = subjects.entrySet().iterator().next();
        assertNotNull(subject);
        assertTrue(subject.getKey() instanceof Class);
        assertEquals(subject.getValue(), "Resource Developer");
    }

    private Collection<ActivityPartition> getInPartition(ActivityNode node) {
        if (!node.hasInPartition())
            return getInPartition(node.getInStructuredNode());
        return node.getInPartition();
    }

    private Collection<ActivityPartition> getInPartition(ActivityEdge node) {
        if (!node.hasInPartition())
            return getInPartition(node.getInStructuredNode());
        return node.getInPartition();
    }

    private Map<Element, String> getSubjectNames(Element element) {
        Map<Element, String> names = new HashMap<>();
        Collection<ActivityPartition> parts = null;
        if (element instanceof ActivityNode)
            parts = getInPartition((ActivityNode) element);
        else if (element instanceof ActivityEdge)
            parts = getInPartition((ActivityEdge) element);
        if (parts == null)
            return names;
        // Select lowest level partitions as subjects
        parts = parts.stream().filter(n -> !n.hasSubpartition()).collect(Collectors.toSet());
        for (ActivityPartition part : parts) {
            Element subject = part.getRepresents() != null ? part.getRepresents() : part;
            String name = subject.getHumanName();
            if (name == null)
                continue;
            names.put(subject, name);
        }
        return names;
    }

    @Test
    public void testStartEndModelExtraction() {
        runExtractionTest("TestStartEnd", 6, 5, 4);
    }
}
