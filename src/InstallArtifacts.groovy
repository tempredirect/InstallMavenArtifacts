/**
 */

def download(URL url, File to){

    if (to.exists()) assert to.delete()
    def conn = url.openConnection()

    try {
        if (conn.responseCode == 200) {
            to.withOutputStream { fileOut ->
                fileOut << conn.inputStream
            }
            println "Downloaded ${url.toString()} -> ${to.absolutePath} [${to.size()}]"
            return to
        } else {
            println "Failed to download ${url} http response :${conn.responseCode}"
        }
    } finally {
        conn.disconnect()
    }
    return null
}

def installArtifacts(arguments) {
    def cli = new CliBuilder(usage: "InstallArtifacts.groovy [--repo] [artifacts*]")
    cli.with {
        h longOpt:  "help", "Display help message"
        r longOpt:  "repo", args: 1, argName:'repoUrl', "url of the maven repo to upload to"
        c longOpt:  "central", "Use repo1.maven.org"
        se longOpt: "spring-external", "Use http://repository.springsource.com/maven/bundles/external"
        sr longOpt: "spring-release", "Use http://repository.springsource.com/maven/bundles/release"
        nc longOpt: "nocache", "cache bust the remote url"
        ns longOpt: "nosource","don't attempt to download the source jar"
    }

    def options = cli.parse(arguments)

    if( !options ) return

    if( options.h){
        cli.usage()
        return
    }

    def repo = options.r ? options.r : "file:///M:/OAM/Fast-Track/Build/Maven_Repos"

    def remoteBase = "http://repo1.maven.org/maven2"
    if( options.se) remoteBase = "http://repository.springsource.com/maven/bundles/external"
    if( options.sr) remoteBase = "http://repository.springsource.com/maven/bundles/release"

    def artifacts = options.arguments().grep{it}.collect { arg ->
        def parts = arg.split(":")
        def labels = ["groupId", "artifactId", "version", "packaging"]
        def artifact = [name:arg, files:[], classifiers:[]]
        parts.eachWithIndex {part, index -> artifact[labels[index]] = part}
        
        if( artifact.version =~ /^\w+$/ ){
            def v = artifact.version
            artifact.version = artifact.packaging
            artifact.packaging = v
        }

        return artifact
    }

    File tmp = new File(System.getProperty("java.io.tmpdir"), "downloads")
    if (!tmp.exists()) assert tmp.mkdir()

    artifacts.each { artifact ->
        def filename = "${artifact.artifactId}-${artifact.version}.${artifact.packaging}"
        def url = new URL("${remoteBase}/${artifact.groupId.replaceAll('\\.', '/')}/${artifact.artifactId}/${artifact.version}/${filename}${options.nc ? "?" + System.currentTimeMillis() : ""}")
        artifact.file = download(url, new File(tmp, filename))

        if(artifact.packaging != "pom"){
            filename = "${artifact.artifactId}-${artifact.version}.pom"
            url = new URL("${remoteBase}/${artifact.groupId.replaceAll('\\.', '/')}/${artifact.artifactId}/${artifact.version}/${filename}${options.nc ? "?" + System.currentTimeMillis() : ""}")

            artifact.pom = download(url, new File(tmp, filename))
        }
        if(artifact.packaging == "jar" && !options.ns){
            filename = "${artifact.artifactId}-${artifact.version}-sources.jar"
            url = new URL("${remoteBase}/${artifact.groupId.replaceAll('\\.', '/')}/${artifact.artifactId}/${artifact.version}/${filename}${options.nc ? "?" + System.currentTimeMillis() : ""}")

            artifact.sources = download(url, new File(tmp, filename))
        }
    }

    artifacts.each { artifact ->
        if(artifact.file){
            def cmd = [
                    "${System.getenv("M2_HOME")}/bin/mvn.bat",
                    "-B",
                    "org.apache.maven.plugins:maven-deploy-plugin:2.7:deploy-file",
                    "-DgroupId=${artifact.groupId}",
                    "-DartifactId=${artifact.artifactId}",
                    "-Dpackaging=${artifact.packaging}",
                    "-Dversion=${artifact.version}",
                    "-Dfile=${artifact.file.absolutePath}",
                    "-Durl=${repo}"
            ]
            if( artifact.pom ) cmd << "-DpomFile=${artifact.pom.absolutePath}"

            if(artifact.classifiers) cmd << "-Dclassifiers=${artifact.classifiers.join(",")}"
            if(artifact.files) cmd << "-Dfiles=${artifact.files.collect {it.absolutePath}.join(",")}"
            if(artifact.sources) cmd << "-Dsources=${artifact.sources.absolutePath}"

            println cmd.join(" ")

            def process = cmd.execute()
            process.waitForProcessOutput(System.out, System.err)
            assert process.exitValue() == 0
        }else{
            println "Skipping : ${artifact.name}"
        }

    }
}

//installArtifacts("""org.apache.felix:org.apache.felix.shell.remote:1.1.2:jar
//                    org.apache.felix:org.apache.felix.webconsole:3.1.8:jar
//                    org.glassfish.osgi-platforms:felix-webconsole-extension:3.1.1:jar
//                    org.glassfish.admingui:glassfish-osgi-console-plugin:3.1.1:jar""".split("\\s"))
//
installArtifacts(args)
