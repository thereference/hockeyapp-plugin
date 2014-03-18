package be.reference.jenkinsci.plugin.hockeyapp;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.ProxyConfiguration;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.*;
import hudson.util.CopyOnWriteList;
import hudson.util.RunList;
import hudson.util.Secret;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.Hudson;

import java.util.*;

import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.kohsuke.stapler.StaplerRequest;


public class HockeyAppRecorder extends Recorder {
    private String tokenPairName;

    public String getTokenPairName() {
        return this.tokenPairName;
    }
    
    private Boolean notifyTeam;

    public Boolean getNotifyTeam() {
        return this.notifyTeam;
    }

    private String buildNotes;

    public String getBuildNotes() {
        return this.buildNotes;
    }
    
    private Integer releaseType;
    
    public Integer getReleaseType() {
    	return this.releaseType;
    }

    private Boolean privateBuild;

    public Boolean getPrivateBuild() {
        return this.privateBuild;
    }
    
    private Boolean notesInMarkdown;

    public Boolean getNotesInMarkdown() {
        return this.notesInMarkdown;
    }
    
    private boolean appendChangelog;

    public boolean getAppendChangelog() {
        return this.appendChangelog;
    }

    /**
     * Comma- or space-separated list of patterns of files/directories to be archived.
     * The variable hasn't been renamed yet for compatibility reasons
     */
    private String filePath;

    public String getFilePath() {
        return this.filePath;
    }

    private String dsymPath;

    public String getDsymPath() {
        return this.dsymPath;
    }

    private String lists;

    public String getLists() {
        return this.lists;
    }

    private Boolean replace;

    public Boolean getReplace() {
        return this.replace;
    }



    private Boolean debug;

    public Boolean getDebug() {
        return this.debug;
    }

    private HockeyAppTeam [] additionalTeams;
    
    public HockeyAppTeam [] getAdditionalTeams() {
        return this.additionalTeams;
    }
    
    @DataBoundConstructor
    public HockeyAppRecorder(String tokenPairName, Boolean notifyTeam, String buildNotes, Boolean appendChangelog, String filePath, String dsymPath, String lists, Boolean replace, Boolean debug, HockeyAppTeam [] additionalTeams, Boolean notesInMarkdown, Boolean privateBuild, Integer releaseType) {
        this.tokenPairName = tokenPairName;
        this.notifyTeam = notifyTeam;
        this.buildNotes = buildNotes;
        this.appendChangelog = appendChangelog;
        this.filePath = filePath;
        this.dsymPath = dsymPath;
        this.replace = replace;
        this.lists = lists;
        this.debug = debug;
        this.additionalTeams = additionalTeams;
        this.notesInMarkdown = notesInMarkdown;
        this.privateBuild = privateBuild;
        this.releaseType = releaseType;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) {
        if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
            return false;

        listener.getLogger().println(Messages.HockeyAppRecorder_InfoUploading());

        try {
            EnvVars vars = build.getEnvironment(listener);

            String workspace = vars.expand("$WORKSPACE");

            List<HockeyAppUploader.UploadRequest> urList = new ArrayList<HockeyAppUploader.UploadRequest>();

            for(HockeyAppTeam team : createDefaultPlusAdditionalTeams()) {
                try {
                    HockeyAppUploader.UploadRequest ur = createPartialUploadRequest(team, vars, build);
                    urList.add(ur);
                } catch (MisconfiguredJobException mje) {
                    listener.getLogger().println(mje.getConfigurationMessage());
                    return false;
                }
            }

            for(HockeyAppUploader.UploadRequest ur : urList) {
                HockeyAppRemoteRecorder remoteRecorder = new HockeyAppRemoteRecorder(workspace, ur, listener);
    
                final List<Map> parsedMaps;
    
                try {
                    Object result = launcher.getChannel().call(remoteRecorder);
                    parsedMaps = (List<Map>) result;
                } catch (UploadException ue) {
                    listener.getLogger().println(Messages.HockeyAppRecorder_IncorrectResponseCode(ue.getStatusCode()));
                    listener.getLogger().println(ue.getResponseBody());
                    return false;
                }
    
                if (parsedMaps.size() == 0) {
                    listener.getLogger().println(Messages.HockeyAppRecorder_NoUploadedFile(ur.filePaths));
                    return false;
                }
                for (Map parsedMap: parsedMaps) {
                    addTestflightLinks(build, listener, parsedMap);
                }
            }
        } catch (Throwable e) {
            listener.getLogger().println(e);
            e.printStackTrace(listener.getLogger());
            return false;
        }

        return true;
    }

    private List<HockeyAppTeam> createDefaultPlusAdditionalTeams() {
        List<HockeyAppTeam> allTeams = new ArrayList<HockeyAppTeam>();
        // first team is default
        allTeams.add(new HockeyAppTeam(getTokenPairName(), getFilePath(), getDsymPath()));
        if(additionalTeams != null) {
            allTeams.addAll(Arrays.asList(additionalTeams));
        }
        return allTeams;
    }

