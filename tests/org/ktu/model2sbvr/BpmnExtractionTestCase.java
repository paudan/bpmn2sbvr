package org.ktu.model2sbvr;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import org.ktu.model2sbvr.extract.BpmnSBVRExtractor;
import org.ktu.model2sbvr.tests.ExtractionTestCase;
import java.util.Collection;
import java.util.Optional;

public abstract class BpmnExtractionTestCase extends ExtractionTestCase {

    protected Package getRootPackage() {
        if (project == null)
            project = Application.getInstance().getProject();
        assertNotNull(project);
        return project.getPrimaryModel();
    }

    protected DiagramPresentationElement getDiagramElements(String modelName) {
        Package model = getRootPackage();
        assertNotNull(model);
        Collection<DiagramPresentationElement> diagrams = BpmnSBVRExtractor.getBPMNDiagrams(model);
        Optional<DiagramPresentationElement> diagramOpt = diagrams.stream()
                .filter(n -> n.getName() != null && n.getName().compareToIgnoreCase(modelName) == 0).findFirst();
        assertTrue(diagramOpt.isPresent());
        return diagramOpt.get();
    }

    protected BpmnSBVRExtractor getExtractor(String modelName) {
        DiagramPresentationElement diagram = getDiagramElements(modelName);
        assertNotNull(diagram);
        BpmnSBVRExtractor extractor = new BpmnSBVRExtractor(diagram, false, false);
        extractor.extractAll();
        return extractor;
    }

}
