package com.ozangunalp;

import java.nio.file.Path;

import picocli.CommandLine.Option;

public class CommandParameters {
    
    @Option(names="--tokens-file", required = true, paramLabel = "tokens-file", description = "File for storing obtained tokens.")
    Path tokensPath;

    @Option(names="--create-instance", paramLabel = "Create kafka instance", description = "Create kafka instance")
    String instanceName;

    @Option(names="--create-topic", paramLabel = "Create instance topic", description = "Create instance topic")
    String instanceTopic;
}
