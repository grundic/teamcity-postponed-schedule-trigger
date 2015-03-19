package ru.mail.teamcity.postponed.schedule.trigger;

import jetbrains.buildServer.BuildAgent;
import jetbrains.buildServer.buildTriggers.*;
import jetbrains.buildServer.buildTriggers.scheduler.SchedulerBuildTriggerService;
import jetbrains.buildServer.buildTriggers.scheduler.SchedulingPolicy;
import jetbrains.buildServer.buildTriggers.scheduler.SchedulingPolicyFactory;
import jetbrains.buildServer.buildTriggers.triggerRules.TriggerRules;
import jetbrains.buildServer.buildTriggers.triggerRules.TriggerRulesFilter;
import jetbrains.buildServer.buildTriggers.vcs.BranchFilterTriggerHelper;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.versionedSettings.VersionedSettingsManager;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.SystemTimeService;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;
import jetbrains.buildServer.vcs.spec.BranchSpecs;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Author: g.chernyshev
 * Date: 13.03.15
 */
public class PostponedSchedulerBuildTriggerService extends BuildTriggerService {

    static final String PROP_TRIGGER_BUILD_WITH_PENDING_CHANGES_ONLY_PARAM = "triggerBuildWithPendingChangesOnly";
    static final String PROP_ENFORCE_CLEAN_CHECKOUT_PARAM = "enforceCleanCheckout";
    static final String PROP_TRIGGER_BUILD_ON_ALL_COMPATIBLE_AGENTS = "triggerBuildOnAllCompatibleAgents";
    static final String PROP_BUILD_TYPE_ID_LIST = "buildTypeIdList";
    static final String PROP_WAIT_TIMEOUT = "waitTimeout";

    @NotNull
    private static final Logger LOG = Logger.getLogger(PostponedSchedulerBuildTriggerService.class);


    @NotNull private final PluginDescriptor pluginDescriptor;
    @NotNull private final BatchTrigger batchTrigger;
    @NotNull private final BuildCustomizerFactory buildCustomizerFactory;
    @NotNull private final TimeService myTimeService;
    @NotNull private final BranchSpecs branchSpecs;
    @NotNull private final VersionedSettingsManager versionedSettingsManager;
    @NotNull private final ProjectManager projectManager;
    private long myServerStartupTime = 0;


    private SchedulerBuildTriggerService delegate;

    public PostponedSchedulerBuildTriggerService(
            @NotNull PluginDescriptor pluginDescriptor,
            @NotNull BatchTrigger batchTrigger,
            @NotNull EventDispatcher<BuildServerListener> eventDispatcher,
            @NotNull BranchSpecs branchSpecs,
            @NotNull BuildCustomizerFactory buildCustomizerFactory,
            @NotNull VersionedSettingsManager versionedSettingsManager,
            @NotNull ProjectManager projectManager) {
        this.pluginDescriptor = pluginDescriptor;
        this.batchTrigger = batchTrigger;
        this.buildCustomizerFactory = buildCustomizerFactory;
        this.branchSpecs = branchSpecs;
        this.versionedSettingsManager = versionedSettingsManager;
        this.projectManager = projectManager;


        myTimeService = SystemTimeService.getInstance();
        eventDispatcher.addListener(new BuildServerAdapter() {
            @Override
            public void serverStartup() {
                myServerStartupTime = myTimeService.now();
            }
        });

        delegate = new SchedulerBuildTriggerService(eventDispatcher, branchSpecs, batchTrigger, buildCustomizerFactory, versionedSettingsManager);
    }

    @NotNull
    @Override
    public String getName() {
        return "postponedSchedulingTrigger";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Postponed Schedule Trigger";
    }

    @NotNull
    @Override
    public String describeTrigger(@NotNull BuildTriggerDescriptor buildTriggerDescriptor) {
        return String.format(
                "%s%n" +
                "Build ids to wait for: %s%n" +
                "Timeout: %s",
                delegate.describeTrigger(buildTriggerDescriptor),
                buildTriggerDescriptor.getProperties().get(PROP_BUILD_TYPE_ID_LIST),
                buildTriggerDescriptor.getProperties().get(PROP_WAIT_TIMEOUT)
        );
    }

    private boolean isTriggerIfPendingChanges(Map<String, String> props) {
        String property = props.get(PROP_TRIGGER_BUILD_WITH_PENDING_CHANGES_ONLY_PARAM);
        return StringUtil.isTrue(property);
    }

    private boolean isEnforceCleanCheckout(Map<String, String> props) {
        String property = props.get(PROP_ENFORCE_CLEAN_CHECKOUT_PARAM);
        return StringUtil.isTrue(property);
    }

    private boolean isTriggerOnAllCompatibleAgents(Map<String, String> props) {
        String property = props.get(PROP_TRIGGER_BUILD_ON_ALL_COMPATIBLE_AGENTS);
        return StringUtil.isTrue(property);
    }

    @Nullable
    @Override
    public String getEditParametersUrl() {
        return pluginDescriptor.getPluginResourcesPath("editPostponedSchedulerBuildTrigger.jsp");
    }

