package org.ktu.model2sbvr.extract;

import com.nomagic.magicdraw.cbm.BPMNHelper;
import com.nomagic.magicdraw.cbm.profiles.BPMN2Profile;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.actions.mdcompleteactions.AcceptEventAction;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityEdge;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ControlNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.ActivityPartition;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.CentralBufferNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.auxiliaryconstructs.mdinformationflows.InformationFlow;
import com.nomagic.uml2.ext.magicdraw.classes.mddependencies.Dependency;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Comment;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.EnumerationLiteral;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Relationship;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Type;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.ktu.model2sbvr.PluginUtilities;
import org.ktu.model2sbvr.models.SBVRExpressionModel;
import org.ktu.model2sbvr.models.SBVRExpressionModel.Bracket;
import org.ktu.model2sbvr.models.SBVRExpressionModel.Conditional;
import org.ktu.model2sbvr.models.SBVRExpressionModel.Conjunction;
import org.ktu.model2sbvr.models.SBVRExpressionModel.RuleType;

/**
 * @author Paulius Danenas, 2019
 */
public class BpmnSBVRExtractor extends AbstractSBVRExtractor {

    private Project project;
    private Profile bpmnProfile;

    private static String[] activityStereotypes = {"SubProcess", "Transaction", "AdHocSubProcess"};
    private static String[] boundaryStereotypes = {"MessageBoundaryEvent", "ErrorBoundaryEvent",  "TimerBoundaryEvent",
            "EscalationBoundaryEvent", "CancelBoundaryEvent", "CompensationBoundaryEvent", "ConditionalBoundaryEvent",
            "SignalBoundaryEvent", "MultipleBoundaryEvent", "ParallelMultipleBoundaryEvent"};

    Map<ControlNode, GatewayNeighborhood> gatewayNeighborhoods, gatewayNeighborhoods2;


    class ActivityNodeNeighborhood {
        ActivityNode activityNode;
        String activityText;
        Map<Element, String> activitySubjects;
        Map<ActivityNode, Map<ActivityEdge, String>> incomingConditions, outgoingConditions;
        Map<ActivityNode, Map<ActivityEdge, String>> correctionsIncoming, correctionsOutgoing;
        private Map<ActivityNode, Integer> nullCountIncoming, nullCountOutgoing;
        int nullsTotalIncoming, nullsTotalOutgoing;

        private ActivityNodeNeighborhood(ActivityNode node) {
            this.activityNode = node;
            this.activityText = getActivityText(node);
            activitySubjects = getSubjectNames(node, false);
            incomingConditions = new HashMap<>();
            nullCountIncoming = new HashMap<>();
            for (ActivityEdge edge : node.getIncoming())
                addCondition(incomingConditions, nullCountIncoming, edge, edge.getSource());
            outgoingConditions = new HashMap<>();
            nullCountOutgoing = new HashMap<>();
            for (ActivityEdge edge : node.getOutgoing())
                addCondition(outgoingConditions, nullCountOutgoing, edge, edge.getTarget());
            // Resolve cases when multiple sequence flows are incoming/outgoing from the same node, with contradictions
            correctionsIncoming = createCorrections(cloneConditions(incomingConditions), nullCountIncoming);
            correctionsOutgoing = createCorrections(cloneConditions(outgoingConditions), nullCountOutgoing);
        }

        private Map<ActivityNode, Map<ActivityEdge, String>> cloneConditions(Map<ActivityNode, Map<ActivityEdge, String>> conditions) {
            Map<ActivityNode, Map<ActivityEdge, String>> cloned = new HashMap<>();
            for (Entry<ActivityNode, Map<ActivityEdge, String>> nodeEntry : conditions.entrySet()) {
                Map<ActivityEdge, String> copy = new HashMap<>();
                for (Entry<ActivityEdge, String> condEl : nodeEntry.getValue().entrySet())
                    copy.put(condEl.getKey(), condEl.getValue());
                cloned.put(nodeEntry.getKey(), copy);
            }
            return cloned;
        }

        private void addCondition(Map<ActivityNode, Map<ActivityEdge, String>> conditions,
                                  Map<ActivityNode, Integer> nullCounts, ActivityEdge edge, ActivityNode node) {
            String condition = getCondition(edge);
            nullCounts.putIfAbsent(node, 0);
            if (condition == null) {
                nullCounts.put(node, nullCounts.get(node) + 1);
                if (conditions == this.incomingConditions)
                    nullsTotalIncoming += 1;
                else
                    nullsTotalOutgoing += 1;
            }
            Map<ActivityEdge, String> condList = conditions.get(node);
            if (condList == null) {
                condList = new HashMap<>();
                conditions.put(node, condList);
            }
            condList.put(edge, condition);
        }

        private Map<ActivityNode, Map<ActivityEdge, String>> createCorrections(Map<ActivityNode, Map<ActivityEdge, String>> conditions,
                                                                               Map<ActivityNode, Integer> nullCounts) {
            for (Entry<ActivityNode, Integer> nodeEntry : nullCounts.entrySet()) {
                Integer nullStats = nodeEntry.getValue();
                Map<ActivityEdge, String> condList = conditions.get(nodeEntry.getKey());
                if (nullStats >= 1 && condList != null && condList.size() > 1)
                    // We have contradictions, transition condition is undefined
                    condList.clear();
            }
            return conditions;
        }

