package org.ktu.model2sbvr.extract;

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import org.ktu.model2sbvr.models.SourceEntry;
import java.util.ArrayList;
import java.util.List;

public class MagicDrawSourceEntry extends SourceEntry {

    public MagicDrawSourceEntry(List<Object> source, String rule) {
        this.source = new ArrayList<>();
        for (Object src: source)
            addEntry(src);
        this.rule = rule;
    }

    public MagicDrawSourceEntry(List<Object> source) {
        this(source, null);
    }

    public void addEntry(Object source) {
        this.source.add(source);
        if (source instanceof Element)
            this.sourceText.add(((Element)source).getHumanName());
        else
            this.sourceText.add(source.toString());
    }
}
