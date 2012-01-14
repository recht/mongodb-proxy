package com.tradeshift.mongoproxy;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Option;

public class Options {

	@Option(name="-l", usage="Local port to bind to")
	private int localPort = 29017;
	
	@Option(name="-h", usage="MongoDB remote host")
	private String mongoHost = "localhost";
	
	@Option(name="-p", usage="MongoDB remote port")
	private int mongoPort = 27017;

	@Option(name="-o", usage="Opcode to reject", multiValued=true)
	private List<Integer> opcodes = new ArrayList<Integer>();
	
	public int getLocalPort() {
		return localPort;
	}

	public void setLocalPort(int localPort) {
		this.localPort = localPort;
	}

	public String getMongoHost() {
		return mongoHost;
	}

	public void setMongoHost(String mongoHost) {
		this.mongoHost = mongoHost;
	}

	public int getMongoPort() {
		return mongoPort;
	}

	public void setMongoPort(int mongoPort) {
		this.mongoPort = mongoPort;
	}

	public List<Integer> getOpcodes() {
		return opcodes;
	}

	public void setOpcodes(List<Integer> opcodes) {
		this.opcodes = opcodes;
	}
	
	
}