        private String formatPadding(String str) {
            return StringUtils.removeEnd(str, "\n").replaceAll("\n", "\n\t");
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Activity element:").append(activityNode.getHumanName()).append("\n")
                    .append("Extracted text: ").append(activityText).append("\n");
            Set<Element> elements = activitySubjects.keySet().stream().filter(Objects::nonNull).collect(Collectors.toSet());
            if (elements.size() > 0) {
                sb.append("Subjects (executing elements): ");
                sb.append(elements.stream().map(Element::getHumanName).collect(Collectors.joining(",")));
            }
            if (!incomingConditions.isEmpty()) {
                sb.append("\nConditions from incoming edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(incomingConditions)));
            }
            if (!outgoingConditions.isEmpty()) {
                sb.append("\nConditions from outgoing edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(outgoingConditions)));
            }
            if (!correctionsIncoming.isEmpty()) {
                sb.append("\nConditions from incoming edges after resolving default conditions:\n");
                sb.append(formatPadding(getConditionsRepresentation(correctionsIncoming)));
            }
            if (!correctionsOutgoing.isEmpty()) {
                sb.append("\nConditions from outgoing edges after resolving default conditions:\n");
                sb.append(formatPadding(getConditionsRepresentation(correctionsOutgoing)));
            }
            if (!nullCountIncoming.isEmpty()) {
                sb.append("\nNumber of incoming edges with null conditions:\n");
                for (Entry<ActivityNode, Integer> nullEntry: nullCountIncoming.entrySet())
                    sb.append("\t").append(nullEntry.getKey().getHumanName()).append(": ").append(nullEntry.getValue()).append("\n");
            }
            if (!nullCountOutgoing.isEmpty()) {
                sb.append("Number of outgoing edges with null conditions: ").append("\n");
                for (Entry<ActivityNode, Integer> nullEntry: nullCountOutgoing.entrySet())
                    sb.append(nullEntry.getKey().getHumanName()).append(": ").append(nullEntry.getValue()).append("\n");
            }
            return sb.toString().trim();
        }
    }

    class GatewayNeighborhood {
        ControlNode gatewayNode;
        Map<ActivityNode, ActivityNodeNeighborhood> incomingActivities, outgoingActivities;
        Map<ActivityNode, ActivityEdge> outgoingDefault;
        Map<ActivityNode, Map<ActivityEdge, String>> incomingConditions, outgoingConditions;
        private Map<ActivityNode, Integer> nullCountIncoming, nullCountOutgoing;
        Map<ControlNode, GatewayNeighborhood> incomingGateways, outgoingGateways;
        int nullsTotalIncoming, nullsTotalOutgoing;
        SBVRExpressionModel partialRule;
        List<Object> partialRuleSource;

        public GatewayNeighborhood(ControlNode gatewayNode, boolean fillIncoming) {
            this.gatewayNode = gatewayNode;
            incomingActivities = new HashMap<>();
            outgoingActivities = new HashMap<>();
            incomingGateways = new HashMap<>();
            outgoingGateways = new HashMap<>();
            incomingConditions = new HashMap<>();
            outgoingConditions = new HashMap<>();
            nullCountIncoming = new HashMap<>();
            nullCountOutgoing = new HashMap<>();
            outgoingDefault = new HashMap<>();
            partialRule = new SBVRExpressionModel();
            partialRuleSource = new ArrayList<>();
            for (ActivityEdge edge : gatewayNode.getIncoming()) {
                ActivityNode srcElement = edge.getSource();
                if (srcElement == null)
                    continue;
                addIncomingCondition(edge, srcElement);
                if (isActivityElement(srcElement) || isEventElement(srcElement)) {
                    ActivityNodeNeighborhood taskTuple = new ActivityNodeNeighborhood(srcElement);
                    incomingActivities.put(taskTuple.activityNode, taskTuple);
                } else if (isGatewayElement(srcElement)) {
                    ControlNode node = (ControlNode) srcElement;
                    GatewayNeighborhood nnode = null;
                    if (fillIncoming)
                        nnode = new GatewayNeighborhood(node, fillIncoming);
                    incomingGateways.put(node, nnode);
                }
            }
            outgoingActivities = new HashMap<>();
            for (ActivityEdge edge : gatewayNode.getOutgoing()) {
                ActivityNode targetElement = edge.getTarget();
                if (targetElement == null)
                    continue;
                if (BPMNHelper.isDefaultSequenceFlow(gatewayNode, edge))
                    outgoingDefault.put(targetElement, edge);
                else
                    addOutgoingCondition(edge, targetElement);
                if (isActivityElement(targetElement) || isEventElement(targetElement)) {
                    ActivityNodeNeighborhood taskTuple = new ActivityNodeNeighborhood(targetElement);
                    outgoingActivities.put(taskTuple.activityNode, taskTuple);
                } else if (isGatewayElement(targetElement)) {
                    ControlNode node = (ControlNode) targetElement;
                    GatewayNeighborhood nnode = null;
                    if (!fillIncoming)
                        nnode = new GatewayNeighborhood(node, fillIncoming);
                    outgoingGateways.put(node, nnode);
                }
            }
        }

        private void addIncomingCondition(ActivityEdge edge, ActivityNode node) {
            String condition = getCondition(edge);
            nullCountIncoming.putIfAbsent(node, 0);
            if (condition == null) {
                nullCountIncoming.put(node, nullCountIncoming.get(node) + 1);
                nullsTotalIncoming += 1;
            }
            Map<ActivityEdge, String> condList = incomingConditions.get(node);
            if (condList == null) {
                condList = new HashMap<>();
                incomingConditions.put(node, condList);
            }
            condList.put(edge, condition);
        }

        private void addOutgoingCondition(ActivityEdge edge, ActivityNode node) {
            String condition = getCondition(edge);
            nullCountOutgoing.putIfAbsent(node, 0);
            if (condition == null) {
                nullCountOutgoing.put(node, nullCountOutgoing.get(node) + 1);
                nullsTotalOutgoing += 1;
            }
            Map<ActivityEdge, String> condList = outgoingConditions.get(node);
            if (condList == null) {
                condList = new HashMap<>();
                outgoingConditions.put(node, condList);
            }
            condList.put(edge, condition);
        }

        private String formatPadding(String str) {
            return StringUtils.removeEnd(str, "\n").replaceAll("\n", "\n\t");
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Gateway element:").append(gatewayNode.getHumanName());
            sb.append("\n");
            if (!incomingActivities.isEmpty()) {
                sb.append("Incoming activity nodes:\n");
                for (ActivityNodeNeighborhood taskNode: incomingActivities.values())
                    sb.append("\t").append(formatPadding(taskNode.toString())).append("\n\n");
            }
            if (!outgoingActivities.isEmpty()) {
                sb.append("Outgoing activity nodes:\n");
                for (ActivityNodeNeighborhood taskNode: outgoingActivities.values())
                    sb.append("\t").append(formatPadding(taskNode.toString())).append("\n\n");
            }
            if (!incomingGateways.isEmpty()) {
                sb.append("Incoming gateway nodes:\n");
                for (Entry<ControlNode, GatewayNeighborhood> gatewayNode: incomingGateways.entrySet()) {
                    sb.append("\t").append("Element: ").append(gatewayNode.getKey().getHumanName()).append("\n");
                    if (gatewayNode.getValue() != null)
                        sb.append("\t").append(formatPadding(gatewayNode.getValue().toString())).append("\n\n");
                }
            }
            if (!outgoingGateways.isEmpty()) {
                sb.append("Outgoing gateway nodes:\n");
                for (Entry<ControlNode, GatewayNeighborhood> gatewayNode: outgoingGateways.entrySet()) {
                    sb.append("\t").append("Element: ").append(gatewayNode.getKey().getHumanName()).append("\n");
                    if (gatewayNode.getValue() != null)
                        sb.append("\t").append(formatPadding(gatewayNode.getValue().toString())).append("\n\n");
                }
            }
            if (!incomingConditions.isEmpty()) {
                sb.append("Conditions from incoming edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(incomingConditions)));
                sb.append("\n");
            }
            if (!outgoingConditions.isEmpty()) {
                sb.append("Conditions from outgoing edges:\n");
                sb.append(formatPadding(getConditionsRepresentation(outgoingConditions)));
                sb.append("\n");
            }
            if (!nullCountIncoming.isEmpty()) {
                sb.append("Number of incoming edges with null conditions:\n");
                for (Entry<ActivityNode, Integer> nullEntry: nullCountIncoming.entrySet())
                    sb.append("\t").append(nullEntry.getKey().getHumanName()).append(": ").append(nullEntry.getValue()).append("\n");
                sb.append("\n");
            }
            if (!nullCountOutgoing.isEmpty()) {
                sb.append("Number of outgoing edges with null conditions:\n");
                for (Entry<ActivityNode, Integer> nullEntry: nullCountOutgoing.entrySet())
                    sb.append("\t").append(nullEntry.getKey().getHumanName()).append(": ").append(nullEntry.getValue()).append("\n");
                sb.append("\n");
            }
            if (!partialRule.isEmpty())
                sb.append("Partial rule:\n").append(partialRule);
            return sb.toString().trim();
        }
    }


    public BpmnSBVRExtractor(DiagramPresentationElement diagram, boolean strictOnly, boolean extractMMVoc) {
        super(diagram, strictOnly, extractMMVoc);
        setProfile();
        extractGatewayNeighborhoods();
    }

    public BpmnSBVRExtractor(Package model, boolean strictOnly, boolean extractMMVoc) {
        super(model, strictOnly, extractMMVoc);
        setProfile();
        extractGatewayNeighborhoods();
    }

    private void setProfile() {
        project = Application.getInstance().getProject();
        bpmnProfile = PluginUtilities.getBPMNProfile(project);
    }

    boolean hasAnyStereotype(Element el, String... stereotypes) {
        if (el == null)
            return false;
        for (String st : stereotypes)
            if (StereotypesHelper.hasStereotype(el, StereotypesHelper.getStereotype(project, st, bpmnProfile)))
                return true;
        return false;
    }

    private Stereotype getActivityStereotype(Element el) {
        if (el == null)
            return null;
        if (isTaskElement(el))
            return BPMNHelper.getTaskStereotype(el);
        List<String> stereotypes = new ArrayList<>(Collections.singleton("CallActivity"));
        stereotypes.addAll(Arrays.asList(activityStereotypes));
        for (String st : stereotypes) {
            Stereotype stereotype = StereotypesHelper.getStereotype(project, st, bpmnProfile);
            if (StereotypesHelper.hasStereotype(el, stereotype))
                return stereotype;
        }
        return null;
    }

    private boolean isTaskElement(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isTask(el);
    }

    private boolean isActivityElement(Element el) {
        if (el == null)
            return false;
        return isTaskElement(el) || BPMN2Profile.isCallActivity(el) || BPMN2Profile.isSubProcess(el);
    }

    private boolean isEventElement(Element el) {
        return isStartEventElement(el) || isBoundaryEvent(el) || isIntermediaryEvent(el) ||
               isEndEventElement(el) || isTimerEvent(el);
    }

    private boolean isStartEventElement(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isStartEvent(el);
    }

    private boolean isTimerEvent(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isTimerBoundaryEvent(el) || BPMN2Profile.isTimerCatchIntermediateEvent(el);
    }

    private boolean isBoundaryEvent(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isBoundaryEvent(el);
    }

    private boolean isIntermediaryEvent(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isIntermediateCatchEvent(el) || BPMN2Profile.isIntermediateThrowEvent(el);
    }

    private boolean isEndEventElement(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isEndEvent(el);
    }

    private boolean isDataObject(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isDataObject(el) || BPMN2Profile.isDataInput(el) || BPMN2Profile.isDataOutput(el);
    }

    boolean isGatewayElement(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isExclusiveGateway(el) || BPMN2Profile.isInclusiveGateway(el) ||
               BPMN2Profile.isParallelGateway(el) || BPMN2Profile.isEventBasedGateway(el);
    }

    private boolean isSequenceFlow(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isSequenceFlow(el);
    }

    private boolean isLaneElement(Element el) {
        if (el == null)
            return false;
        return BPMN2Profile.isLane(el) || BPMN2Profile.isLaneSet(el);
    }

    private Stereotype getStereotypeInList(Element el, String[] stereotypesList) {
        return getStereotypeInList(el, stereotypesList, project, bpmnProfile);
    }

    String getCondition(ActivityEdge el) {
        String cond = getCondition(el.getGuard());
        if (cond != null && cond.trim().length() > 0)
            return cond;
        cond = el.getName().trim();
        return cond.length() > 0 ? cond : null;
    }


    private Collection<ActivityPartition> getInPartition(ActivityNode node) {
        if (node == null)
            return null;
        if (!node.hasInPartition())
            return getInPartition(node.getInStructuredNode());
        return node.getInPartition();
    }

    private Collection<ActivityPartition> getInPartition(ActivityEdge node) {
        if (node == null)
            return null;
        if (!node.hasInPartition())
            return getInPartition(node.getInStructuredNode());
        return node.getInPartition();
    }

    private Map<Element, String> getSubjectNames(Element element, boolean getEventSubjects) {
        Map<Element, String> names = new HashMap<>();
        // Should be modelled as unary concepts
        if (!getEventSubjects && isEventElement(element)) {
            // Add null values to allow for single step in loops
            names.put(null, null);
            return names;
        }
        Collection<ActivityPartition> parts = null;
        if (element instanceof ActivityNode)
            parts = getInPartition((ActivityNode) element);
        else if (element instanceof ActivityEdge)
            parts = getInPartition((ActivityEdge) element);
        else if (isLaneElement(element))
            parts = Collections.singleton((ActivityPartition) element);
        if (parts == null)
            return names;
        // Select lowest level partitions as subjects
        parts = parts.stream().filter(n -> !n.hasSubpartition()).collect(Collectors.toSet());
        for (ActivityPartition part : parts) {
            Element subject = part.getRepresents() != null ? part.getRepresents() : part;
            String name = extractElementText(subject);
            if (name == null)
                continue;
            names.put(subject, name);
        }
        return names;
    }

    private String getLaneName(ActivityPartition element) {
        Element subject = element.getRepresents() != null ? element.getRepresents() : element;
        return extractElementText(subject);
    }

    private String getDataObjectName(Element dataObject) {
        if (dataObject == null)
            return null;
        String name = extractElementText(dataObject);
        if ((name == null || name.trim().length() == 0) && dataObject instanceof CentralBufferNode) {
            Type dataType = ((CentralBufferNode)dataObject).getType();
            if (dataType != null)
                return dataType.getName();
        }
        return name;
    }

    private String getActivityText(ActivityNode el) {
        if (el == null)
            return null;
        if (isStartEventElement(el)){
            String name = extractElementText(el);
            return name == null ? "Process starts" : name;
        } else if (isEndEventElement(el)){
            String name = extractElementText(el);
            return name == null ? "Process ends" : name;
        } else if (isTimerEvent(el)) {
            String name = extractElementText(el);
            if (name != null)
                return name + " has passed";
        } else
            return extractElementText(el);
        return null;
    }

    private String extractActivityGC(ActivityNode el) {
        if (el == null)
            return null;
        if (isStartEventElement(el)){
            String name = extractActionUnaryLikeGC(el);
            if (name == null) {
                Activity owner = el.getActivity();
                if (owner != null) {
                    String extracted = extractElementText(owner);
                    if (extracted != null)
                        return extracted;
                }
            } else
                return name;
        } else if (isEndEventElement(el)){
            String name = extractActionUnaryLikeGC(el);
            if (name == null) {
                Activity owner = el.getActivity();
                if (owner != null) {
                    String extracted = extractElementText(owner);
                    if (extracted != null)
                        return extracted;
                }
            } else
                return name;
        } else if (isTimerEvent(el))
            return extractElementText(el);
        else
            return extractActionGC(el);
        return null;
    }

    private SBVRExpressionModel addLaneGeneralConcept(SBVRExpressionModel candidate, ActivityPartition element) {
        String objText = getLaneName(element);
        SBVRExpressionModel objConcept = getGeneralConcept(objText);
        return objConcept != null ? candidate.addIdentifiedExpression(objConcept) : candidate.addUnidentifiedText(objText);
    }


    @Override
    protected void extractGeneralConceptCandidates() {
        Iterator<Element> iterator = candidateElements.iterator();
        while (iterator.hasNext()) {
            Element el = iterator.next();
            if (isLaneElement(el) || BPMN2Profile.isResource(el))
                createGeneralConcept(el, extractElementText(el), true, true);
            else if (BPMN2Profile.isMessageFlow(el)) {
                Collection<Classifier> conveyed = ((InformationFlow) el).getConveyed();
                for (Classifier classifier : conveyed)
                    if (classifier.getClassType().equals(com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class.class) && BPMN2Profile.isBPMNMessage(classifier))
                        createGeneralConcept(classifier, extractElementText(classifier), true, true);
            } else if ((isActivityElement(el) || isEventElement(el)) && !extractedAuto)
                createGeneralConcept(el, extractActivityGC((ActivityNode) el), false, false);
            else if (isSequenceFlow(el)) {
                String condition = getCondition((ActivityEdge) el);
                if (condition != null)
                    gc_candidates.setManualExtraction(new MagicDrawSourceEntry(Collections.singletonList(el)));
            } else if (isEventElement(el) && extractElementText(el) != null)
                gc_candidates.setManualExtraction(new MagicDrawSourceEntry(Collections.singletonList(el)));
            else if (isDataObject(el) || BPMN2Profile.isDataStore(el)) {
                Collection<State> states = ((CentralBufferNode) el).getInState();
                if (!states.isEmpty())
                    for (State state : states) {
                        String stateText = extractElementText(state);
                        String elText = getDataObjectName(el);
                        if (stateText != null && elText != null)
                            createGeneralConcept(el, stateText + " " + elText, true, true);
                    }
                else
                    createGeneralConcept(el, getDataObjectName(el), true, true);
            }
            if (BPMN2Profile.isMessageFlow(el)) {
                for (Classifier convObj : ((InformationFlow) el).getConveyed())
                    if (BPMN2Profile.isBPMNMessage(convObj))
                        createGeneralConcept(el, extractElementText(el), false, true);
            } else if (el.getClassType().equals(Comment.class))
                if (extractElementText(el) != null)
                    gc_candidates.setManualExtraction(new MagicDrawSourceEntry(Collections.singletonList(el)));
        }
    }

    @Override
    protected void extractVerbConceptCandidates() {
        Iterator<Element> iterator = candidateElements.iterator();
        while (iterator.hasNext()) {
            Element el = iterator.next();
            if (isActivityElement(el) && !isEventElement(el) && !strictOnly) {
                Map<Element, String> subjects = getSubjectNames(el, false);
                for (Entry<Element, String> subject: subjects.entrySet())
                    createVerbConceptFromAction(subject.getKey(), el);
            } else if (isEventElement(el)) {
                if (isStartEventElement(el))
                    createUnaryVerbConcept(el, "starts", extractActivityGC((ActivityNode) el));
                else if (isEndEventElement(el))
                    createUnaryVerbConcept(el, "ends", extractActivityGC((ActivityNode) el));
                else if (isTimerEvent(el))
                    createUnaryVerbConcept(el, "has passed", extractActivityGC((ActivityNode) el));
                else
                    createVerbConceptFromCondition(el, extractElementText(el));
            } else if (isEventElement(el) && !strictOnly) {
                createVerbConceptFromCondition(el, extractElementText(el));
                vc_candidates.setManualExtraction(new MagicDrawSourceEntry(Collections.singletonList(el)));
            } else if (isSequenceFlow(el) && !strictOnly)
                createVerbConceptFromCondition(el, getCondition((ActivityEdge) el));
            else if (isDataObject(el) || BPMN2Profile.isDataStore(el))
                for (State state : ((CentralBufferNode) el).getInState())
                    createUnaryVerbConcept(el, state);
            else if (el.getClassType().equals(Comment.class) && extractElementText(el) != null && !strictOnly)
                vc_candidates.setManualExtraction(new MagicDrawSourceEntry(Collections.singletonList(el)));
        }
    }

    @Override
    protected void extractBusinessRuleCandidates() {
        Iterator<Element> iterator = candidateElements.iterator();
        while (iterator.hasNext()) {
            Element el = iterator.next();
            extractRuleT1(el);
            extractRuleT2(el);
            extractRuleT3(el);
            extractRuleT4(el);
            extractRuleT5(el);
            extractRuleT6(el);
            extractRuleT7(el);
            extractRuleT8(el);
            extractRuleT9(el);
            extractComplexRule(el);
        }
    }

    private SBVRExpressionModel addActivity(SBVRExpressionModel model, ActivityNode activity, String subject) {
        if (activity == null)
            return model;
        String taskText = getActivityText(activity);
        if (!isEventElement(activity))
            taskText = subject + " " + taskText;
        SBVRExpressionModel binary2 = getVerbConcept(taskText);
        return binary2 != null ? model.addIdentifiedExpression(binary2) : model.addUnidentifiedText(taskText);
    }

    private SBVRExpressionModel addCondition(SBVRExpressionModel model, String condition) {
        if (condition == null)
            return model;
        condition = condition.replaceAll("\n", " ").replaceAll("_", " ").replaceAll("  ", " ").trim();
        SBVRExpressionModel binary2 = getVerbConcept(condition);
        return binary2 != null ? model.addIdentifiedExpression(binary2) : model.addUnidentifiedText(condition);
    }

    private SBVRExpressionModel createMultipleConditions(Map<ActivityEdge, String> conditions, List<Object> objects) {
        SBVRExpressionModel model = new SBVRExpressionModel();
        if (conditions == null || conditions.isEmpty())
            return model;
        boolean added_or_first = true;
        Set<String> strConditions = conditions.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (strConditions.isEmpty())
            return model;
        for (Entry<ActivityEdge, String> cond : conditions.entrySet()) {
            if (!added_or_first)
                model.addConjunction(Conjunction.OR);
            else
                added_or_first = false;
            String condition = cond.getValue();
            if (condition != null) {
                model = addCondition(model, cond.getValue());
                if (objects != null)
                    objects.add(cond.getKey());
            }
        }
        return model;
    }

    private Conjunction getGatewayConjunction(ControlNode gateway) {
        if (!isGatewayElement(gateway))
            return null;
        if (BPMN2Profile.isExclusiveGateway(gateway) || BPMN2Profile.isEventBasedGateway(gateway))
            return Conjunction.OR;
        else if (BPMN2Profile.isInclusiveGateway(gateway) || BPMN2Profile.isParallelGateway(gateway))
            return Conjunction.AND;
        return null;
    }

    private void extractRuleT1(Element el) {
        if (!isSequenceFlow(el))
            return;
        ActivityNode before = ((ActivityEdge) el).getSource();
        ActivityNode after = ((ActivityEdge) el).getTarget();
        if (getActivityText(before) == null || getActivityText(after) == null)
            return;
        if (!((isActivityElement(before) || isStartEventElement(before) || isIntermediaryEvent(before)) &&
              (isActivityElement(after) || isEndEventElement(after) || isIntermediaryEvent(after))))
            return;
        Map<Element, String> subjectsFrom = getSubjectNames(before, false);
        Map<Element, String> subjectsTo = getSubjectNames(after, false);
        for (Entry<Element, String> subjectFrom : subjectsFrom.entrySet()) {
            for (Entry<Element, String> subjectTo : subjectsTo.entrySet()) {
                SBVRExpressionModel candidate = new SBVRExpressionModel()
                        .addRuleExpression(SBVRExpressionModel.RuleType.OBLIGATION);
                candidate = addActivity(candidate, after, subjectTo.getValue());
                candidate = candidate.addRuleConditional(Conditional.AFTER);
                candidate = addActivity(candidate, before, subjectFrom.getValue());
                String incomingCondition = getCondition((ActivityEdge) el);
                if (incomingCondition != null) {
                    candidate = candidate.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF);
                    candidate = addCondition(candidate, incomingCondition);
                }
                List<Object> src = new ArrayList<>();
                if (subjectFrom.getKey() != null)
                    src.add(subjectFrom.getKey());
                src.add(after);
                if (subjectFrom.getKey() != null)
                    src.add(subjectFrom.getKey());
                src.add(before);
                MagicDrawSourceEntry source = new MagicDrawSourceEntry(src, "T1");
                br_candidates.add(source, candidate);
                br_candidates.setAutomaticExtraction(source);
            }
        }
    }

    private void extractRuleT2(Element el) {
        extractRulesWithGatewaysSimplified(el, BPMN2Profile.isExclusiveGateway(el), Conjunction.OR, "T2");
        //extractRuleWithGateways(el, BPMN2Profile.isExclusiveGateway(el), Conjunction.OR);
    }

    private void extractRuleT3(Element el) {
        extractRulesWithGatewaysSimplified(el, BPMN2Profile.isInclusiveGateway(el), Conjunction.AND, "T3");
        //extractRuleWithGateways(el, BPMN2Profile.isInclusiveGateway(el), Conjunction.AND);
    }

    private void extractRulesWithGatewaysSimplified(Element el, boolean checkGatewayCondition, Conjunction conjunction, String rule) {
        if (!checkGatewayCondition)
            return;
        GatewayNeighborhood tuple = gatewayNeighborhoods.get(el);
        if (tuple.incomingActivities.isEmpty() && tuple.outgoingActivities.isEmpty())
            return;
        if (!tuple.outgoingActivities.isEmpty()) {
            //Calculate joint conditions for exclusion by negation, if no conditions are set
            SBVRExpressionModel jointConditions = new SBVRExpressionModel();
            boolean first_added = true;
            for (Map<ActivityEdge, String> outEntry: tuple.outgoingConditions.values()) {
                for (String condition: outEntry.values())
                    if (condition != null && condition.trim().length() > 0) {
                        if (!first_added)
                            jointConditions.addConjunction(Conjunction.OR);
                        else
                            first_added = false;
                        jointConditions = addCondition(jointConditions, condition);
                    }
            }
            for (Entry<ActivityNode, ActivityNodeNeighborhood> actOut: tuple.outgoingActivities.entrySet()) {
                Map<Element, String> subjectsOut = actOut.getValue().activitySubjects;
                for (Entry<Element, String> subjectOut: subjectsOut.entrySet()) {
                    if (tuple.incomingActivities.isEmpty())
                        continue;
                    SBVRExpressionModel partial = new SBVRExpressionModel();
                    List<Object> objects = new ArrayList<>();
                    objects.add(actOut.getKey());
                    if (subjectOut.getKey() != null)
                        objects.add(subjectOut.getKey());
                    partial = addActivity(partial, actOut.getKey(), subjectOut.getValue());
                    partial.addRuleConditional(Conditional.AFTER);
                    first_added = true;
                    for (Entry<ActivityNode, ActivityNodeNeighborhood> incNode: tuple.incomingActivities.entrySet()) {
                        Map<Element, String> subjectsIn = incNode.getValue().activitySubjects;
                        for (Entry<Element, String> subjectIn: subjectsIn.entrySet()) {
                            if (!first_added)
                                partial.addConjunction(conjunction);
                            else
                                first_added = false;
                            partial = addActivity(partial, incNode.getKey(), subjectIn.getValue());
                            if (subjectIn.getKey() != null)
                                objects.add(subjectIn.getKey());
                            SBVRExpressionModel conditionModel = createMultipleConditions(incNode.getValue().outgoingConditions.get(el), objects);
                            if (!conditionModel.isEmpty())
                                partial.addRuleConditional(Conditional.IF).addIdentifiedExpression(conditionModel);
                        }
                    }
                    SBVRExpressionModel conditionModel = createMultipleConditions(actOut.getValue().incomingConditions.get(el), objects);
                    SBVRExpressionModel candidate = new SBVRExpressionModel();
                    if (!conditionModel.isEmpty()) {
                        candidate.addRuleExpression(RuleType.OBLIGATION)
                                .addIdentifiedExpression(partial)
                                .addConjunction(Conjunction.AND)
                                .addRuleConditional(Conditional.IF)
                                .addIdentifiedExpression(conditionModel);
                    } else {
                        RuleType ruleType = RuleType.OBLIGATION;
                        // Check if activity has no default incoming sequence flows from exclusive gateway; otherwise, set rule type to "PERMISSION"
                        if (BPMN2Profile.isExclusiveGateway(el) && !tuple.outgoingDefault.keySet().contains(actOut.getKey()))
                                ruleType = RuleType.PERMISSION;
                        candidate.addRuleExpression(ruleType).addIdentifiedExpression(partial);
                        if (!jointConditions.isEmpty())
                            candidate.addConjunction(Conjunction.AND)
                                    .addRuleConditional(Conditional.IF_NOT)
                                    .addBracket(Bracket.LEFT)
                                    .addIdentifiedExpression(jointConditions)
                                    .addBracket(Bracket.RIGHT);
                    }
                    MagicDrawSourceEntry source = new MagicDrawSourceEntry(objects, rule);
                    br_candidates.add(source, candidate);
                    br_candidates.setAutomaticExtraction(source);
                }
            }
        }
    }

    private SBVRExpressionModel addTasksWithConditions(SBVRExpressionModel candidate, Map<ActivityNode, ActivityNodeNeighborhood> tasksData,
                                                       boolean incomingActivities, List<Object> objects, Conjunction conjunction, ControlNode gate) {
        List<Object> tasksDefault = new ArrayList<>();
        boolean added_first = true;
        boolean rules_added = false;
        for (Entry<ActivityNode, ActivityNodeNeighborhood> entryOut : tasksData.entrySet()) {
            Map<Element, String> subjectsOut = entryOut.getValue().activitySubjects;
            Map<ActivityNode, Map<ActivityEdge, String>> conditionsOut = entryOut.getValue().incomingConditions;
            Map<ActivityEdge, String> gateConditions = conditionsOut.get(gate);
            if (gateConditions != null) {
                for (Entry<ActivityEdge, String> cond : gateConditions.entrySet())
                    if (cond.getValue() == null)
                        gateConditions.remove(cond.getKey());
            }
            for (Entry<Element, String> subjectOut : subjectsOut.entrySet()) {
                // Add verb concept from rule and subject (lane, resource, etc.)
                if (subjectOut.getKey() != null)
                    objects.add(subjectOut.getKey());
                objects.add(entryOut.getValue().activityNode);
                if (gateConditions != null && !gateConditions.isEmpty()) {
                    if (!added_first)
                        candidate.addUnidentifiedText(",").addConjunction(conjunction);
                    else
                        added_first = false;
                    candidate = addActivity(candidate, entryOut.getValue().activityNode, subjectOut.getValue());
                    // Add verb concepts from conditions
                    SBVRExpressionModel conditionModel = createMultipleConditions(gateConditions, objects);
                    if (!conditionModel.isEmpty())
                        candidate.addConjunction(Conjunction.AND)
                                .addRuleConditional(Conditional.IF)
                                .addIdentifiedExpression(conditionModel);
                    rules_added = true;
                } else {
                    // No conditions are present, process as default
                    String outTaskText = extractElementText(entryOut.getValue().activityNode);
                    String vbTextRepr = subjectOut.getValue() != null ? (subjectOut.getValue() + " " + outTaskText) : outTaskText;
                    if (vbTextRepr != null) {
                        SBVRExpressionModel taskModel = getVerbConcept(vbTextRepr);
                        tasksDefault.add(taskModel != null ? taskModel : vbTextRepr);
                    }
                }
            }
        }
        if (!tasksDefault.isEmpty()) {
            if (rules_added)
                candidate.addUnidentifiedText(",").addRuleConditional(Conditional.OTHERWISE);
            added_first = true;
            // If we have inclusive converging gateway, conditions will be combined with AND
            if (incomingActivities)
                conjunction = Conjunction.AND;
            for (Object model : tasksDefault) {
                if (!added_first)
                    candidate.addConjunction(conjunction);
                else
                    added_first = false;
                if (model instanceof SBVRExpressionModel)
                    candidate.addIdentifiedExpression((SBVRExpressionModel) model);
                else
                    candidate.addUnidentifiedText(model.toString());
            }
        }
        return candidate;
    }


    private void extractRuleT4(Element el) {
        if (!BPMN2Profile.isEventBasedGateway(el))
            return;
        ControlNode node = (ControlNode) el;
        for (ActivityEdge edgeInc: node.getIncoming()) {
            if (isActivityElement(edgeInc.getSource()) && getActivityText(edgeInc.getSource()) != null) {
                Map<Element, String> subjectsInc = getSubjectNames(edgeInc.getSource(), false);
                for (Entry<Element, String> subjectInc: subjectsInc.entrySet()) {
                    for (ActivityEdge edgeOut: node.getOutgoing()) {
                        if (isIntermediaryEvent(edgeOut.getTarget()) && getActivityText(edgeOut.getTarget()) != null) {
                            SBVRExpressionModel model = new SBVRExpressionModel().addRuleExpression(RuleType.PERMISSION);
                            model = addActivity(model, edgeOut.getTarget(), null);
                            model.addRuleConditional(Conditional.AFTER);
                            model = addActivity(model, edgeInc.getSource(), subjectInc.getValue());
                            String incomingCondition = getCondition(edgeInc);
                            if (incomingCondition != null) {
                                model.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF);
                                model = addCondition(model, incomingCondition);
                            }
                            String outgoingCondition = getCondition(edgeOut);
                            if (outgoingCondition != null) {
                                model.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF);
                                model = addCondition(model, outgoingCondition);
                            }
                            List<Object> src = new ArrayList<>(Arrays.asList(edgeOut.getTarget(), edgeInc.getSource()));
                            if (subjectInc.getKey() != null)
                                src.add(subjectInc.getKey());
                            MagicDrawSourceEntry source = new MagicDrawSourceEntry(src, "T4");
                            br_candidates.add(source, model);
                            br_candidates.setAutomaticExtraction(source);
                        }
                    }
                }
            }
        }
    }

