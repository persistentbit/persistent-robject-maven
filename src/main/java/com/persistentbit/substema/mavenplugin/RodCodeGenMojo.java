package com.persistentbit.substema.mavenplugin;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.collections.PStream;
import com.persistentbit.core.tokenizer.Token;
import com.persistentbit.robjects.javagen.GeneratedJava;
import com.persistentbit.robjects.javagen.JavaGenOptions;
import com.persistentbit.robjects.javagen.ServiceJavaGen;
import com.persistentbit.robjects.rod.RodParser;
import com.persistentbit.robjects.rod.RodTokenType;
import com.persistentbit.robjects.rod.RodTokenizer;
import com.persistentbit.robjects.rod.values.RService;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generate sources from a ROD file
 *
 * @goal generate-sources
 * @phase generate-sources
 *
 * @description Generate sources from a ROD file
 */
public class RodCodeGenMojo extends AbstractMojo {
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @since 1.0
     */
    MavenProject project;



    /**
     * @parameter default-value="target/generated-sources/rod"
     * @required
     */
    File outputDirectory;

    /**
     * Sources
     *
     * @parameter
     * @required
     */
    List<String> sources;


    public void execute()  throws MojoExecutionException, MojoFailureException {
        try{
            getLog().info("--------------  GENERATING SOURCES --------");

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
            JavaGenOptions  genOptions  =   new JavaGenOptions(true,true);
            rodFiles.forEach(rf -> {
                getLog().info("Generating java for " + rf);
                String packageName = rf.getName().substring(0,rf.getName().length()-".rod".length());
                try {
                    String code = new String(Files.readAllBytes(Paths.get(rf.toURI())));
                    PList<Token<RodTokenType>> tokens = tokenizer.tokenize(rf.getName(),code);
                    RodParser rodParser = new RodParser(packageName,tokens);
                    RService service = rodParser.parseService();
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
