/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package org.ngereza.jpackage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author NGEREZA
 */
@Mojo(name = "package",
        defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true,
        threadSafe = true, requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM,
        requiresDependencyCollection = ResolutionScope.RUNTIME_PLUS_SYSTEM
)
public class JpackageMavenPlugin extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true)
    private MavenProject project;

    @Parameter
    private String jarDir;

    @Parameter(defaultValue = "")
    private String icon;

    @Parameter
    private String mainJar;

    @Parameter(required = true)
    private String mainClass;

    @Parameter(required = true)
    private String appName;

    @Parameter(required = true)
    private String appVersion;

    @Parameter(required = true)
    private String vendor;
    
    @Parameter(defaultValue = "base")
    private String multiRelease;

    @Parameter
    private List<String> mainArgs;

    @Parameter
    private List<String> jvmOption;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String targetDir = new File(project.getBuild().getOutputDirectory()).getParentFile().getAbsolutePath() + File.separator;
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator;

            String modules = "";
            String runtime = "";

            mainJar = project.getBuild().getFinalName() + ".jar";
            jarDir = targetDir + "app" + File.separator;
            String finalJar = targetDir + mainJar;
            //

            deleDirIfExists(Path.of(jarDir));
            Files.createDirectories(Path.of(jarDir));

            System.out.println("""
                               **********************************************************************
                               ******************* COPY JARs DEPENDENIES *****************************
                               **********************************************************************
                               
                               """);

            project.getArtifacts().forEach((Artifact t) -> {
                try {
                    File artifact = t.getFile();
                    String absolute = artifact.getAbsolutePath();
                    String dest = jarDir + artifact.getName();
                    getLog().debug("Copy dependency : " + absolute + " to " + dest);
                    Files.copy(Path.of(absolute), Path.of(dest), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    getLog().error(ex);
                }
            });

            System.out.println("""
                               **********************************************************************
                               ********************* COPY MAIN-JAR  *********************************
                               **********************************************************************
                               
                               """);
            Files.copy(Path.of(finalJar), Path.of(jarDir + mainJar), StandardCopyOption.REPLACE_EXISTING);

            System.out.println(" *************** JARs DEPENDENCIES COPIED ******************************\n\n\n");
            //
            System.out.println("""
                               **********************************************************************
                               *************** FIND APPLICATION RUNTIME DEPENDENCIES  ***************
                               **********************************************************************
                               
                               """);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String jdepsArgs = " --ignore-missing-deps "
                    + "--no-recursive  "
                    + "--multi-release "+multiRelease
                    + " --print-module-deps "
                    + " \"" + jarDir + "*.jar\" ";

            CommandLine cl = new CommandLine(javaBin + "jdeps")
                    .addArguments(jdepsArgs);

            DefaultExecuteResultHandler rh = new DefaultExecuteResultHandler();
            ExecuteWatchdog wd = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
            Executor exec = new DefaultExecutor();

            PumpStreamHandler psh = new PumpStreamHandler(out);

            exec.setStreamHandler(psh);
            exec.setWatchdog(wd);

            exec.execute(cl, rh);
            rh.waitFor();

            boolean stat = exec.isFailure(1);
            //
            modules = out.toString().lines().toList().get(0) + ",jdk.localedata";
            System.out.println(modules);
            if (!stat) {
                return;
            }
            System.out.println("JDEPS *************** ALL RUNTIME DEPENDENCIES FOUND ****************************\n\n\n");

            System.out.println("""
                               **********************************************************************
                               ********************* GENERATE APPLICATION RUNTIME  ******************
                               **********************************************************************
                               
                               """);

            runtime = targetDir + "runtime";
            deleDirIfExists(Path.of(runtime));
            String jlinkArgs = " --verbose --add-modules " + modules + " "
                    + "--include-locales=fr "
                    + "--no-header-files --no-man-pages --strip-debug "
                    + "--compress=2 --output \"" + runtime + "\"";
            //
            cl = new CommandLine(javaBin + "jlink")
                    .addArguments(jlinkArgs);

            rh = new DefaultExecuteResultHandler();
            wd = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
            exec = new DefaultExecutor();

            psh = new PumpStreamHandler(System.out);

            exec.setStreamHandler(psh);
            exec.setWatchdog(wd);

            exec.execute(cl, rh);
            rh.waitFor();

            stat = exec.isFailure(1);
            System.out.println("JLINK *************** RUNTIME GENERATED ******************************\n\n\n");
            //
            if (!stat) {
                return;
            }

            System.out.println("""
                               **********************************************************************
                               ********************* BUILD IMAGE APP  *******************************
                               **********************************************************************
                               
                               """);
            //
            String finalappDir = project.getBasedir() + File.separator + appName;
            deleDirIfExists(Path.of(finalappDir));
            String jpackageArgs = " --type app-image --runtime-image \"" + runtime + "\" --input \"" + jarDir + "\" "
                    + " --main-jar \"" + mainJar + "\" --name \"" + appName + "\" --icon \"" + icon + "\" "
                    + " --main-class \"" + mainClass + "\" "+getMainArg()+" "+getVMOptions()+" "
                    + " --verbose  --dest \"" + project.getBasedir() + "\" "
                    + " --copyright \"" + vendor + "\" --vendor \"" + vendor + "\" --app-version \"" + appVersion + "\" ";

            //
            cl = new CommandLine(javaBin + "jpackage")
                    .addArguments(jpackageArgs);

            rh = new DefaultExecuteResultHandler();
            wd = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
            exec = new DefaultExecutor();

            psh = new PumpStreamHandler(System.out);

            exec.setStreamHandler(psh);
            exec.setWatchdog(wd);

            exec.execute(cl, rh);
            rh.waitFor();

            stat = exec.isFailure(1);

            if (!stat) {
                throw new MojoFailureException("Error...");
            }

            System.out.println("*********************** SUCCES! ******************************\n\n\n");
            //
        } catch (Exception e) {
            getLog().error(e);
        }
    }

    private void deleDirIfExists(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile).forEach(File::delete);
        }
    }
    
    private String opt;
    private String getVMOptions(){
        opt = "";
        if(jvmOption != null){
            jvmOption.forEach((String t) -> {
                opt = " --java-options \""+t+"\" ";
            });
        }
        return opt;
    }
    
    private String args;
    private String getMainArg(){
        args = "";
        if(mainArgs != null){
            mainArgs.forEach((String t) -> {
                args += " "+t+" ";
            });
            if(!args.isBlank()){
                args = " --arguments \""+args+"\" "; 
            }
        }
        return args;
    }
}