    private void extractRuleT5(Element el) {
        if (!BPMN2Profile.isParallelGateway(el))
            return;
        GatewayNeighborhood tuple = gatewayNeighborhoods.get(el);
        if (tuple.incomingActivities.isEmpty() && tuple.outgoingActivities.isEmpty())
            return;
        if (!tuple.outgoingActivities.isEmpty()) {
            for (Entry<ActivityNode, ActivityNodeNeighborhood> actOut: tuple.outgoingActivities.entrySet()) {
                Map<Element, String> subjectsOut = actOut.getValue().activitySubjects;
                for (Entry<Element, String> subjectOut: subjectsOut.entrySet()) {
                    if (tuple.incomingActivities.isEmpty())
                        continue;
                    SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                    List<Object> objects = new ArrayList<>();
                    objects.add(actOut.getKey());
                    if (subjectOut.getKey() != null)
                        objects.add(subjectOut.getKey());
                    candidate = addActivity(candidate, actOut.getKey(), subjectOut.getValue());
                    SBVRExpressionModel conditionModel = createMultipleConditions(actOut.getValue().incomingConditions.get(el), objects);
                    if (!conditionModel.isEmpty())
                        candidate.addConjunction(Conjunction.AND)
                                .addRuleConditional(Conditional.IF)
                                .addIdentifiedExpression(conditionModel);
                    candidate.addRuleConditional(Conditional.AFTER);
                    boolean first_added = true;
                    for (Entry<ActivityNode, ActivityNodeNeighborhood> incNode: tuple.incomingActivities.entrySet()) {
                        Map<Element, String> subjectsIn = incNode.getValue().activitySubjects;
                        for (Entry<Element, String> subjectIn: subjectsIn.entrySet()) {
                            if (!first_added)
                                candidate.addConjunction(Conjunction.AND);
                            else
                                first_added = false;
                            candidate = addActivity(candidate, incNode.getKey(), subjectIn.getValue());
                            objects.add(incNode.getKey());
                            if (subjectIn.getKey() != null)
                                objects.add(subjectIn.getKey());
                            conditionModel = createMultipleConditions(incNode.getValue().outgoingConditions.get(el), objects);
                            if (!conditionModel.isEmpty())
                                candidate.addConjunction(Conjunction.AND)
                                        .addRuleConditional(Conditional.IF)
                                        .addIdentifiedExpression(conditionModel);
                        }
                    }
                    MagicDrawSourceEntry source = new MagicDrawSourceEntry(objects, "T5");
                    br_candidates.add(source, candidate);
                    br_candidates.setAutomaticExtraction(source);
                }
            }
        }
    }


