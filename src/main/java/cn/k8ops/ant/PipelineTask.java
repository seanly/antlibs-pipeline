package cn.k8ops.ant;

import cn.k8ops.ant.asl.pipeline.Config;
import cn.k8ops.ant.asl.pipeline.Step;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PipelineTask extends Task {

    @Setter
    @Getter
    private String file;

    private Config config;

    @SneakyThrows
    @Override
    public void execute() {
        if (null == StringUtils.trimToNull(file)) {
            throw new BuildException("config not set.");
        }

        File configFile = new File(file);
        if (!configFile.exists()) {
            throw new BuildException("config file is not exist.");
        }

        this.config = Config.parse(file);
        initDirs();
        runPipeline();
    }

    public boolean runPipeline() {

        boolean result = false;

        for (Step step : config.getSteps()) {
            result = runStep(step);

            if (!result) {
                break;
            }
        }

        return result;
    }

    private boolean runStep(Step step) {
        boolean result = false;

        log(String.format("--//STEP: %s", step.getName()));

        Map<String, String> localEnviron = config.getEnvironment();
        localEnviron.putAll(step.getEnvironment());
        if (!step.shouldRun(localEnviron)) {
            return true;
        }

        for (cn.k8ops.ant.asl.pipeline.Task task : step.getTasks()) {
            result = runTask(task);
            if (!result) {
                break;
            }
        }

        if (step.getAfterTasks().size() != 0) {
            boolean result2 = runTasks(step.getAfterTasks());
            if (result) {
                result = result2;
            }
        }

        return result;
    }

    private boolean runTasks(List<cn.k8ops.ant.asl.pipeline.Task> tasks) {
        for(cn.k8ops.ant.asl.pipeline.Task task : tasks) {
            if (!runTask(task)) {
                return false;
            }
        }

        return true;
    }

    private boolean runTask(cn.k8ops.ant.asl.pipeline.Task task) {
        return ant(task);
    }

    public final static String DIR_DOT_CI = ".ci";

    @SneakyThrows
    private boolean ant(cn.k8ops.ant.asl.pipeline.Task task) {
        log(String.format("--//task: %s", task.getId()));

        String runId = String.format("%s-%s", task.getId(), getCurrentTime());

        Properties properties = new Properties();
        properties.putAll(task.getProperties());

        File propsFile = new File(getWs(), DIR_DOT_CI + File.separator + runId + ".properties");
        properties.store(new FileOutputStream(propsFile), "task properties");

        return true;
    }

    private File getWs() {
        File configFile = new File(file);
        return configFile.getParentFile();
    }

    private void initDirs() {
        File dotCIDir = new File(getWs(), DIR_DOT_CI);
        if (!dotCIDir.exists()) {
            dotCIDir.mkdirs();
        }
    }

    public static String getCurrentTime() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
    }
}
