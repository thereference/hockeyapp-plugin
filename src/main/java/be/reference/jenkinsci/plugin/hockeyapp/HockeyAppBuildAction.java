package be.reference.jenkinsci.plugin.hockeyapp;

import hudson.model.Action;
import hudson.model.ProminentProjectAction;

public class HockeyAppBuildAction implements ProminentProjectAction {
    public String iconFileName;
    public String displayName;
    public String urlName;

    public HockeyAppBuildAction() {
    }

    public HockeyAppBuildAction(Action action) {
        iconFileName = action.getIconFileName();
        displayName = action.getDisplayName();
        urlName = action.getUrlName();
    }

    public String getIconFileName() {
        return iconFileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUrlName() {
        return urlName;
    }
}
