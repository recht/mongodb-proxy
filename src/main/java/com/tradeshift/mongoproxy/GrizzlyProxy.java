package com.tradeshift.mongoproxy;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrizzlyProxy {
	
	private static final Logger log = LoggerFactory.getLogger(GrizzlyProxy.class);

	public static void main(String[] args) {
		Options opts = new Options();
		CmdLineParser parser = new CmdLineParser(opts);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
			System.exit(-1);
		}
		
		TCPNIOTransportBuilder tb = TCPNIOTransportBuilder.newInstance();
		tb.setIOStrategy(WorkerThreadIOStrategy.getInstance());
		log.debug("IO Strategy: {}",  tb.getIOStrategy());
		
		TCPNIOTransport transport = tb.build();
		FilterChainBuilder fb = FilterChainBuilder.stateless();
		fb.add(new TransportFilter());
		
		String mongoHost = "localhost";
		int mongoPort = 27017;
		log.info("Connecting to mongo at {}:{}", mongoHost, mongoPort);
		fb.add(new OpcodeFilter(TCPNIOConnectorHandler.builder(transport).build(), mongoHost, mongoPort, opts.getOpcodes()));
		
		transport.setProcessor(fb.build());
		try {
			TCPNIOServerConnection connection = transport.bind(29017);
			transport.start();

			log.info("Proxy running on {}", connection.getLocalAddress());
			synchronized(GrizzlyProxy.class) {
				GrizzlyProxy.class.wait();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}
