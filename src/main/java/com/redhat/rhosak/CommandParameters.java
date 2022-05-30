package com.redhat.rhosak;

import java.nio.file.Path;

import picocli.CommandLine.Option;

public class CommandParameters {
    
    @Option(names="--tokens-file", paramLabel = "tokens-file", description = "File for storing obtained tokens.", defaultValue = "credentials.json")
    Path tokensPath;

    @Option(names="--create-instance", paramLabel = "Create kafka instance", description = "Create kafka instance")
    String instanceName;

    @Option(names="--create-topic", paramLabel = "Create instance topic", description = "Create instance topic")
    String instanceTopic;
}