    @Nullable
    @Override
    public Map<String, String> getDefaultTriggerProperties() {
        Map<String, String> defaultProps = delegate.getDefaultTriggerProperties();
        return defaultProps;
    }

    @Nullable
    @Override
    public PropertiesProcessor getTriggerPropertiesProcessor() {
        return new PropertiesProcessor() {
            public Collection<InvalidProperty> process(Map<String, String> properties) {
                Collection<InvalidProperty> invalid = delegate.getTriggerPropertiesProcessor().process(properties);

                String buildIdProp = properties.get(PROP_BUILD_TYPE_ID_LIST);
                if (null != buildIdProp) {
                    String[] buildIdList = splitBuildIds(buildIdProp);
                    for (String buildId: buildIdList){
                        if (!buildId.isEmpty()) {
                            SBuildType buildType = projectManager.findBuildTypeByExternalId(buildId);
                            if (null == buildType) {
                                invalid.add(new InvalidProperty(PROP_BUILD_TYPE_ID_LIST, String.format("Can't find build with <%s> id!", buildId)));
                            }
                        }
                    }
                }

                String waitTimeoutProp = properties.get(PROP_WAIT_TIMEOUT);
                if (null != waitTimeoutProp) {
                    try {
                        Long.parseLong(waitTimeoutProp);
                    } catch (NumberFormatException exception) {
                        invalid.add(new InvalidProperty(PROP_WAIT_TIMEOUT, String.format("Can't parse timeout value <%s>!", waitTimeoutProp)));
                    }
                }

                return invalid;
            }
        };
    }

    private String[] splitBuildIds(String value){
        return value.split(";");
    }

