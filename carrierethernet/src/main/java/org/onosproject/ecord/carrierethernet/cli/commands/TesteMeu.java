package org.onosproject.ecord.carrierethernet.cli.commands;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

@Command(scope = "onos", name = "um-teste",
description = "um pequeno teste onos")
public class TesteMeu extends AbstractShellCommand {

	@Override
	protected void execute() {
		print("uma pequena aplicacao");
	}

}
