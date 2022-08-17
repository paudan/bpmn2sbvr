package org.ktu.model2sbvr.tests;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.tests.MagicDrawTestCase;
import com.nomagic.magicdraw.uml.Finder;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import org.ktu.model2sbvr.models.ConceptExtractionEntry;
import org.ktu.model2sbvr.models.SBVRExpressionModel;
import org.ktu.model2sbvr.models.SourceEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public abstract class ExtractionTestCase extends MagicDrawTestCase {

    protected static Project project = null;
    protected SessionManager sessionManager = SessionManager.getInstance();

    abstract protected Path getFilename();

    @Override
    protected void setUpTest() throws Exception {
        super.setUpTest();
        setSkipMemoryTest(true);
        setMemoryTestReady(false);
        Path filename = getFilename();
        if (filename != null && (project == null || !project.isLoaded()))
            project = openProject(filename.normalize().toUri().getPath());
        if (project == null || !project.isLoaded())
            throw new IOException("File " + filename + " was not opened or could not be found!");
        if (sessionManager.isSessionCreated())
            sessionManager.closeSession();
        sessionManager.createSession("Perform tests");
    }

    protected void endTest() {
        if (sessionManager.isSessionCreated())
            sessionManager.cancelSession();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Logger.getLogger(ExtractionTestCase.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    protected void printExtractorOutput(Map<SourceEntry, ConceptExtractionEntry> objects) {
        for(Entry<SourceEntry, ConceptExtractionEntry> item: objects.entrySet()) {
            List<String> outputs = new ArrayList<>();
            for (SBVRExpressionModel sbvr: item.getValue().getCandidates())
                outputs.add(sbvr.toString());
            System.out.println("Source: " + String.join(",", item.getKey().getSourceNames()) + " -> output: " + String.join(",", outputs));
        }
    }

    protected List<String> getOutputsAsStrings(Map<SourceEntry, ConceptExtractionEntry> objects, boolean outputSources) {
        List<String> results = new ArrayList<>();
        for (Entry<SourceEntry, ConceptExtractionEntry> item: objects.entrySet()) {
            List<String> outputs = new ArrayList<>();
            for (SBVRExpressionModel sbvr : item.getValue().getCandidates())
                if (outputSources)
                    outputs.add(sbvr.toString());
                else
                    results.add(sbvr.toString());
            if (outputSources)
                results.add("Source: " + String.join(",", item.getKey().getSourceNames()) + " -> output: " + String.join(",", outputs));
        }
        return results;
    }

    protected Element getElementByName(Collection<Element> root, String name, Class class_, String stereotype) {
        Element element = Finder.byNameRecursively().find(root, class_, name);
        if (element == null)
            return element;
        if (StereotypesHelper.hasStereotype(element, stereotype))
            return element;
        return null;
    }

    protected Element getElementByName(Package root, String name, Class class_, String stereotype) {
        Element element = Finder.byNameRecursively().find(root, class_, name);
        if (element == null)
            return element;
        if (StereotypesHelper.hasStereotype(element, stereotype))
            return element;
        return null;
    }

    protected Map<String, Map<SourceEntry, ConceptExtractionEntry>> objectsByRuleName(Map<SourceEntry, ConceptExtractionEntry> objects) {
        Map<String, Map<SourceEntry, ConceptExtractionEntry>> output = new TreeMap<>();
        for (Entry<SourceEntry, ConceptExtractionEntry> item: objects.entrySet()) {
            String rule = item.getKey().getRule();
            output.putIfAbsent(rule, new HashMap<>());
            output.get(rule).put(item.getKey(), item.getValue());
        }
        return output;
    }
}