    @NotNull
    @Override
    public BuildTriggeringPolicy getBuildTriggeringPolicy() {
        final BranchFilterTriggerHelper myBranchFilterHelper = new BranchFilterTriggerHelper(this.branchSpecs, "+:<default>");

        return new PolledBuildTrigger() {
            @Override
            public void triggerBuild(@NotNull PolledTriggerContext polledTriggerContext) throws BuildTriggerException {
                BuildTriggerDescriptor triggerDescriptor = polledTriggerContext.getTriggerDescriptor();
                SchedulingPolicy schedulingPolicy = SchedulingPolicyFactory.createSchedulingPolicyOrThrowError(triggerDescriptor);

                for (BranchEx branchEx : myBranchFilterHelper.getBranches(polledTriggerContext, null)) {
                    final String lastTimeKey = "lastTriggeredTime_" + branchEx.getName();
                    final String lastTimeValue = polledTriggerContext.getCustomDataStorage().getValue(lastTimeKey);

                    long now = myTimeService.now();
                    final long lastTime;
                    if (lastTimeValue != null) {
                        try {
                            lastTime = Long.parseLong(lastTimeValue);
                        } catch (NumberFormatException e) {
                            // fix value to prevent exception on next try
                            polledTriggerContext.getCustomDataStorage().putValue(lastTimeKey, Long.toString(now));
                            LOG.error(String.format("Invalid lastTimeValue <%s>", lastTimeValue));
                            return;
                        }
                    } else {
                        lastTime = 0;
                    }
                    if (lastTime < myServerStartupTime) {
                        // start, unpause
                        polledTriggerContext.getCustomDataStorage().putValue(lastTimeKey, Long.toString(now));
                        return;
                    }


                    long schedulingTime = schedulingPolicy.getScheduledTime(lastTime);
                    if (schedulingTime > 0L && now >= schedulingTime) {
                        String waitTimeoutProp = polledTriggerContext.getTriggerDescriptor().getProperties().get(PROP_WAIT_TIMEOUT);
                        long delta = 0; // milliseconds
                        if (null != waitTimeoutProp) {
                            long waitTimeoutValue = 0;
                            try {
                                waitTimeoutValue = Long.parseLong(waitTimeoutProp);
                            } catch (NumberFormatException ignored){
                            }
                            delta = TimeUnit.MINUTES.toMillis(waitTimeoutValue);
                        }

                        if (!isBuildInQueueOrRunning(polledTriggerContext) || now > schedulingTime + delta) {
                            polledTriggerContext.getCustomDataStorage().putValue(lastTimeKey, Long.toString(now));
                            createTrigger(branchEx).triggerBuild(polledTriggerContext);
                        }
                    }
                }
            }

            private boolean isBuildInQueueOrRunning(@NotNull PolledTriggerContext polledTriggerContext) {
                Map<String, String> properties = polledTriggerContext.getTriggerDescriptor().getProperties();

                String buildIdProp = properties.get(PROP_BUILD_TYPE_ID_LIST);
                String[] buildIdList = splitBuildIds(buildIdProp);
                for (String buildId: buildIdList){
                    if (buildId.isEmpty()) {
                        continue;
                    }
                    SBuildType buildType = projectManager.findBuildTypeByExternalId(buildId);
                    if (null == buildType) {
                        continue;
                    }
                    if (!buildType.getQueuedBuilds(null).isEmpty() || !buildType.getRunningBuilds().isEmpty()) {
                        return true;
                    }
                }
                return false;
            }

            @NotNull
            private PolledBuildTrigger createTrigger(@NotNull final BranchEx branchEx) {
                return new PolledBuildTrigger() {
                    @Override
                    public void triggerBuild(@NotNull PolledTriggerContext polledTriggerContext) throws BuildTriggerException {
                        BuildTypeEx buildType = (BuildTypeEx) polledTriggerContext.getBuildType();

                        BuildTriggerDescriptor triggerDescriptor = polledTriggerContext.getTriggerDescriptor();
                        BuildCustomizer buildCustomizer = buildCustomizerFactory.createBuildCustomizer(buildType, null);
                        buildCustomizer.setDesiredBranchName(branchEx.getName());
                        Map<String, String> properties = triggerDescriptor.getProperties();

                        if (!isTriggerIfPendingChanges(properties) || !pendingChangesAccepted(buildType, branchEx, polledTriggerContext)) {
                            if (isEnforceCleanCheckout(properties)) {
                                buildCustomizer.setCleanSources(true);
                            }

                            addToQueue(polledTriggerContext, buildCustomizer);
                        }

                    }

                    private void addToQueue(@NotNull PolledTriggerContext polledTriggerContext, @NotNull BuildCustomizer buildCustomizer) {
                        SBuildType buildType = polledTriggerContext.getBuildType();
                        BuildTriggerDescriptor triggerDescriptor = polledTriggerContext.getTriggerDescriptor();
                        List<TriggerTask> tasks = new LinkedList<TriggerTask>();

                        if (isTriggerOnAllCompatibleAgents(triggerDescriptor.getProperties())) {
                            for (BuildAgent buildAgent : buildType.getCanRunAndCompatibleAgents(false)) {
                                TriggerTask task = batchTrigger.newTriggerTask(buildCustomizer.createPromotion());
                                task.setRunOnAgent((SBuildAgent) buildAgent);
                                tasks.add(task);
                            }
                        } else {
                            tasks.add(batchTrigger.newTriggerTask(buildCustomizer.createPromotion()));
                        }

                        batchTrigger.processTasks(tasks, getDisplayName());
                    }


                    private boolean pendingChangesAccepted(@NotNull BuildTypeEx buildType, @NotNull BranchEx branchEx, @NotNull PolledTriggerContext polledTriggerContext) {
                        long lastProcessed = -1L;
                        String lastProcessedKey = "lastProcessedModId_" + branchEx.getName();
                        String lastProcessedValue = polledTriggerContext.getCustomDataStorage().getValue(lastProcessedKey);
                        if (lastProcessedValue != null) {
                            try {
                                lastProcessed = Long.parseLong(lastProcessedValue);
                            } catch (NumberFormatException e) {
                                LOG.error(String.format("Failed to convert '%s' value!", lastProcessedValue));
                            }
                        }

                        ArrayList<SVcsModification> vcsModifications = new ArrayList<SVcsModification>();
                        List<ChangeDescriptor> detectedChanges = branchEx.getDetectedChanges(SelectPrevBuildPolicy.SINCE_LAST_BUILD, null);

                        for (ChangeDescriptor changeDescriptor : detectedChanges) {
                            SVcsModification relatedVcsChange = changeDescriptor.getRelatedVcsChange();
                            if ((relatedVcsChange != null) && (relatedVcsChange.getId() > lastProcessed)) {
                                vcsModifications.add(relatedVcsChange);
                            }
                        }

                        if (!vcsModifications.isEmpty()) {
                            lastProcessed = vcsModifications.get(0).getId();
                        }

                        try {
                            Collection<SBuildType> dependencies = buildType.getDependencyGraph().getNodes();
                            String configuredTriggerRules = polledTriggerContext.getTriggerDescriptor().getProperties().get(SchedulerBuildTriggerService.PROP_TRIGGER_RULES_PARAM);

                            TriggerRules triggerRules = versionedSettingsManager.excludeSettingRoots(configuredTriggerRules, dependencies);
                            TriggerRulesFilter triggerRulesFilter = new TriggerRulesFilter(triggerRules, dependencies);

                            triggerRulesFilter.setAnalyzeFullHistoryForMergeCommits(isAnalyzeFullHistoryForMergeCommits(buildType));
                            triggerRulesFilter.filterModifications(vcsModifications);
                            return !vcsModifications.isEmpty();
                        } finally {
                            polledTriggerContext.getCustomDataStorage().putValue(lastProcessedKey, String.valueOf(lastProcessed));
                        }
                    }

                    private boolean isAnalyzeFullHistoryForMergeCommits(@NotNull BuildTypeEx buildType) {
                        String value = buildType.getParameters().get("teamcity.scheduleTrigger.analyzeFullHistoryForMergeCommits");
                        return value != null ? Boolean.parseBoolean(value) : TeamCityProperties.getBooleanOrTrue("teamcity.scheduleTrigger.analyzeFullHistoryForMergeCommits");
                    }

                };
            }
        };
    }

    @Override
    public boolean isMultipleTriggersPerBuildTypeAllowed() {
        return true;
    }
}