    private void addTestflightLinks(AbstractBuild<?, ?> build, BuildListener listener, Map parsedMap) {
        HockeyAppBuildAction installAction = new HockeyAppBuildAction();
        String installUrl = (String) parsedMap.get("public_url");
        installAction.displayName = Messages.HockeyAppRecorder_InstallLinkText();
        installAction.iconFileName = "package.gif";
        installAction.urlName = installUrl;
        build.addAction(installAction);
        listener.getLogger().println(Messages.HockeyAppRecorder_InfoInstallLink(installUrl));

        HockeyAppBuildAction configureAction = new HockeyAppBuildAction();
        String configUrl = (String) parsedMap.get("config_url");
        configureAction.displayName = Messages.HockeyAppRecorder_ConfigurationLinkText();
        configureAction.iconFileName = "gear2.gif";
        configureAction.urlName = configUrl;
        build.addAction(configureAction);
        listener.getLogger().println(Messages.HockeyAppRecorder_InfoConfigurationLink(configUrl));

        build.addAction(new EnvAction());

        // Add info about the selected build into the environment
        EnvAction envData = build.getAction(EnvAction.class);
        if (envData != null) {
            envData.add("HOCKEYAPP_INSTALL_URL", installUrl);
            envData.add("HOCKEYAPP_CONFIG_URL", configUrl);
        }
    }

    private HockeyAppUploader.UploadRequest createPartialUploadRequest(HockeyAppTeam team, EnvVars vars, AbstractBuild<?, ?> build) {
        HockeyAppUploader.UploadRequest ur = new HockeyAppUploader.UploadRequest();
        HockeyAppToken tokenPair = getTokenPair(team.getTokenPairName());
        ur.filePaths = vars.expand(StringUtils.trim(team.getFilePath()));
        ur.dsymPath = vars.expand(StringUtils.trim(team.getDsymPath()));
        ur.apiToken = vars.expand(Secret.toString(tokenPair.getApiToken()));
        ur.buildNotes = createBuildNotes(vars.expand(buildNotes), build.getChangeSet());
        ur.notesInMarkdown = notesInMarkdown;
        ur.privateBuild = privateBuild;
        ur.releaseType = releaseType;
        ur.lists = vars.expand(lists);
        ur.notifyTeam = notifyTeam;
        ProxyConfiguration proxy = getProxy();
        ur.proxyHost = proxy.name;
        ur.proxyPass = proxy.getPassword();
        ur.proxyPort = proxy.port;
        ur.proxyUser = proxy.getUserName();
        ur.debug = debug;
        return ur;
    }

    private ProxyConfiguration getProxy() {
        ProxyConfiguration proxy;
        if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {
            proxy = Hudson.getInstance().proxy;
        } else {
            proxy = new ProxyConfiguration("", 0, "", "");
        }
        return proxy;
    }

    // Append the changelog if we should and can
    private String createBuildNotes(String buildNotes, final ChangeLogSet<?> changeSet) {
        if (appendChangelog) {
            StringBuilder stringBuilder = new StringBuilder();

            // Show the build notes first
            stringBuilder.append(buildNotes);

            // Then append the changelog
            stringBuilder.append("\n\n")
                    .append(changeSet.isEmptySet() ? Messages.HockeyAppRecorder_EmptyChangeSet() : Messages.HockeyAppRecorder_Changelog())
                    .append("\n");

            int entryNumber = 1;

            for (Entry entry : changeSet) {
                stringBuilder.append("\n").append(entryNumber).append(". ");
                stringBuilder.append(entry.getMsg()).append(" \u2014 ").append(entry.getAuthor());

                entryNumber++;
            }
            buildNotes = stringBuilder.toString();
        }
        return buildNotes;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        ArrayList<HockeyAppBuildAction> actions = new ArrayList<HockeyAppBuildAction>();
        RunList<? extends AbstractBuild<?, ?>> builds = project.getBuilds();

        Collection predicated = CollectionUtils.select(builds, new Predicate() {
            public boolean evaluate(Object o) {
                Result result = ((AbstractBuild<?, ?>) o).getResult();
                if (result == null) return false; // currently running builds can have a null result
                return result.isBetterOrEqualTo(Result.SUCCESS);
            }
        });

        ArrayList<AbstractBuild<?, ?>> filteredList = new ArrayList<AbstractBuild<?, ?>>(predicated);


        Collections.reverse(filteredList);
        for (AbstractBuild<?, ?> build : filteredList) {
            List<HockeyAppBuildAction> testflightActions = build.getActions(HockeyAppBuildAction.class);
            if (testflightActions != null && testflightActions.size() > 0) {
                for (HockeyAppBuildAction action : testflightActions) {
                    actions.add(new HockeyAppBuildAction(action));
                }
                break;
            }
        }

        return actions;
    }

    private HockeyAppToken getTokenPair(String tokenPairName) {
        for (HockeyAppToken tokenPair : getDescriptor().getTokenPairs()) {
            if (tokenPair.getTokenPairName().equals(tokenPairName))
                return tokenPair;
        }


        String tokenPairNameForMessage = tokenPairName != null ? tokenPairName : "(null)";
        throw new MisconfiguredJobException(Messages._HockeyAppRecorder_TokenPairNotFound(tokenPairNameForMessage));
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private final CopyOnWriteList<HockeyAppToken> tokenPairs = new CopyOnWriteList<HockeyAppToken>();

        public DescriptorImpl() {
            super(HockeyAppRecorder.class);
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            tokenPairs.replaceBy(req.bindParametersToList(HockeyAppToken.class, "hockeyAppToken."));
            save();
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.HockeyAppRecorder_UploadLinkText();
        }

        public Iterable<HockeyAppToken> getTokenPairs() {
            return tokenPairs;
        }
    }

    private static class EnvAction implements EnvironmentContributingAction {
        private transient Map<String, String> data = new HashMap<String, String>();

        private void add(String key, String value) {
            if (data == null) return;
            data.put(key, value);
        }

        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            if (data != null) env.putAll(data);
        }

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return null;
        }
    }
}
