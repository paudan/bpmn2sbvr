package org.ktu.model2sbvr.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultConceptModel extends AbstractConceptModel {

    public DefaultConceptModel() {
        super();
    }

    @Override
    public Set<String> getCandidatesListText() {
        Set<String> candidates = new HashSet<>();
        for (SourceEntry concept : data.keySet())
            if (data.get(concept) != null)
                for (SBVRExpressionModel sbvr : data.get(concept).getCandidates())
                    candidates.add(sbvr.toString());
        return candidates;
    }

    @Override
    public Set<String> getCandidatesListHTML() {
        Set<String> candidates = new HashSet<>();
        for (SourceEntry concept : data.keySet())
            if (data.get(concept) != null)
                for (SBVRExpressionModel sbvr : data.get(concept).getCandidates())
                    candidates.add(sbvr.toHTMLString(true, null));
        return candidates;
    }

    @Override
    public DefaultConceptModel clone() {
        DefaultConceptModel copy = new DefaultConceptModel();
        copy = (DefaultConceptModel) this.copyInstance(copy);
        return copy;
    }

    @Override
    public Map<String, SBVRExpressionModel> getListMap() {
        Map<String, SBVRExpressionModel> map = new HashMap<>();
        for (SourceEntry concept : data.keySet())
            if (data.get(concept) != null)
                for (SBVRExpressionModel sbvr : data.get(concept).getCandidates())
                    map.put(sbvr.toString(), sbvr);
        return map;
    }
}
