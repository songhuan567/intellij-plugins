package com.google.jstestdriver.idea.execution;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.internal.ImmutableSet;
import com.google.jstestdriver.JsTestDriverServer;
import com.google.jstestdriver.idea.TestRunner;
import com.google.jstestdriver.idea.execution.settings.JstdRunSettings;
import com.google.jstestdriver.idea.execution.settings.ServerType;
import com.google.jstestdriver.idea.execution.settings.TestType;
import com.google.jstestdriver.idea.server.ui.ToolPanel;
import com.google.jstestdriver.idea.util.EscapeUtils;
import com.intellij.execution.*;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static java.io.File.pathSeparator;

/**
 * @author Sergey Simonchik
 */
public class JstdTestRunnerCommandLineState extends CommandLineState {

  private static final String JSTD_FRAMEWORK_NAME = "JsTestDriver";
  private static final Function<File, String> GET_ABSOLUTE_PATH = new Function<File, String>() {
    @Override
    public String apply(File file) {
      return file.getAbsolutePath();
    }
  };

  private final Project myProject;
  private final ExecutionEnvironment myExecutionEnvironment;
  private final JstdRunSettings myRunSettings;
  private final ImmutableList<VirtualFile> myConfigVirtualFiles;
  private final String myCoverageFilePath;

  public JstdTestRunnerCommandLineState(
    @NotNull Project project,
    @NotNull ExecutionEnvironment executionEnvironment,
    @NotNull JstdRunSettings runSettings,
    @NotNull List<VirtualFile> configVirtualFiles,
    @Nullable String coverageFilePath)
  {
    super(executionEnvironment);
    myProject = project;
    myExecutionEnvironment = executionEnvironment;
    myRunSettings = runSettings;
    myConfigVirtualFiles = ImmutableList.copyOf(configVirtualFiles);
    myCoverageFilePath = coverageFilePath;
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ProcessHandler processHandler = startProcess();
    TestLocationProvider locationProvider = new TestLocationProvider() {
      @NotNull
      @Override
      public List<Location> getLocation(@NotNull String protocolId, @NotNull String locationData, Project project) {
        return Collections.emptyList();
      }
    };
    ConsoleView consoleView = createConsole(myProject, processHandler, myExecutionEnvironment,
                                            executor, locationProvider);
    //consoleView.addMessageFilter(new NodeJSStacktraceFilter(myProject));

    return new DefaultExecutionResult(consoleView, processHandler);
  }

  public static ConsoleView createConsole(@NotNull Project project,
                                          @NotNull ProcessHandler processHandler,
                                          @NotNull ExecutionEnvironment env,
                                          Executor executor,
                                          @NotNull TestLocationProvider locationProvider)
    throws ExecutionException {
    JstdRunConfiguration runConfiguration = (JstdRunConfiguration) env.getRunProfile();
    TestConsoleProperties testConsoleProperties = new SMTRunnerConsoleProperties(
      new RuntimeConfigurationProducer.DelegatingRuntimeConfiguration<JstdRunConfiguration>(runConfiguration),
      JSTD_FRAMEWORK_NAME,
      executor
    );

    ConsoleView testsOutputConsoleView = SMTestRunnerConnectionUtil.createConsoleWithCustomLocator(
      JSTD_FRAMEWORK_NAME,
      testConsoleProperties,
      env.getRunnerSettings(),
      env.getConfigurationSettings(), locationProvider
    );

    testsOutputConsoleView.attachToProcess(processHandler);

    Disposer.register(project, testsOutputConsoleView);
    return testsOutputConsoleView;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    Map<TestRunner.ParameterKey, String> params = createParameterMap();
    GeneralCommandLine commandLine = createCommandLine(params);

    OSProcessHandler osProcessHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
    ProcessTerminatedListener.attach(osProcessHandler);
    return osProcessHandler;
  }

  @NotNull
  private static GeneralCommandLine createCommandLine(@NotNull Map<TestRunner.ParameterKey, String> parameters) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    // uncomment this if you want to debug jsTestDriver code in the test-runner process
    //addParameter("-Xdebug");
    //addParameter("-Xrunjdwp:transport=dt_socket,address=5000,server=y,suspend=y");

    File file = new File(PathUtil.getJarPathForClass(JsTestDriverServer.class));
    commandLine.setWorkDirectory(file.getParentFile());

    commandLine.addParameter("-cp");
    commandLine.addParameter(buildClasspath());

    commandLine.addParameter(TestRunner.class.getName());
    for (Map.Entry<TestRunner.ParameterKey, String> param : parameters.entrySet()) {
      String keyValue = EscapeUtils.join(Arrays.asList(param.getKey().name().toLowerCase(), param.getValue()), '=');
      commandLine.addParameter("--" + keyValue);
    }

    return commandLine;
  }

  private static String buildClasspath() {
    List<File> classpathFiles = getClasspathRootFiles(TestRunner.class, JsTestDriverServer.class);
    Set<String> classpathPaths = ImmutableSet.copyOf(Lists.transform(classpathFiles, GET_ABSOLUTE_PATH));
    return Joiner.on(pathSeparator).join(classpathPaths);
  }

  private static List<File> getClasspathRootFiles(Class<?>... classList) {
    List<File> classpath = Lists.newArrayList();
    for (Class<?> clazz : classList) {
      String path = PathUtil.getJarPathForClass(clazz);
      File file = new File(path);
      classpath.add(file.getAbsoluteFile());
    }
    return classpath;
  }

  public Map<TestRunner.ParameterKey, String> createParameterMap() {
    Map<TestRunner.ParameterKey, String> parameters = Maps.newLinkedHashMap();
    String serverUrl = myRunSettings.getServerType() == ServerType.INTERNAL ?
                       "http://localhost:" + ToolPanel.serverPort :
                       myRunSettings.getServerAddress();
    parameters.put(TestRunner.ParameterKey.SERVER_URL, serverUrl);
    parameters.put(TestRunner.ParameterKey.CONFIG_FILE, joinJstdConfigs(myConfigVirtualFiles));
    if (myRunSettings.getTestType() == TestType.TEST_CASE) {
      parameters.put(TestRunner.ParameterKey.TEST_CASE, myRunSettings.getTestCaseName());
    }
    if (myRunSettings.getTestType() == TestType.TEST_METHOD) {
      parameters.put(TestRunner.ParameterKey.TEST_CASE, myRunSettings.getTestCaseName());
      parameters.put(TestRunner.ParameterKey.TEST_METHOD, myRunSettings.getTestMethodName());
    }
    if (myCoverageFilePath != null) {
      parameters.put(TestRunner.ParameterKey.COVERAGE_OUTPUT_FILE, myCoverageFilePath);
      if (!myRunSettings.getFilesExcludedFromCoverage().isEmpty()) {
        String excludedPaths = EscapeUtils.join(myRunSettings.getFilesExcludedFromCoverage(), ',');
        parameters.put(TestRunner.ParameterKey.COVERAGE_EXCLUDED_PATHS, excludedPaths);
      }
    }
    return parameters;
  }

  @NotNull
  private static String joinJstdConfigs(@NotNull List<VirtualFile> configs) {
    List<String> paths = Lists.newArrayListWithCapacity(configs.size());
    for (VirtualFile config : configs) {
      paths.add(config.getPath());
    }
    return EscapeUtils.join(paths, ',');
  }

}
