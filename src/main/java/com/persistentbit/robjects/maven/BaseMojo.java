package com.persistentbit.robjects.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;



public abstract class BaseMojo extends AbstractMojo {

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @since 1.0
     */
    MavenProject project;

    /**
     * Sources
     *
     * @parameter
     * @required
     */
    List<String> sources;

    /**
     * @parameter default-value="target/generated-sources/builderbuilder"
     * @required
     */
    File outputDirectory;




    @Override
    public void execute() {
        try {
            /*
            for (String r : sources) {
                docBuilder.addSourceTree(new File(r));
            }*/

            generate();

            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

        } catch (Exception e) {
            getLog().error("General error", e);
        }
    }

    protected abstract  void generate() throws Exception;

}
