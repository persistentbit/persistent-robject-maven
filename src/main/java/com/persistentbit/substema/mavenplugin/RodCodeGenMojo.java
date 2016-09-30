package com.persistentbit.substema.mavenplugin;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.collections.PStream;
import com.persistentbit.substema.dependencies.DependencySupplier;
import com.persistentbit.substema.dependencies.SupplierDef;
import com.persistentbit.substema.dependencies.SupplierType;
import com.persistentbit.substema.javagen.GeneratedJava;
import com.persistentbit.substema.javagen.JavaGenOptions;
import com.persistentbit.substema.javagen.ServiceJavaGen;
import com.persistentbit.substema.compiler.SubstemaCompiler;
import com.persistentbit.substema.compiler.values.RSubstema;
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
import java.util.List;

/*
 * Generate packages from a ROD file
 *
 * @goal generate-packages
 * @phase generate-packages
 *
 * @description Generate packages from a ROD file
 */
@Mojo(
        name="generate-packages",
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
     * @parameter default-value="target/generated-packages/rod"
     * @required
     */
    @Parameter(defaultValue = "target/generated-sources/substema",required = true)
    File outputDirectory;

    @Parameter(defaultValue = "src/main/resources",required = true)
    File resourcesDirectory;

    /*
     * Sources
     *
     * @parameter
     * @required
     */
    @Parameter(name="packages",required = true)
    List<String> packages;


    public void execute()  throws MojoExecutionException, MojoFailureException {
        try{
            getLog().info("Compiling Substemas...");
            PList<SupplierDef> supplierDefs = PList.empty();
            try{
                if(resourcesDirectory.exists()){
                    getLog().info("Adding Dependency Supplier " + SupplierType.folder + " , " + resourcesDirectory.getAbsolutePath());
                    supplierDefs = supplierDefs.plus(new SupplierDef(SupplierType.folder,resourcesDirectory.getAbsolutePath()));

                }
                List<String> classPathElements = project.getCompileClasspathElements();
                if(classPathElements != null){
                    supplierDefs = supplierDefs.plusAll(PStream.from(classPathElements).map(s -> {
                        File f = new File(s);
                        if(f.exists()){
                            SupplierType type = f.isDirectory() ? SupplierType.folder : SupplierType.archive;
                            getLog().info("Adding Dependency Supplier " + type + " , " + f.getAbsolutePath());
                            return new SupplierDef(type,f.getAbsolutePath());
                        } else {
                            return null;
                        }
                    }).filterNulls());
                }

            }catch(Exception e){
                throw new MojoExecutionException("Error building dependencyList",e);
            }
            DependencySupplier dependencySupplier = new DependencySupplier(supplierDefs);
            PList<RSubstema> substemas = SubstemaCompiler.compile(dependencySupplier,PList.from(packages));

            substemas.forEach(ss -> getLog().info(ss.toString()));

            /*ClassLoader classLoader = this.getClass().getClassLoader();
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
                        //getLog().info(cpe);
                        if(new File(cpe).isDirectory()){
                            cpeUri = new URI(cpeUri.toString() + "/");
                        }
                        //getLog().info(cpeUri.toString());
                        urls[i] = cpeUri.toURL();
                        getLog().info("classpath += '" + urls[i] + "'");
                    }
                    classLoader = new URLClassLoader(urls, classLoader);

                    Thread.currentThread().setContextClassLoader(classLoader);
                }
            }
            catch (Exception e)
            {
                throw new MojoExecutionException("Error extending the classpath for the dbjup-mojo.", e);
            }*/




            if ( !outputDirectory.exists() ){
                if(outputDirectory.mkdirs() == false){
                    throw new MojoExecutionException("Can't create output folder " + outputDirectory.getAbsolutePath());
                }
            }
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            JavaGenOptions genOptions  =   new JavaGenOptions(true,true);
            //substemas.forEach(ss -> {
            //    getLog().info("-------- " + ss.getPackageName() + " --------");
            //    getLog().info(JJPrinter.print(true,new JJMapper().write(ss)));
            //});
            substemas.forEach( ss -> {
                PList<GeneratedJava> genCodeList = ServiceJavaGen.generate(genOptions,ss);

                genCodeList.forEach(g -> {
                    String packagePath = g.name.getPackageName().replace('.',File.separatorChar);
                    File dest = new File(outputDirectory,packagePath);
                    if(dest.exists() == false){ dest.mkdirs(); }
                    dest = new File(dest,g.name.getClassName() + ".java");
                    getLog().info("Generating " + dest.getAbsolutePath());
                    try(FileWriter fw = new FileWriter(dest)){
                        fw.write(g.code);
                    }catch (IOException io){
                        getLog().error(io);
                        throw new RuntimeException("Can't write to " + dest.getAbsolutePath());
                    }
                });
            });
/*
            PStream<File> rodFiles = PStream.from(packages).map(n -> new File(n));
            rodFiles.forEach(rf -> {
                if(rf.exists() == false){
                    getLog().error("Can't find substema file: " + rf.getAbsolutePath());
                }
                if(rf.getName().toLowerCase().endsWith(".substema") == false){
                    throw new RuntimeException("Expected *.substema filename");
                }
            });
            SubstemaTokenizer tokenizer = new SubstemaTokenizer();
            JavaGenOptions genOptions  =   new JavaGenOptions(true,true);
            rodFiles.forEach(rf -> {
                getLog().info("Generating java for " + rf);
                String packageName = rf.getName().substring(0,rf.getName().length()-".rod".length());
                try {
                    String code = new String(Files.readAllBytes(Paths.get(rf.toURI())));
                    PList<Token<SubstemaTokenType>> tokens = tokenizer.tokenize(rf.getName(),code);
                    SubstemaParser rodParser = new SubstemaParser(packageName,tokens);
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
*/

        }catch(MojoExecutionException e){
              throw e;
        }catch (Exception e){
            getLog().error("General error",e);

        }


    }
}
