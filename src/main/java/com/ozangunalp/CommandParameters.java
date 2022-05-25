package com.ozangunalp;

import java.nio.file.Path;

import picocli.CommandLine.Option;

public class CommandParameters {
    
    @Option(names="--tokens-file", required = true, paramLabel = "tokens-file", description = "File for storing obtained tokens.")
    Path tokensPath;

    @Option(names="--tokens-instance-file", required = true, paramLabel = "tokens-instance-file", description = "File for storing obtained tokens 2.")
    Path tokensPath2;

    @Option(names="--create-instance", paramLabel = "Create kafka instance", description = "Create kafka instance")
    String instanceName;

    @Option(names="--create-topic", paramLabel = "Create instance topic", description = "Create instance topic")
    String instanceTopic;

    @Option(names="--create-service-account", paramLabel = "Create service account", description = "Create service account")
    String serviceAccount;
}
