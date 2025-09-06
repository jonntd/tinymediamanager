package org.tinymediamanager.cli;

import org.tinymediamanager.TinyMediaManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

@Command
public class CmdVersion implements IVersionProvider {

  @Override
  public String[] getVersion() throws Exception {
    return TinyMediaManager.generateLogHeader();
  }

}
