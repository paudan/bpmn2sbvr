package org.ktu.model2sbvr.extract;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityEdge;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ControlNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import org.junit.Test;
import org.ktu.model2sbvr.extract.BpmnSBVRExtractor.GatewayNeighborhood;
import org.ktu.model2sbvr.models.SBVRExpressionModel.Conjunction;
import org.ktu.model2sbvr.tests.ExtractionTestCase;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class BpmnExtractorInternalsTest extends ExtractionTestCase {

    @Override
    protected Path getFilename() {
        return Paths.get("tests", "resources", "bpmn", "gateway_rules.mdzip");
    }

    private Package getRootPackage() {
        if (project == null)
            project = Application.getInstance().getProject();
        assertNotNull(project);
        return project.getPrimaryModel();
    }

    private BpmnSBVRExtractor getBpmnExtractor(String name) {
        Package model = getRootPackage();
        assertNotNull(model);
        Collection<DiagramPresentationElement> diagrams = BpmnSBVRExtractor.getBPMNDiagrams(model);
        Optional<DiagramPresentationElement> diagramOpt = diagrams.stream()
                .filter(n -> n.getName() != null && n.getName().compareToIgnoreCase(name) == 0).findFirst();
        assertTrue(diagramOpt.isPresent());
        BpmnSBVRExtractor extractor = new BpmnSBVRExtractor(diagramOpt.get(), false, false);
        extractor.extractAll();
        return extractor;
    }

    private void outputInternalStructures(String modelName, boolean fillIncoming) {
        BpmnSBVRExtractor extractor = getBpmnExtractor(modelName);
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        for (Element el: elements)
            if (extractor.isGatewayElement(el)){
                System.out.println();
                GatewayNeighborhood tuple = extractor.new GatewayNeighborhood((ControlNode) el, fillIncoming);
                System.out.println(tuple);
            }
    }

    @Test
    public void testInternalStructures() {
        outputInternalStructures("TestModel1", true);
    }

    @Test
    public void testInternalStructures2() {
        System.out.println("Structures, when incoming element structures are filled recursively:");
        outputInternalStructures("TestModel2", true);
        System.out.println("Structures, when outgoing element structures are filled recursively:");
        outputInternalStructures("TestModel2", false);
    }

    private void testGatewaySearch(String modelName, String taskName, String gatewayName, int sizeAssertion) {
        BpmnSBVRExtractor extractor = getBpmnExtractor(modelName);
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        for (Element el: elements)
            if (el.getHumanName().equalsIgnoreCase(taskName)){
                ActivityNode task = (ActivityNode) el;
                for (ActivityEdge edge: task.getIncoming())
                    if (extractor.isGatewayElement(edge.getSource())) {
                        GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(edge.getSource());
                        assertEquals(gatewayName, edge.getSource().getHumanName());
                        assertEquals(sizeAssertion, nhood.outgoingGateways.size());
                    }
            }
    }

    private ControlNode getBoundaryGateway(GatewayNeighborhood nhood) {
        if (nhood.outgoingGateways.isEmpty())
            return nhood.gatewayNode;
        for (GatewayNeighborhood neighborNode: nhood.outgoingGateways.values())
            return getBoundaryGateway(neighborNode);
        return null;
    }

    private void testFindBoundaryGateway(String modelName, String taskName, String gatewayName) {
        BpmnSBVRExtractor extractor = getBpmnExtractor(modelName);
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        for (Element el: elements)
            if (el.getHumanName().equalsIgnoreCase(taskName)){
                ActivityNode task = (ActivityNode) el;
                for (ActivityEdge edge: task.getIncoming())
                    if (extractor.isGatewayElement(edge.getSource())) {
                        GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(edge.getSource());
                        ControlNode gateway = getBoundaryGateway(nhood);
                        assertNotNull(gateway);
                        assertEquals(gatewayName, gateway.getHumanName());

                        Conjunction conjunction = getGatewayConjunction(extractor, gateway);
                        Object[] results = extractor.getRuleWithGateways(gateway, conjunction, null);
                        System.out.println(results[0]);
                    }
                break;
            }
    }

    private void getAllBoundaryGateways(GatewayNeighborhood nhood, Set<ControlNode> nodes) {
        if (nhood.outgoingGateways.isEmpty())
            nodes.add(nhood.gatewayNode);
        for (GatewayNeighborhood neighborNode: nhood.outgoingGateways.values())
            getAllBoundaryGateways(neighborNode, nodes);
    }


    private Conjunction getGatewayConjunction(BpmnSBVRExtractor extractor, ControlNode gateway) {
        if (extractor.hasAnyStereotype(gateway, "ExclusiveGateway"))
            return Conjunction.OR;
        else if (extractor.hasAnyStereotype(gateway, "InclusiveGateway"))
            return Conjunction.AND;
        return null;
    }

    private void testFindAllBoundaryGateways(String modelName, String taskName, int numAssert) {
        BpmnSBVRExtractor extractor = getBpmnExtractor(modelName);
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        for (Element el: elements)
            if (el.getHumanName().equalsIgnoreCase(taskName)){
                ActivityNode task = (ActivityNode) el;
                for (ActivityEdge edge: task.getIncoming())
                    if (extractor.isGatewayElement(edge.getSource())) {
                        GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(edge.getSource());
                        Set<ControlNode> boundaryGateways = new HashSet<>();
                        getAllBoundaryGateways(nhood, boundaryGateways);
                        assertEquals(numAssert, boundaryGateways.size());
                    }
                break;
            }
    }


    private void getAllBoundaryGatewaysLeft(GatewayNeighborhood nhood, Set<ControlNode> nodes) {
        if (nhood.incomingGateways.isEmpty())
            nodes.add(nhood.gatewayNode);
        for (GatewayNeighborhood neighborNode: nhood.incomingGateways.values())
            getAllBoundaryGatewaysLeft(neighborNode, nodes);
    }

    private Set<ControlNode> testFindAllBoundaryGatewaysLeft(String modelName, String gatewayName) {
        BpmnSBVRExtractor extractor = getBpmnExtractor(modelName);
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        Set<ControlNode> boundaryGateways = new HashSet<>();
        for (Element el: elements) {
            //System.out.println(el.getHumanName());
            if (el.getHumanName().equalsIgnoreCase(gatewayName) && extractor.isGatewayElement(el)) {
                GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(el);
                getAllBoundaryGatewaysLeft(nhood, boundaryGateways);
                break;
            }
        }
        return boundaryGateways;
    }

    private void testProcessPartialRules(String modelName, String... gatewayNames) {
        BpmnSBVRExtractor extractor = getBpmnExtractor(modelName);
        extractor.extractBusinessRuleCandidates();
        Collection<Element> elements = extractor.getExtractedDiagramElements();
        for (String gatewayName: gatewayNames)
            for (Element el: elements)
                if (extractor.isGatewayElement(el) && el.getHumanName().equalsIgnoreCase(gatewayName)){
                    GatewayNeighborhood nhood = extractor.gatewayNeighborhoods.get(el);
                    System.out.println(nhood);
                }
    }

    @Test
    public void testBoundaryGatewaySearchTestModel1() {
        Set<ControlNode> boundaryGateways = testFindAllBoundaryGatewaysLeft("TestModel1", "Inclusive Gateway Inclusive Gateway1");
        assertEquals(1, boundaryGateways.size());
        for (ControlNode gateway: boundaryGateways)
            System.out.println("Gateway: " + gateway.getHumanName());
    }

    @Test
    public void testBoundaryGatewaySearchTestModel2() {
        testGatewaySearch("TestModel2", "Task a2", "Exclusive Gateway Excl1", 0);
        testGatewaySearch("TestModel2", "Task t2", "Inclusive Gateway Inc1", 1);
        testFindBoundaryGateway("TestModel2", "Task t2", "Exclusive Gateway Excl1");
    }

    @Test
    public void testBoundaryGatewaySearchTestModel3() {
        testGatewaySearch("TestModel3", "Task a2", "Exclusive Gateway Excl2", 0);
        testGatewaySearch("TestModel3", "Task top", "Exclusive Gateway Excl2", 0);
        testGatewaySearch("TestModel3", "Task t2", "Inclusive Gateway Inc1", 1);

        testFindBoundaryGateway("TestModel3", "Task t2", "Exclusive Gateway Excl2");
    }

    @Test
    public void testBoundaryGatewaySearchTestModel4() {
        testGatewaySearch("TestModel4", "Task a2", "Exclusive Gateway Excl2", 0);
        testGatewaySearch("TestModel4", "Task top", "Exclusive Gateway Excl2", 0);
        testGatewaySearch("TestModel4", "Task t2", "Inclusive Gateway Inc1", 2);

        testFindAllBoundaryGateways("TestModel4", "Task t2", 2);
    }

    @Test
    public void testPartialRuleProcessing() {
        //testProcessPartialRules("TestModel4", "Exclusive Gateway Excl1", "Inclusive Gateway Inc1");
        testProcessPartialRules("TestModel1", "Exclusive Gateway Exclusive Gateway2", "Inclusive Gateway Inclusive Gateway2");
    }


}
