package org.ktu.model2sbvr;

import com.nomagic.magicdraw.cbm.BPMNConstants;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.magicdraw.core.project.ProjectsManager;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import java.io.File;
import java.net.URI;
import java.util.Set;

public class PluginUtilities {
    
    public static final String SBVR_MODELVOC_PACKAGE_NAME = "SBVR Model Vocabulary";

    public static void addSBVRProfiles() {
        Project project = Application.getInstance().getProject();
        ProjectsManager projectsManager = Application.getInstance().getProjectsManager();
        URI uriprof = new File("SBVR profile.mdxml").toURI();
        ProjectDescriptor projdesc = ProjectDescriptorsFactory.createProjectDescriptor(uriprof);
        if (projectsManager.findAttachedProject(project, projdesc) == null)
            projectsManager.useModule(project, projdesc);
        uriprof = new File("SBVR customizations.mdxml").toURI();
        projdesc = ProjectDescriptorsFactory.createProjectDescriptor(uriprof);
        if (projectsManager.findAttachedProject(project, projdesc) == null)
            projectsManager.useModule(project, projdesc);
        uriprof = new File("Integration profile.mdzip").toURI();
        projdesc = ProjectDescriptorsFactory.createProjectDescriptor(uriprof);
        if (projectsManager.findAttachedProject(project, projdesc) == null)
            projectsManager.useModule(project, projdesc);
        
    }

    public static Profile getCustomizationsProfile(Project project) {
        return StereotypesHelper.getProfileByURI(project, new File("SBVR customizations.mdxml").toURI().toString());
    }

    public static Profile getBPMNProfile(Project project) {
        return StereotypesHelper.getProfileByURI(project, new File(BPMNConstants.BPMN2_PROFILE_FILENAME).toURI().toString());
    }

    public static boolean isBPMNDiagram(DiagramPresentationElement diag) {
        Set<String> bpmnDiagrams = BPMNConstants.BPMN_DIAGRAMS;
        for (String dn: bpmnDiagrams)
            if (diag.getDiagramType().getType().compareToIgnoreCase(dn) == 0)
                return true;
        return false;
    }
}