    private void extractRuleT6(Element el) {
        if (!isActivityElement(el))
            return;
        List<Element> boundaryElements = BPMNHelper.getBoundaryEventRefs(el, getActivityStereotype(el));
        if (boundaryElements.isEmpty())
            return;
        for (Element boundary: boundaryElements) {
            if (!isBoundaryEvent(boundary))
                continue;
            boolean isInterrupting = false;
            Stereotype st = getStereotypeInList(boundary, boundaryStereotypes);
            if (st == null)
                continue;
            List cancelActivity = StereotypesHelper.getStereotypePropertyValue(boundary, st, "cancelActivity");
            if (!cancelActivity.isEmpty()) {
                Object valueObj = cancelActivity.get(0);
                if (valueObj instanceof EnumerationLiteral) {
                    String value = ((EnumerationLiteral) valueObj).getName();
                    if (value != null)
                        isInterrupting = Boolean.parseBoolean(value);
                }
            }
            Map<Element, String> activitySubjects = getSubjectNames(el, true);
            for (Entry<Element, String> subject: activitySubjects.entrySet()) {
                String boundaryText = extractElementText(boundary);
                if (boundaryText == null)
                    continue;
                SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.PERMISSION);
                candidate = addCondition(candidate, boundaryText).addRuleConditional(Conditional.WHEN);
                candidate = addActivity(candidate, (ActivityNode) el, subject.getValue());
                List<Object> src = new ArrayList<>();
                if (subject.getKey() != null)
                    src.add(subject.getKey());
                src.add(el);
                src.add(boundary);
                MagicDrawSourceEntry source = new MagicDrawSourceEntry(src, "T6");
                br_candidates.add(source, candidate);
                br_candidates.setAutomaticExtraction(source);

                for (ActivityEdge outNode: ((AcceptEventAction)boundary).getOutgoing()) {
                    ActivityNode outTask = outNode.getTarget();
                    if (isActivityElement(outTask)) {
                        if (getActivityText(outTask) == null)
                            continue;
                        candidate = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                        candidate = addActivity(candidate, outTask, subject.getValue())
                                .addRuleConditional(Conditional.AFTER);
                        candidate.addBracket(Bracket.LEFT);
                        candidate = addCondition(candidate, getProperName(boundary))
                                .addRuleConditional(Conditional.WHEN);
                        candidate = addActivity(candidate, (ActivityNode) el, subject.getValue());
                        candidate.addBracket(Bracket.RIGHT);
                        List<Object> srcOut = new ArrayList<>();
                        if (subject.getKey() != null)
                            srcOut.add(subject.getKey());
                        srcOut.addAll(Arrays.asList(el, boundary, outTask));
                        source = new MagicDrawSourceEntry(srcOut, "T6");
                        br_candidates.add(source, candidate);
                        br_candidates.setAutomaticExtraction(source);
                    }
                }

                if (!isInterrupting) {
                    candidate = new SBVRExpressionModel().addRuleExpression(RuleType.PROHIBITION);
                    candidate = addActivity(candidate, (ActivityNode) el, subject.getValue())
                            .addRuleConditional(Conditional.AFTER);
                    candidate = addCondition(candidate, getProperName(boundary));
                    src = new ArrayList<>();
                    if (subject.getKey() != null)
                        src.add(subject.getKey());
                    src.add(el);
                    src.add(boundary);
                    source = new MagicDrawSourceEntry(src, "T6");
                    br_candidates.add(source, candidate);
                    br_candidates.setAutomaticExtraction(source);
                }
            }
        }
    }


    private void extractRuleT7(Element el) {
        if (isActivityElement(el) || isEventElement(el))
            extractTasksWithDataObjects(el, false, "is produced", "is provided to", "T7");
        else if (isSequenceFlow(el))
            extractTasksWithAssociationsAndDataObjects(el, false, "is produced", "is provided to", "T7");
    }

    private void extractRuleT8(Element el) {
        if (isActivityElement(el))
            extractTasksWithDataObjects(el, true, "is available to", "is provided with data", "T8");
        else if (isSequenceFlow(el))
            extractTasksWithAssociationsAndDataObjects(el, true, "is available to", "is provided with data", "T8");
    }


    private void extractTasksWithAssociationsAndDataObjects(Element el, boolean checkDataStore,
                                                            String reservedVerb1, String reservedVerb2, String rule) {
        if (!isSequenceFlow(el))
            return;
        ActivityEdge flow = (ActivityEdge) el;
        List<Element> dataObjects = new ArrayList<>();
        for (Relationship assoc: flow.get_relationshipOfRelatedElement())
            if (assoc instanceof Dependency) {
                Collection<NamedElement> clients = ((Dependency) assoc).getClient();
                for (NamedElement client: clients)
                    if (checkDataStore ? BPMN2Profile.isDataStore(el) : isDataObject(client))
                        dataObjects.add(client);
            }
        processOutgoingConnectionsWithDataObjects(dataObjects, flow.getSource(), checkDataStore, reservedVerb1, rule);
        processIncomingConnectionsWithDataObjects(dataObjects, flow.getTarget(), checkDataStore, reservedVerb2, rule);
    }


    private void extractTasksWithDataObjects(Element el, boolean checkDataStore, String reservedVerb1, String reservedVerb2, String rule) {
        if (!isActivityElement(el))
            return;
        // Outgoing tasks with data objects
        List<Element> dataObjects = new ArrayList<>();
        for (ActivityEdge outAssoc: ((ActivityNode)el).getOutgoing())
            if (BPMN2Profile.isDataAssociation(outAssoc)) {
                ActivityNode dataObj = outAssoc.getTarget();
                if (checkDataStore ? BPMN2Profile.isDataStore(dataObj) : isDataObject(dataObj))
                    dataObjects.add(dataObj);
            }
        processOutgoingConnectionsWithDataObjects(dataObjects, (ActivityNode) el, checkDataStore, reservedVerb1, rule);
        // Incoming tasks with data objects
        dataObjects.clear();
        for (ActivityEdge outAssoc: ((ActivityNode)el).getIncoming())
            if (BPMN2Profile.isDataAssociation(outAssoc)) {
                ActivityNode dataObj = outAssoc.getSource();
                if (checkDataStore ? BPMN2Profile.isDataStore(dataObj) : isDataObject(dataObj))
                    dataObjects.add(dataObj);
            }
        if (dataObjects.isEmpty())
            return;
        processIncomingConnectionsWithDataObjects(dataObjects, (ActivityNode) el, checkDataStore, reservedVerb2, rule);
    }


    private void processOutgoingConnectionsWithDataObjects(List<Element> dataObjects, ActivityNode taskElement, boolean checkDataStore,
                                                           String reservedVerb1, String rule) {
        if (dataObjects.isEmpty() || getActivityText(taskElement) == null)
            return;
        Map<Element, String> taskSubjects = getSubjectNames(taskElement, false);
        if (!dataObjects.isEmpty()) {
            for (Entry<Element, String> taskSubject : taskSubjects.entrySet()) {
                SBVRExpressionModel subjectConcept = getGeneralConcept(taskSubject.getValue());
                SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                boolean added_first_obj = true;
                for (Element dataObj : dataObjects) {
                    if (!added_first_obj)
                        candidate = candidate.addConjunction(Conjunction.AND);
                    else
                        added_first_obj = false;
                    String objText = getDataObjectName(dataObj);
                    if (objText == null)
                        continue;
                    Collection<State> states = Collections.emptyList();
                    if (dataObj instanceof CentralBufferNode)
                        states = ((CentralBufferNode) dataObj).getInState();
                    if (states.isEmpty()) {
                        SBVRExpressionModel objConcept = getGeneralConcept(objText);
                        if (objConcept != null)
                            candidate.addIdentifiedExpression(objConcept);
                        else
                            candidate.addUnidentifiedText(objText);
                        candidate = candidate.addVerbConcept(reservedVerb1, true);
                        if (checkDataStore)
                            candidate = subjectConcept != null ?
                                    candidate.addIdentifiedExpression(subjectConcept) :
                                    candidate.addUnidentifiedText(taskSubject.getValue());
                    } else {
                        boolean added_first_state = true;
                        for (State state : states) {
                            if (!added_first_state)
                                candidate = candidate.addConjunction(Conjunction.AND);
                            else
                                added_first_state = false;
                            String stateText = extractElementText(state);
                            if (stateText == null)
                                continue;
                            SBVRExpressionModel objConcept = getGeneralConcept(stateText + " " + objText);
                            candidate = objConcept != null ?
                                    candidate.addIdentifiedExpression(objConcept) :
                                    candidate.addUnidentifiedText(stateText + " " + objText);
                            candidate = candidate.addVerbConcept(reservedVerb1, true);
                            if (checkDataStore)
                                candidate = subjectConcept != null ?
                                        candidate.addIdentifiedExpression(subjectConcept) :
                                        candidate.addUnidentifiedText(taskSubject.getValue());
                        }
                    }
                }
                candidate = candidate.addRuleConditional(Conditional.WHEN);
                candidate = addActivity(candidate, taskElement, taskSubject.getValue());
                List<Object> srcElements = new ArrayList<>(dataObjects);
                if (taskSubject.getKey() != null)
                    srcElements.add(taskSubject.getKey());
                srcElements.add(taskElement);
                MagicDrawSourceEntry source = new MagicDrawSourceEntry(srcElements, rule);
                br_candidates.add(source, candidate);
                br_candidates.setAutomaticExtraction(source);
            }
        }
    }


    private void processIncomingConnectionsWithDataObjects(List<Element> dataObjects, ActivityNode taskElement, boolean checkDataStore,
                                                           String reservedVerb2, String rule) {
        if (dataObjects.isEmpty() || getActivityText(taskElement) == null)
            return;
        Map<Element, String> taskSubjects = getSubjectNames(taskElement, false);
        for (Entry<Element, String> taskSubject : taskSubjects.entrySet()) {
            SBVRExpressionModel subjectConcept = getGeneralConcept(taskSubject.getValue());
            SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(RuleType.PERMISSION);
            candidate = addActivity(candidate, taskElement, taskSubject.getValue())
                    .addRuleConditional(Conditional.ONLY_IF);
            boolean added_first_obj = true;
            for (Element dataObj: dataObjects) {
                if (!added_first_obj)
                    candidate = candidate.addConjunction(Conjunction.AND);
                else
                    added_first_obj = false;
                String objText = getDataObjectName(dataObj);
                if (objText == null)
                    continue;
                Collection<State> states = Collections.emptyList();
                if (dataObj instanceof CentralBufferNode)
                    states = ((CentralBufferNode) dataObj).getInState();
                if (states.isEmpty()) {
                    SBVRExpressionModel objConcept = getGeneralConcept(objText);
                    if (objConcept != null)
                        candidate.addIdentifiedExpression(objConcept);
                    else
                        candidate.addUnidentifiedText(objText);
                    candidate = candidate.addVerbConcept(reservedVerb2, true);
                    if (!checkDataStore)
                        candidate = subjectConcept != null ?
                                candidate.addIdentifiedExpression(subjectConcept) :
                                candidate.addUnidentifiedText(taskSubject.getValue());
                } else {
                    boolean added_first_state = true;
                    for (State state: states) {
                        if (!added_first_state)
                            candidate = candidate.addConjunction(Conjunction.AND);
                        else
                            added_first_state = false;
                        String stateText = extractElementText(state);
                        if (stateText == null)
                            continue;
                        SBVRExpressionModel objConcept = getGeneralConcept(stateText + " " + objText);
                        candidate = objConcept != null ?
                                candidate.addIdentifiedExpression(objConcept) :
                                candidate.addUnidentifiedText(stateText + " " + objText);
                        candidate = candidate.addVerbConcept(reservedVerb2, true);
                        if (!checkDataStore)
                            candidate = subjectConcept != null ?
                                    candidate.addIdentifiedExpression(subjectConcept) :
                                    candidate.addUnidentifiedText(taskSubject.getValue());
                    }
                }
            }
            List<Object> srcElements = new ArrayList<>();
            srcElements.add(taskElement);
            if (taskSubject.getKey() != null)
                srcElements.add(taskSubject.getKey());
            srcElements.addAll(dataObjects);
            MagicDrawSourceEntry source = new MagicDrawSourceEntry(srcElements, rule);
            br_candidates.add(source, candidate);
            br_candidates.setAutomaticExtraction(source);
        }
    }


    private void extractRuleT9(Element el) {
        if (BPMN2Profile.isMessageFlow(el)) {
            Collection<Classifier> conveyed = ((InformationFlow) el).getConveyed();
            if (conveyed.isEmpty())
                extractMessageFlow(el, extractElementText(el));
            else {
                for (Classifier convObj : conveyed)
                    if (BPMN2Profile.isBPMNMessage(convObj))
                        extractMessageFlow(el, convObj);
            }
        }
    }

    private void extractMessageFlow(Element el, Object convObj) {
        if (!(convObj == null || convObj instanceof String || convObj instanceof Element))
            return;
        Collection<NamedElement> sources = ((InformationFlow) el).getInformationSource();
        Collection<NamedElement> targets = ((InformationFlow) el).getInformationTarget();
        NamedElement source = null, target = null;
        if (!sources.isEmpty() && !targets.isEmpty()) {
            source = sources.iterator().next();
            target = targets.iterator().next();
        }
        if (source == null)
            return;
        if (isLaneElement(source) && isLaneElement(target)) {
            addMessageFlowBetweenLanes(el, convObj, source, null, target, null, "sends", "to", RuleType.PERMISSION);
            addMessageFlowBetweenLanes(el, convObj, target, null, source, null, "receives", "from", RuleType.PERMISSION);
        } else if ((isActivityElement(source) || isEventElement(source)) && isLaneElement(target)) {
            if (getActivityText((ActivityNode) source) == null)
                return;
            Set<Element> subjects = getSubjectNames(source, true).keySet();
            RuleType ruleType = RuleType.PERMISSION;
            if (BPMN2Profile.isSendTask(source) || BPMN2Profile.isReceiveTask(source))
                ruleType = RuleType.OBLIGATION;
            for (Element subject: subjects) {
                addMessageFlowBetweenLanes(el, convObj, subject, (ActivityNode) source, target, null, "sends", "to", ruleType);
                addMessageFlowBetweenLanes(el, convObj, target, null, subject, null, "receives", "from", RuleType.PERMISSION);
            }
        } else if (isLaneElement(source) && (isActivityElement(target) || isEventElement(target))) {
            if (getActivityText((ActivityNode) target) == null)
                return;
            Set<Element> subjects = getSubjectNames(target, true).keySet();
            for (Element subject: subjects)
                addMessageFlowBetweenLanes(el, convObj, source, null, subject, (ActivityNode)target, "sends", "to", RuleType.PERMISSION);
            addReceivingNodeEventRules(el, convObj, source, (ActivityNode) target);
        } else if ((isActivityElement(source) || isEventElement(source)) && (isActivityElement(target) || isEventElement(target))) {
            if (getActivityText((ActivityNode) source) == null || getActivityText((ActivityNode) target) == null)
                return;
            RuleType ruleType = RuleType.PERMISSION;
            if (BPMN2Profile.isSendTask(source) || BPMN2Profile.isReceiveTask(source))
                ruleType = RuleType.OBLIGATION;
            Set<Element> subjects = getSubjectNames(source, true).keySet();
            Set<Element> subjectsT = getSubjectNames(target, true).keySet();
            for (Element subject: subjects)
                for (Element subjectT: subjectsT)
                    addMessageFlowBetweenLanes(el, convObj, subject, (ActivityNode)source, subjectT, (ActivityNode)target, "sends", "to", ruleType);
            addReceivingNodeEventRules(el, convObj, source, (ActivityNode) target);
        }
    }

    private void addMessageFlowBetweenLanes(Element el, Object convObj, Element subject1, ActivityNode task1,
                                            Element subject2, ActivityNode task2, String verb1, String verb2, RuleType ruleType) {
        SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(ruleType);
        if (task1 != null) {
            String objText = extractElementText(subject1);
            if (isLaneElement(subject1))
                objText = getLaneName((ActivityPartition) subject1);
            candidate = addActivity(candidate, task1, objText).addRuleConditional(Conditional.WHEN);
        } else {
            if (isLaneElement(subject1))
                candidate = addLaneGeneralConcept(candidate, (ActivityPartition) subject1);
            else
                candidate = addGeneralConcept(candidate, subject1);
        }
        candidate = addConveyedObject(candidate, convObj, verb1, verb2);
        if (isLaneElement(subject2))
            candidate = addLaneGeneralConcept(candidate, (ActivityPartition) subject2);
        else
            candidate = addGeneralConcept(candidate, subject2);
        if (task2 != null) {
            candidate = candidate.addRuleConditional(Conditional.WHEN);
            String objText = extractElementText(subject2);
            if (isLaneElement(subject2))
                objText = getLaneName((ActivityPartition) subject2);
            candidate = addActivity(candidate, task2, objText);
        }
        List<Object> source = new ArrayList<>();
        if (subject1 != null)
            source.add(subject1);
        source.add(el);
        if (subject2 != null)
            source.add(subject2);
        if (task1 != null)
            source.add(task1);
        if (task2 != null)
            source.add(task2);
        MagicDrawSourceEntry src = new MagicDrawSourceEntry(source, "T9");
        br_candidates.add(src, candidate);
        br_candidates.setAutomaticExtraction(src);
    }

    private SBVRExpressionModel addConveyedObject(SBVRExpressionModel candidate, Object convObj, String verb1, String verb2) {
        if (convObj != null) {
            candidate = candidate.addVerbConcept(verb1, true);
            if (convObj instanceof String)
                candidate = addGeneralConcept(candidate, (String) convObj);
            else if (convObj instanceof Element)
                candidate = addGeneralConcept(candidate, (Element) convObj);
            candidate = candidate.addVerbConcept(verb2, true);
        } else
            candidate = candidate.addVerbConcept(verb1 + " message " + verb2, true);
        return candidate;
    }

    private void addReceivingNodeEventRules(Element el, Object convObj, Element source, ActivityNode target) {
        Map<Element, String> subjects = getSubjectNames(target, true);
        RuleType ruleType = RuleType.PERMISSION;
        if (isActivityElement(target)) {
            if (BPMN2Profile.isSendTask(target) || BPMN2Profile.isReceiveTask(target))
                ruleType = RuleType.OBLIGATION;
            for (Element subject : subjects.keySet())
                if (source instanceof ActivityNode) {
                    if (getActivityText((ActivityNode) source) == null)
                        continue;
                    Map<Element, String> subjectsS = getSubjectNames(source, true);
                    for (Element subjectS: subjectsS.keySet())
                        addMessageFlowBetweenLanes(el, convObj, subject, null, subjectS, target, "receives", "from", ruleType);
                } else
                    addMessageFlowBetweenLanes(el, convObj, subject, null, source, target, "receives", "from", ruleType);
        } else if (isEventElement(target))
            for (Element subject : subjects.keySet()) {
                SBVRExpressionModel candidate = new SBVRExpressionModel().addRuleExpression(ruleType);
                candidate = addActivity(candidate, target, null).addRuleConditional(Conditional.ONLY_WHEN);
                candidate = addGeneralConcept(candidate, subject);
                candidate = addConveyedObject(candidate, convObj, "receives", "from");
                candidate = addGeneralConcept(candidate, source);
                List<Object> src = new ArrayList<>();
                src.add(el);
                if (subject != null)
                    src.add(subject);
                src.add(source);
                src.add(target);
                MagicDrawSourceEntry sourceEntry = new MagicDrawSourceEntry(src, "T9");
                br_candidates.add(sourceEntry, candidate);
                br_candidates.setAutomaticExtraction(sourceEntry);
            }
    }

    private void extractGatewayNeighborhoods() {
        gatewayNeighborhoods = new HashMap<>();
        for (Element el: candidateElements)
            if (isGatewayElement(el))
                gatewayNeighborhoods.put((ControlNode) el, new GatewayNeighborhood((ControlNode) el, true));

        gatewayNeighborhoods2 = new HashMap<>();
        for (Element el: candidateElements)
            if (isGatewayElement(el))
                gatewayNeighborhoods2.put((ControlNode) el, new GatewayNeighborhood((ControlNode) el, false));
    }


    Object[] getRuleWithGateways(ControlNode el, Conjunction conjunction, ControlNode targetGateway) {
        GatewayNeighborhood tuple = gatewayNeighborhoods.get(el);
        List<Object> objects = new ArrayList<>();
        List<String> representations = new ArrayList<>();
        SBVRExpressionModel candidate = new SBVRExpressionModel();
        Object[] results = new Object[3];
        results[0] = candidate;
        results[1] = objects;
        results[2] = representations;
        if (tuple.incomingActivities.isEmpty() && tuple.outgoingActivities.isEmpty())
            return results;
        if (!tuple.incomingActivities.isEmpty())
            candidate = addTasksWithConditions(candidate, tuple.incomingActivities, true, objects, conjunction, el);
        SBVRExpressionModel ruleModel = null;
        if (targetGateway != null) {
            Map<ActivityEdge, String> conditions = tuple.outgoingConditions.get(targetGateway);
            if (conditions != null)
                ruleModel = createMultipleConditions(conditions, objects);
        }
        // If sequence flow has condition, add it
        if (ruleModel != null && !ruleModel.isEmpty())
            candidate.addConjunction(Conjunction.AND).addRuleConditional(Conditional.IF)
                    .addIdentifiedExpression(ruleModel);
        else {
            // Add outgoing activities as negation condition
            if (!tuple.outgoingActivities.isEmpty()) {
                candidate.addRuleConditional(Conditional.IF_NOT).addBracket(Bracket.LEFT);
                candidate = addTasksWithConditions(candidate, tuple.outgoingActivities, false, objects, conjunction, el);
                candidate.addBracket(Bracket.RIGHT);
            }
        }
        results[0] = candidate;
        return results;
    }

    private void createPartialRules(GatewayNeighborhood nhood, Element startedElement, ControlNode targetGateway) {
        ControlNode gateway = nhood.gatewayNode;
        Conjunction conjunction = getGatewayConjunction(gateway);
        if (conjunction == null)
            return;
        // If gateway is a boundary gateway, return atomic partial rule
        if (nhood.incomingGateways.isEmpty()) {
            Object[] results = getRuleWithGateways(gateway, conjunction, targetGateway);
            if (results == null)
                return;
            SBVRExpressionModel partial = (SBVRExpressionModel) results[0];
            nhood.partialRule = new SBVRExpressionModel().addBracket(Bracket.LEFT)
                    .addIdentifiedExpression(partial).addBracket(Bracket.RIGHT);
            nhood.partialRuleSource = (List<Object>) results[1];
            return;
        }
        nhood.partialRule = new SBVRExpressionModel();
        nhood.partialRuleSource = new ArrayList<>();
        Set<SBVRExpressionModel> defaultRules = new HashSet<>();
        Set<SBVRExpressionModel> conditionedRules = new HashSet<>();
        boolean first_added = true;
        SBVRExpressionModel modelPart = new SBVRExpressionModel();
        // Recursively process incoming gateways
        for (Entry<ControlNode, GatewayNeighborhood> gatewayIn: nhood.incomingGateways.entrySet()) {
            createPartialRules(gatewayIn.getValue(), startedElement, gateway);
            // It is possible that multiple sequence flows are between the two gateways
            Map<ActivityEdge, String> conditions = nhood.incomingConditions.get(gatewayIn.getKey());
            SBVRExpressionModel ruleModel = createMultipleConditions(conditions, nhood.partialRuleSource);
            SBVRExpressionModel partialIncomingRule = gatewayIn.getValue().partialRule;
            if (partialIncomingRule.isEmpty())
                continue;
            if (ruleModel.isEmpty())
                defaultRules.add(partialIncomingRule);
            else {
                if (!first_added)
                    modelPart.addConjunction(conjunction);
                else
                    first_added = false;
                modelPart.addIdentifiedExpression(partialIncomingRule)
                        .addConjunction(Conjunction.AND)
                        .addRuleConditional(Conditional.IF)
                        .addIdentifiedExpression(ruleModel);
                conditionedRules.add(partialIncomingRule);
            }
            nhood.partialRuleSource.addAll(gatewayIn.getValue().partialRuleSource);
        }
        // Process incoming activities
        for (Entry<ActivityNode, ActivityNodeNeighborhood> activityIn: nhood.incomingActivities.entrySet()) {
            // Exclude element which was used as the starting point
            if (activityIn.getKey().equals(startedElement))
                continue;
            // It is possible that multiple sequence flows are between the two tasks
            Map<ActivityEdge, String> conditions = activityIn.getValue().incomingConditions.get(activityIn.getKey());
            SBVRExpressionModel ruleModel = createMultipleConditions(conditions, nhood.partialRuleSource);
            Map<Element, String> subjects = getSubjectNames(activityIn.getKey(), false);
            for (Entry<Element, String> subject : subjects.entrySet()) {
                SBVRExpressionModel partialIncomingRule = addActivity(new SBVRExpressionModel(), activityIn.getKey(), subject.getValue());
                if (partialIncomingRule.isEmpty())
                    continue;
                if (ruleModel.isEmpty())
                    defaultRules.add(partialIncomingRule);
                else {
                    if (!first_added)
                        modelPart.addConjunction(conjunction);
                    else
                        first_added = false;
                    modelPart.addIdentifiedExpression(partialIncomingRule)
                            .addRuleConditional(Conditional.IF)
                            .addIdentifiedExpression(ruleModel);
                    conditionedRules.add(partialIncomingRule);
                }
                if (subject.getKey() != null)
                    nhood.partialRuleSource.add(subject.getKey());
                nhood.partialRuleSource.add(activityIn.getKey());
            }
        }
        // Add default conditions
        if (!defaultRules.isEmpty()) {
            first_added = true;
            if (!conditionedRules.isEmpty())
                modelPart.addUnidentifiedText(",").addRuleConditional(Conditional.OTHERWISE);
            for (SBVRExpressionModel default_: defaultRules) {
                if (!first_added)
                    modelPart.addConjunction(conjunction);
                else
                    first_added = false;
                modelPart.addIdentifiedExpression(default_);
            }
        }
        // Finally, recursively combine extracted partial rule
        if (!modelPart.isEmpty())
            nhood.partialRule.addBracket(Bracket.LEFT).addIdentifiedExpression(modelPart).addBracket(Bracket.RIGHT);
    }

    private void extractComplexRule(Element el) {
        if (!(isActivityElement(el) || isEventElement(el)))
            return;
        Map<Element, String> subjects = getSubjectNames(el, false);
        Collection<ActivityEdge> incomingEdges = ((ActivityNode) el).getIncoming();
        if (incomingEdges.isEmpty())
            return;
        Set<ActivityEdge> incomingGateways = incomingEdges.stream().filter(n -> isGatewayElement(n.getSource())).collect(Collectors.toSet());
        // If activity is connected only with other activities, T1 rule will extract such rules
        if (incomingGateways.isEmpty())
            return;
        for (Entry<Element, String> subject : subjects.entrySet()) {
            for (ActivityEdge edge: incomingGateways) {
                ControlNode gateway = (ControlNode) edge.getSource();
                GatewayNeighborhood nhood = gatewayNeighborhoods.get(gateway);
                createPartialRules(nhood, el, null);
                // Create rule consequent part
                SBVRExpressionModel ruleConsequent = new SBVRExpressionModel().addRuleExpression(RuleType.OBLIGATION);
                List<Object> sources = new ArrayList<>();
                sources.add(el);
                if (subject.getKey() != null)
                    sources.add(subject.getKey());
                ruleConsequent = addActivity(ruleConsequent, (ActivityNode)el, subject.getValue());
                ActivityNodeNeighborhood activityNhood = nhood.outgoingActivities.get(el);
                if (activityNhood == null)
                    return;
                Map<ActivityEdge, String> conditionsOut = activityNhood.incomingConditions.get(gateway);
                SBVRExpressionModel rulePart = createMultipleConditions(conditionsOut, sources);
                if (!rulePart.isEmpty())
                    ruleConsequent.addRuleConditional(Conditional.IF).addIdentifiedExpression(rulePart);
                // Add partial rule for each of the incoming gateway as antecedent (after)
                for (GatewayNeighborhood incGateway: nhood.incomingGateways.values()) {
                    if (incGateway.partialRule == null || incGateway.partialRule.isEmpty())
                        continue;
                    SBVRExpressionModel rule = ruleConsequent.clone();
                    List<Object> sourcesCopy = new ArrayList<>(sources);
                    rule.addUnidentifiedText(",");
                    if (incGateway.partialRule.getExpressionElement(0).compareTo(Conditional.AFTER.toString()) != 0)
                        rule.addRuleConditional(Conditional.AFTER);
                    rule.addIdentifiedExpression(incGateway.partialRule);
                    sources.addAll(incGateway.partialRuleSource);
                    MagicDrawSourceEntry src = new MagicDrawSourceEntry(sourcesCopy, "Complex");
                    br_candidates.add(src, rule);
                    br_candidates.setAutomaticExtraction(src);
                }
            }
        }
    }


    @Override
    protected void extractModelVocabulary() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String[] getMetamodelVocabularyNames() {
        return null;    // Not implemented yet!
    }

    @Override
    public String removeMetaconceptName(String name) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static Collection<DiagramPresentationElement> getBPMNDiagrams(Package model, Collection<DiagramPresentationElement> diagrams) {
        Project project = Application.getInstance().getProject();
        if (project == null || model == null)
            return diagrams;
        for (Diagram diag : model.getOwnedDiagram()) {
            DiagramPresentationElement pres = project.getDiagram(diag);
            if (pres != null && PluginUtilities.isBPMNDiagram(pres))
                diagrams.add(pres);
        }
        for (Activity el : BPMNHelper.getBPMNProcesses(model))
            for (Diagram diag : el.getOwnedDiagram()) {
                DiagramPresentationElement pres = project.getDiagram(diag);
                if (pres != null && PluginUtilities.isBPMNDiagram(pres))
                    diagrams.add(pres);
            }
        for (Package pkg : model.getNestedPackage())
            diagrams = getBPMNDiagrams(pkg, diagrams);
        return diagrams;
    }

    public static Collection<DiagramPresentationElement> getBPMNDiagrams(Package root) {
        Collection<DiagramPresentationElement> diagrams = new HashSet<>();
        return BpmnSBVRExtractor.getBPMNDiagrams(root, diagrams);
    }

    @Override
    protected Collection<Element> getDiagramElements(Collection<Element> colelem, DiagramPresentationElement diagram) {
        Collection<Element> newelem = new HashSet<>();
        for (Element element : colelem)
            if (element instanceof Package && diagram.findPresentationElement(element, null) != null)
                addPackageElements(diagram, (Package) element, newelem);
            else if (element instanceof StructuredActivityNode && diagram.findPresentationElement(element, null) != null)
                addSubProcessElements(diagram, (StructuredActivityNode) element, newelem);
        return newelem;
    }

    private void addSubProcessElements(DiagramPresentationElement diagram, StructuredActivityNode node, Collection<Element> elements) {
        for (Element el : node.getOwnedElement())
            if (diagram.findPresentationElement(el, null) != null)
                elements.add(el);
        for (ActivityNode innerNode : node.getNode())
            if (innerNode instanceof StructuredActivityNode && diagram.findPresentationElement(innerNode, null) != null)
                addSubProcessElements(diagram, (StructuredActivityNode) innerNode, elements);
    }

    private String getConditionsRepresentation(Map<ActivityNode, Map<ActivityEdge, String>> structConditions){
        final StringBuilder sb = new StringBuilder();
        for (Entry<ActivityNode, Map<ActivityEdge, String>> entry: structConditions.entrySet()) {
            sb.append(entry.getKey().getHumanName()).append(": [");
            if (!entry.getValue().isEmpty()) {
                for (Entry<ActivityEdge, String> condition: entry.getValue().entrySet())
                    sb.append(condition.getKey().getHumanName()).append(" -> ").append(condition.getValue()).append(", ");
                sb.delete(sb.length() - 2, sb.length());
            }
            sb.append("]").append("\n");
        }
        return sb.toString();
    }


}
