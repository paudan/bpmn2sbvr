package org.ktu.model2sbvr;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.Visitor;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.jmi.reflect.VisitorContext;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Association;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Constraint;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.OpaqueExpression;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.ValueSpecification;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ktu.model2sbvr.models.SBVRExpressionModel.RuleType;

public class StatsCollectionVisitor extends Visitor {

    private final Boolean strict;
    private Map<String, Map<String, Map<String, Integer[]>>> stats;
    private final String packageName, prefix;
    private String diagramName;

    private class StringComparator implements Comparator<String> {

        @Override
        public int compare(String o1, String o2) {
            Collator usCollator = Collator.getInstance(Locale.getDefault());
            usCollator.setStrength(Collator.PRIMARY);
            return usCollator.compare(o1, o2);
        }

    }

    public StatsCollectionVisitor(Boolean strict, Map<String, Map<String, Map<String, Integer[]>>> stats, String packageName, String prefix) {
        this.strict = strict;
        this.packageName = packageName;
        this.prefix = prefix;
        this.stats = stats;
    }

    public void setDiagramName(String diagramName) {
        this.diagramName = diagramName;
    }

    private void addStatsEntry(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package element, Integer[] eStats) {
        Map<String, Map<String, Integer[]>> modelEntry = stats.get(packageName);
        if (modelEntry == null) {
            modelEntry = new HashMap<>();
            stats.put(packageName, modelEntry);
        }
        Map<String, Integer[]> diagramEntry = modelEntry.get(diagramName);
        if (diagramEntry == null) {
            diagramEntry = new HashMap<>();
            modelEntry.put(diagramName, diagramEntry);
        }
        Integer[] statsEntry = diagramEntry.get(element.getName());
        if (statsEntry == null)
            diagramEntry.put(element.getName(), eStats);
    }

    @Override
    public void visitPackage(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package element, VisitorContext context) {
        String gcName = String.format("business_vocabulary_%s%s.txt", prefix, strict ? "_strict" : "");
        String mvName = String.format("model_vocabulary_%s%s.txt", prefix, strict ? "_strict" : "");
        String brName = String.format("business_rules_%s%s.txt", prefix, strict ? "_strict" : "");
        try (PrintWriter outgc = new PrintWriter(new BufferedWriter(new FileWriter(gcName, true)));
                PrintWriter outmv = new PrintWriter(new BufferedWriter(new FileWriter(mvName, true)));
                PrintWriter outbr = new PrintWriter(new BufferedWriter(new FileWriter(brName, true)))) {
            Project project = Application.getInstance().getProject();
            Profile profile = PluginUtilities.getCustomizationsProfile(project);
            Stereotype operativeSt = StereotypesHelper.getStereotype(project, "operative business rule", profile);
            Stereotype structuralSt = StereotypesHelper.getStereotype(project, "structural business rule", profile);
            if (element.getName().compareToIgnoreCase("SBVR Business Vocabulary") == 0)
                addStatsEntry(element, printVocabularyContents(outgc, element));
            else if (element.getName().compareToIgnoreCase("SBVR Model Vocabulary") == 0)
                addStatsEntry(element, printVocabularyContents(outmv, element));
            else if (element.getName().compareToIgnoreCase("SBVR Business Rules") == 0) {
                Integer[] stats_ = new Integer[3];
                Arrays.fill(stats_, 0);
                outbr.println();
                outbr.println("Model name: " + packageName);
                if (diagramName != null)
                    outbr.println("Diagram: " + diagramName);
                Set<String> items = new TreeSet<>(new StringComparator());
                for (Element el : element.getOwnedElement()) {
                    String name = el.getHumanName();
                    if (el instanceof Constraint) {
                        ValueSpecification spec = ((Constraint) el).getSpecification();
                        List<String> body = null;
                        if (spec != null && spec instanceof OpaqueExpression)
                            body = ((OpaqueExpression) spec).getBody();
                        else if (spec != null && spec.getExpression() != null && spec.getExpression() instanceof OpaqueExpression)
                            body = ((OpaqueExpression) spec.getExpression()).getBody();
                        if (body != null && !body.isEmpty())
                            name += " " + body.get(0);
                        if (operativeSt != null) {
                            if (name.startsWith(operativeSt.getName() + " " + RuleType.OBLIGATION.toString()))
                                stats_[0] += 1;
                            else if (name.startsWith(operativeSt.getName() + " " + RuleType.PERMISSION.toString()))
                                stats_[1] += 1;
                        } else if (structuralSt != null && StereotypesHelper.hasStereotype(el, structuralSt))
                            stats_[2] += 1;
                    }
                    items.add(name);
                }
                for (String str : items)
                    outbr.println(str);
                addStatsEntry(element, stats_);
            }
        } catch (IOException ex) {
            Logger.getLogger(StatsCollectionVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    protected Integer[] printVocabularyContents(PrintWriter writer, com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package element) {
        writer.println();
        writer.println("Model name: " + packageName);
        if (diagramName != null)
            writer.println("Diagram: " + diagramName);
        Set<String> gcItems = new TreeSet<>(new StringComparator()), 
                vcItems = new TreeSet<>(new StringComparator()), 
                ucItems = new TreeSet<>(new StringComparator());
        for (Element el : element.getOwnedElement())
            if (el instanceof Association && ((Association) el).getHumanName().startsWith("association")) {
                Association assoc = (Association) el;
                List<Property> ends = assoc.getMemberEnd();
                String name = ends.get(1).getType().getHumanName();
                name += " " + assoc.getName() + " ";
                name += ends.get(0).getType().getHumanName();
                vcItems.add(name);
            } else if (el instanceof Class) {
                gcItems.add(el.getHumanName());
                for (Property prop : ((Class) el).getOwnedAttribute())
                    if (prop.getHumanName().trim().length() > 0 && prop.getHumanName().startsWith("characteristic"))
                        ucItems.add(el.getHumanName() + " " + prop.getName());
            }
        for (String str : gcItems)
            writer.println(str);
        for (String str : vcItems)
            writer.println(str);
        for (String str : ucItems)
            writer.println(str);
        return new Integer[]{gcItems.size(), vcItems.size(), ucItems.size()};
    }

    public Map<String, Map<String, Map<String, Integer[]>>> getStats() {
        return stats;
    }

}
