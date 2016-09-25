package com.persistentbit.substema.mavenplugin;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.collections.PStream;
import com.persistentbit.core.tokenizer.Token;

import com.persistentbit.substema.javagen.GeneratedJava;
import com.persistentbit.substema.javagen.JavaGenOptions;
import com.persistentbit.substema.javagen.ServiceJavaGen;
import com.persistentbit.substema.rod.RodParser;
import com.persistentbit.substema.rod.RodTokenType;
import com.persistentbit.substema.rod.RodTokenizer;
import com.persistentbit.substema.rod.values.RSubstema;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/*
 * Generate sources from a ROD file
 *
 * @goal generate-sources
 * @phase generate-sources
 *
 * @description Generate sources from a ROD file
 */
@Mojo(
        name="generate-sources",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class RodCodeGenMojo extends AbstractMojo {
    /*
     * @parameter property="project"
     * @required
     * @readonly
     * @since 1.0
     */
    @Parameter(property = "project",required = true, readonly = true)
    MavenProject project;



    /*
     * @parameter default-value="target/generated-sources/rod"
     * @required
     */
    @Parameter(defaultValue = "target/generated-sources/rod",required = true)
    File outputDirectory;

    /*
     * Sources
     *
     * @parameter
     * @required
     */
    @Parameter(required = true)
    List<String> sources;


    public void execute()  throws MojoExecutionException, MojoFailureException {
        try{
            getLog().info("--------------  GENERATING SOURCES --------");
            ClassLoader classLoader = this.getClass().getClassLoader();

            try
            {
                @SuppressWarnings("unchecked")
                final List<String> classPathElements = project.getTestClasspathElements();
                URL[] urls = null;
                if (classPathElements != null && classPathElements.size() > 0)
                {
                    urls = new URL[classPathElements.size()];
                    for (int i = 0; i < urls.length; i++)
                    {
                        String cpe = classPathElements.get(i);
                        URI cpeUri = new File(cpe).toURI();
                        getLog().info(cpe);
                        if(new File(cpe).isDirectory()){
                            cpeUri = new URI(cpeUri.toString() + "/");
                        }
                        getLog().info(cpeUri.toString());
                        urls[i] = cpeUri.toURL();
                        getLog().info("classpath += '" + urls[i] + "'");
                    }
                    classLoader = new URLClassLoader(urls, classLoader);

                    Thread.currentThread().setContextClassLoader(classLoader);

                    //                    //wil: testje mbt CL
                    //                    // "local" class
                    //                    Class finClazz = Class
                    //                            .forName("be.schaubroeck.boekhouding.upgrade.UpgradeActionV0203011Post");
                    //                    ClassLoader finCL = finClazz.getClassLoader();
                    //                    // "foreign" class
                    //                    Class palClazz = Class.forName("be.schaubroeck.pal.upgrade.UpgradeActionV1_3_4");
                    //                    //hier zal al een exceptie optreden.
                    //                    ClassLoader palCL = finClazz.getClassLoader();

                }
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error extending the classpath for the dbjup-mojo.", e);
            }

            File f = new File("/Users/petermuys/develop/persstentbit/substema-api/target/classes");

            getLog().info("Files:");
            for(File s : f.listFiles()){
                getLog().info("Found " + s);
            }

            //URL url = classLoader.getResource("com.persistentbit.substema.api.rod");
            URL url = classLoader.getResource("com.persistentbit.parser.substema");
            getLog().info("URL = " + url);
            String test = new String(Files.readAllBytes(Paths.get(url.toURI())));
            getLog().info(test);



            if ( !outputDirectory.exists() ){
                outputDirectory.mkdirs();
            }
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

            PStream<File> rodFiles = PStream.from(sources).map(n -> new File(n));
            rodFiles.forEach(rf -> {
                if(rf.exists() == false){
                    getLog().error("Can't find rod file: " + rf.getAbsolutePath());
                }
                if(rf.getName().toLowerCase().endsWith(".rod") == false){
                    throw new RuntimeException("Expected *.rod filename");
                }
            });
            RodTokenizer tokenizer = new RodTokenizer();
            JavaGenOptions genOptions  =   new JavaGenOptions(true,true);
            rodFiles.forEach(rf -> {
                getLog().info("Generating java for " + rf);
                String packageName = rf.getName().substring(0,rf.getName().length()-".rod".length());
                try {
                    String code = new String(Files.readAllBytes(Paths.get(rf.toURI())));
                    PList<Token<RodTokenType>> tokens = tokenizer.tokenize(rf.getName(),code);
                    RodParser rodParser = new RodParser(packageName,tokens);
                    RSubstema service = rodParser.parseSubstema();
                    PList<GeneratedJava> genCodeList = ServiceJavaGen.generate(genOptions,packageName,service);

                    genCodeList.forEach(g -> {
                        String packagePath = g.name.packageName.replace('.',File.separatorChar);
                        File dest = new File(outputDirectory,packagePath);
                        if(dest.exists() == false){ dest.mkdirs(); }
                        dest = new File(dest,g.name.className + ".java");
                        getLog().info("Generating " + dest.getAbsolutePath());
                        try(FileWriter fw = new FileWriter(dest)){
                            fw.write(g.code);
                        }catch (IOException io){
                            getLog().error(io);
                            throw new RuntimeException("Can't write to " + dest.getAbsolutePath());
                        }
                    });

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }


            });


        }catch(Exception e){
            getLog().error("General error",e);

        }


    }
}
